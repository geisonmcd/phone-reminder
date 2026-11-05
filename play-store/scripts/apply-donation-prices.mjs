import { createRequire } from "node:module";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { google } from "googleapis";

const PACKAGE_NAME = "com.geison.smartrandomreminder";
const PRODUCT_ID = "donation_beer_small";
const PURCHASE_OPTION_ID = "buy";
const REGIONS_VERSION = "2025/03";
const TARGET_USD = 1.99;
const LATENCY_TOLERANCE = "PRODUCT_UPDATE_LATENCY_TOLERANCE_LATENCY_SENSITIVE";
const MAX_PATCH_RETRIES = 3;

const REPORT_REGIONS = [
  "US", "BR", "MX", "AR", "IN", "ID", "PH", "DE", "FR", "PT", "ES", "IT",
  "GB", "CA", "AU", "JP", "KR", "TR", "ZA", "NG", "EG", "PK", "CO", "CL",
  "PE", "RU",
];

// ISO 4217 / Play currencies that bill in whole units (Money.nanos must be 0).
const ZERO_DECIMAL_CURRENCIES = new Set([
  "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "MGA", "PYG",
  "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF",
]);

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const CSV_PATH = join(ROOT, "play-store", "donation-prices.csv");
const OUT_PATH = join(ROOT, "play-store", "out", "resolved-prices.csv");

const require = createRequire(import.meta.url);

function isZeroDecimal(currency) {
  return ZERO_DECIMAL_CURRENCIES.has(currency);
}

function asBigInt(value) {
  if (value == null || value === "") return 0n;
  return BigInt(value);
}

function moneyToNanos(money) {
  return asBigInt(money?.units) * 1000000000n + asBigInt(money?.nanos);
}

function moneyAmount(money) {
  const nanos = moneyToNanos(money);
  const negative = nanos < 0n;
  const abs = negative ? -nanos : nanos;
  const units = abs / 1000000000n;
  const fraction = abs % 1000000000n;
  const amount = Number(units) + Number(fraction) / 1e9;
  return negative ? -amount : amount;
}

function formatMoneyAmount(money) {
  if (!money) return "";
  const nanos = moneyToNanos(money);
  const negative = nanos < 0n;
  const abs = negative ? -nanos : nanos;
  const units = abs / 1000000000n;
  const fraction = (abs % 1000000000n).toString().padStart(9, "0").replace(/0+$/, "");
  const text = fraction.length ? `${units}.${fraction}` : `${units}`;
  return negative ? `-${text}` : text;
}

function normalizeMoney(money) {
  if (!money?.currencyCode) {
    throw new Error("Price is missing currencyCode.");
  }
  return {
    currencyCode: money.currencyCode,
    units: asBigInt(money.units).toString(),
    nanos: Number(asBigInt(money.nanos)),
  };
}

function amountToMoney(currency, amount, zeroDecimal) {
  if (!currency) throw new Error("Cannot build a price without a currency.");
  if (zeroDecimal) {
    return {
      currencyCode: currency,
      units: String(Math.round(amount)),
      nanos: 0,
    };
  }
  const sign = amount < 0 ? -1 : 1;
  const cents = BigInt(Math.round(Math.abs(amount) * 100));
  const units = cents / 100n;
  const nanos = (cents % 100n) * 10000000n;
  return {
    currencyCode: currency,
    units: String(sign < 0 ? -units : units),
    nanos: Number(sign < 0 ? -nanos : nanos),
  };
}

function sameMoney(left, right) {
  if (!left || !right) return false;
  return left.currencyCode === right.currencyCode && moneyToNanos(left) === moneyToNanos(right);
}

function exceeds(amount, playMoney, zeroDecimal) {
  if (!playMoney) return false;
  const candidate = amountToMoney(playMoney.currencyCode, amount, zeroDecimal);
  return moneyToNanos(candidate) > moneyToNanos(playMoney);
}

function nearestCharm(amount) {
  if (!Number.isFinite(amount)) return 0.49;
  const cents = Math.round(amount * 100);
  if (cents <= 49) return 0.49;
  const steps = Math.round((cents - 49) / 50);
  return (49 + 50 * Math.max(0, steps)) / 100;
}

function lowestCharmAtOrBelow(capAmount) {
  const capCents = Math.floor(capAmount * 100 + 1e-6);
  if (capCents < 49) return null;
  const steps = Math.floor((capCents - 49) / 50);
  return (49 + 50 * steps) / 100;
}

function roundInt2Sig(amount) {
  const value = Math.round(amount);
  if (value <= 0) return 0;
  const digits = String(Math.abs(value));
  if (digits.length <= 2) return value;
  const factor = 10 ** (digits.length - 2);
  return Math.round(value / factor) * factor;
}

function floorInt2Sig(amount) {
  const value = Math.floor(amount + 1e-9);
  if (value <= 0) return 0;
  const digits = String(value);
  if (digits.length <= 2) return value;
  const factor = 10 ** (digits.length - 2);
  return Math.floor(value / factor) * factor;
}

function parseCsv(text) {
  const rows = [];
  let row = [];
  let field = "";
  let inQuotes = false;
  const source = text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
  for (let i = 0; i < source.length; i++) {
    const char = source[i];
    if (inQuotes) {
      if (char === '"') {
        if (source[i + 1] === '"') {
          field += '"';
          i += 1;
        } else {
          inQuotes = false;
        }
      } else {
        field += char;
      }
      continue;
    }
    if (char === '"') {
      inQuotes = true;
    } else if (char === ",") {
      row.push(field);
      field = "";
    } else if (char === "\n") {
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else if (char !== "\r") {
      field += char;
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows.filter((cells) => cells.some((cell) => cell.trim() !== ""));
}

function normalizeCurrency(value) {
  return String(value ?? "").trim().toUpperCase();
}

function readDonationCsv(csvPath) {
  const table = parseCsv(readFileSync(csvPath, "utf8"));
  const header = (table[0] || []).map((cell) => cell.trim());
  const expected = ["region", "currency", "price", "usd_equiv"];
  if (expected.some((name, index) => header[index] !== name)) {
    throw new Error(`Unexpected CSV header in ${csvPath}: ${header.join(",")}`);
  }
  const byRegion = new Map();
  for (const cells of table.slice(1)) {
    const region = String(cells[0] ?? "").trim().toUpperCase();
    if (!region) continue;
    const price = Number(String(cells[2] ?? "").trim());
    const usdEquiv = Number(String(cells[3] ?? "").trim());
    byRegion.set(region, {
      region,
      currency: normalizeCurrency(cells[1]),
      price: Number.isFinite(price) ? price : null,
      usdEquiv: Number.isFinite(usdEquiv) ? usdEquiv : null,
    });
  }
  return byRegion;
}

function forcedPrice(region) {
  if (region === "US") {
    return { currency: "USD", amount: 1.99 };
  }
  if (region === "BR") {
    return { currency: "BRL", amount: 4.99 };
  }
  return null;
}

function decidePrice({ region, csv, playMoney, existingMoney }) {
  const forced = forcedPrice(region);
  if (forced) {
    return {
      region,
      currency: forced.currency,
      amount: forced.amount,
      money: amountToMoney(forced.currency, forced.amount, false),
      source: "forced",
    };
  }

  const playCurrency = playMoney?.currencyCode || null;
  const existingCurrency = existingMoney?.price?.currencyCode || null;
  const currency = playCurrency || existingCurrency;
  const zeroDecimal = isZeroDecimal(currency);
  let source;
  let amount;
  let useExact = null;

  const currencyMatches = Boolean(
    csv && currency && csv.currency === currency && Number.isFinite(csv.price),
  );

  if (currencyMatches) {
    source = "csv";
    amount = csv.price;
  } else if (csv && Number.isFinite(csv.usdEquiv) && playMoney) {
    source = "scaled";
    const scaled = moneyAmount(playMoney) * (csv.usdEquiv / TARGET_USD);
    amount = zeroDecimal ? roundInt2Sig(scaled) : nearestCharm(scaled);
    if (exceeds(amount, playMoney, zeroDecimal)) {
      if (zeroDecimal) {
        const floored = floorInt2Sig(moneyAmount(playMoney));
        if (floored >= 1 && !exceeds(floored, playMoney, true)) {
          amount = floored;
        } else {
          useExact = normalizeMoney(playMoney);
        }
      } else {
        const stepped = lowestCharmAtOrBelow(moneyAmount(playMoney));
        if (stepped != null && !exceeds(stepped, playMoney, false)) {
          amount = stepped;
        } else {
          useExact = normalizeMoney(playMoney);
        }
      }
    }
  } else if (playMoney) {
    source = "fallback";
    useExact = normalizeMoney(playMoney);
    amount = moneyAmount(playMoney);
  } else if (existingMoney?.price) {
    source = "existing";
    useExact = normalizeMoney(existingMoney.price);
    amount = moneyAmount(existingMoney.price);
  } else {
    throw new Error(`Cannot price region ${region}: no Play conversion, CSV currency match, or existing price.`);
  }

  let money = useExact || amountToMoney(currency, amount, zeroDecimal);
  if (playMoney && source !== "existing" && moneyToNanos(money) > moneyToNanos(playMoney)) {
    money = normalizeMoney(playMoney);
  }

  return {
    region,
    currency: money.currencyCode,
    amount: moneyAmount(money),
    money,
    source,
  };
}

function findBuyOption(product) {
  const option = (product.purchaseOptions || []).find(
    (item) => item.purchaseOptionId === PURCHASE_OPTION_ID,
  );
  if (!option) {
    const ids = (product.purchaseOptions || []).map((item) => item.purchaseOptionId).join(", ") || "(none)";
    throw new Error(`Purchase option '${PURCHASE_OPTION_ID}' was not found. Options: ${ids}`);
  }
  return option;
}

function enUsTitle(product) {
  const listing = (product.listings || []).find((item) => item.languageCode === "en-US");
  return listing ? listing.title ?? "" : null;
}

function buildResolvedRows({ playPrices, existingConfigs, csvByRegion }) {
  const existingByRegion = new Map(existingConfigs.map((config) => [config.regionCode, config]));
  const regions = new Set([
    ...playPrices.keys(),
    ...existingByRegion.keys(),
    "US",
    "BR",
  ]);
  const rows = [];
  for (const region of [...regions].sort((a, b) => a.localeCompare(b))) {
    const existing = existingByRegion.get(region);
    const decided = decidePrice({
      region,
      csv: csvByRegion.get(region) || null,
      playMoney: playPrices.get(region) || null,
      existingMoney: existing || null,
    });
    rows.push({
      ...decided,
      oldMoney: existing?.price ? normalizeMoney(existing.price) : null,
    });
  }
  return rows;
}

function csvField(value) {
  const text = value == null ? "" : String(value);
  if (/[",\n\r]/.test(text)) return `"${text.replaceAll('"', '""')}"`;
  return text;
}

function writeResolvedCsv(rows) {
  mkdirSync(dirname(OUT_PATH), { recursive: true });
  const lines = ["region,currency,price,source,old_price"];
  for (const row of rows) {
    lines.push([
      row.region,
      row.currency,
      formatMoneyAmount(row.money),
      row.source,
      row.oldMoney ? formatMoneyAmount(row.oldMoney) : "",
    ].map(csvField).join(","));
  }
  writeFileSync(OUT_PATH, `${lines.join("\n")}\n`);
}

function buildPatchedProduct(original, rows) {
  const patched = structuredClone(original);
  const buy = findBuyOption(patched);
  const existing = buy.regionalPricingAndAvailabilityConfigs || [];
  const existingByRegion = new Map(existing.map((config) => [config.regionCode, config]));
  const rowByRegion = new Map(rows.map((row) => [row.region, row]));
  const nextConfigs = [];

  for (const config of existing) {
    const row = rowByRegion.get(config.regionCode);
    if (!row) {
      nextConfigs.push(structuredClone(config));
      continue;
    }
    const next = structuredClone(config);
    next.price = normalizeMoney(row.money);
    nextConfigs.push(next);
  }

  const addedRegions = rows
    .map((row) => row.region)
    .filter((region) => !existingByRegion.has(region))
    .sort((a, b) => a.localeCompare(b));
  for (const region of addedRegions) {
    nextConfigs.push({
      regionCode: region,
      price: normalizeMoney(rowByRegion.get(region).money),
      availability: "AVAILABLE",
    });
  }

  buy.regionalPricingAndAvailabilityConfigs = nextConfigs;
  assertUntouched(original, patched);
  return patched;
}

function assertUntouched(original, patched) {
  if (JSON.stringify(original.listings ?? null) !== JSON.stringify(patched.listings ?? null)) {
    throw new Error("Refusing to update listings.");
  }
  if (
    JSON.stringify(original.taxAndComplianceSettings ?? null) !==
    JSON.stringify(patched.taxAndComplianceSettings ?? null)
  ) {
    throw new Error("Refusing to update taxAndComplianceSettings.");
  }
  const originalBuy = findBuyOption(original);
  const patchedBuy = findBuyOption(patched);
  if (JSON.stringify(originalBuy.newRegionsConfig ?? null) !== JSON.stringify(patchedBuy.newRegionsConfig ?? null)) {
    throw new Error("Refusing to update newRegionsConfig.");
  }
  if (
    JSON.stringify(originalBuy.taxAndComplianceSettings ?? null) !==
    JSON.stringify(patchedBuy.taxAndComplianceSettings ?? null)
  ) {
    throw new Error("Refusing to update purchase option taxAndComplianceSettings.");
  }
  const other = (options) =>
    (options || []).filter((option) => option.purchaseOptionId !== PURCHASE_OPTION_ID);
  if (JSON.stringify(other(original.purchaseOptions)) !== JSON.stringify(other(patched.purchaseOptions))) {
    throw new Error("Refusing to update purchase options other than buy.");
  }
  const originalByRegion = new Map(
    (originalBuy.regionalPricingAndAvailabilityConfigs || []).map((config) => [config.regionCode, config]),
  );
  for (const config of patchedBuy.regionalPricingAndAvailabilityConfigs || []) {
    const previous = originalByRegion.get(config.regionCode);
    if (previous && previous.availability !== config.availability) {
      throw new Error(`Availability changed for ${config.regionCode}.`);
    }
  }
}

function diffCounts(original, patched) {
  const originalBuy = findBuyOption(original);
  const patchedBuy = findBuyOption(patched);
  const originalConfigs = originalBuy.regionalPricingAndAvailabilityConfigs || [];
  const patchedConfigs = patchedBuy.regionalPricingAndAvailabilityConfigs || [];
  const originalByRegion = new Map(originalConfigs.map((config) => [config.regionCode, config]));
  const patchedRegions = new Set(patchedConfigs.map((config) => config.regionCode));
  const counts = { unchanged: 0, changed: 0, added: 0, removed: 0 };
  const removed = [];
  for (const config of patchedConfigs) {
    const previous = originalByRegion.get(config.regionCode);
    if (!previous) counts.added += 1;
    else if (sameMoney(previous.price, config.price)) counts.unchanged += 1;
    else counts.changed += 1;
  }
  for (const region of originalByRegion.keys()) {
    if (!patchedRegions.has(region)) {
      counts.removed += 1;
      removed.push(region);
    }
  }
  return { counts, removed };
}

function printSummary(product) {
  const listings = product.listings || [];
  console.log(`Listings (${listings.length}):`);
  for (const listing of listings) {
    console.log(`  ${listing.languageCode}: ${listing.title}`);
  }
  console.log("Purchase options:");
  for (const option of product.purchaseOptions || []) {
    console.log(`  ${option.purchaseOptionId}: ${option.state ?? "(none)"}`);
  }
  const productState = product.state ?? product.status ?? "(none)";
  console.log(`Product state: ${productState}`);
  const buy = findBuyOption(product);
  const regional = buy.regionalPricingAndAvailabilityConfigs || [];
  console.log(`Regional configs for ${PURCHASE_OPTION_ID}: ${regional.length}`);
}

function printDiff(original, patched, rows) {
  const rowByRegion = new Map(rows.map((row) => [row.region, row]));
  const configs = findBuyOption(patched).regionalPricingAndAvailabilityConfigs || [];
  const lines = configs.map((config) => {
    const row = rowByRegion.get(config.regionCode);
    const oldText = row?.oldMoney
      ? `${row.oldMoney.currencyCode} ${formatMoneyAmount(row.oldMoney)}`
      : "-";
    const newText = `${config.price.currencyCode} ${formatMoneyAmount(config.price)}`;
    return {
      region: config.regionCode,
      change: `${oldText} -> ${newText}`,
      source: row?.source ?? "",
    };
  });
  const regionWidth = Math.max("region".length, ...lines.map((line) => line.region.length));
  const changeWidth = Math.max("old -> new".length, ...lines.map((line) => line.change.length));
  console.log(`${"region".padEnd(regionWidth)}  ${"old -> new".padEnd(changeWidth)}  source`);
  for (const line of lines) {
    console.log(`${line.region.padEnd(regionWidth)}  ${line.change.padEnd(changeWidth)}  ${line.source}`);
  }
  const { counts, removed } = diffCounts(original, patched);
  console.log(
    `counts: unchanged=${counts.unchanged} changed=${counts.changed} added=${counts.added} removed=${counts.removed}`,
  );
  if (counts.removed !== 0) {
    throw new Error(`Removed ${counts.removed} regions (${removed.join(", ")}). Refusing to drop regional prices.`);
  }
}

function applyFallback(rows, regions, playPrices) {
  const applied = [];
  const rowByRegion = new Map(rows.map((row) => [row.region, row]));
  for (const region of regions) {
    const playMoney = playPrices.get(region);
    const row = rowByRegion.get(region);
    if (!playMoney || !row) continue;
    const normalized = normalizeMoney(playMoney);
    if (row.source === "fallback-api" && sameMoney(row.money, normalized)) continue;
    if (sameMoney(row.money, normalized) && row.source === "fallback") {
      row.source = "fallback-api";
      applied.push(region);
      continue;
    }
    if (sameMoney(row.money, normalized)) continue;
    row.money = normalized;
    row.currency = normalized.currencyCode;
    row.amount = moneyAmount(normalized);
    row.source = "fallback-api";
    applied.push(region);
  }
  return applied;
}

function requestBody(product) {
  const body = structuredClone(product);
  for (const option of body.purchaseOptions || []) {
    // state is output-only and sits inside the purchaseOptions update mask.
    delete option.state;
  }
  return body;
}

function patchSupportsLatencyTolerance() {
  try {
    const packageJson = require.resolve("googleapis/package.json");
    const declarations = readFileSync(
      join(dirname(packageJson), "build", "src", "apis", "androidpublisher", "v3.d.ts"),
      "utf8",
    );
    const marker = "export interface Params$Resource$Monetization$Onetimeproducts$Patch";
    const start = declarations.indexOf(marker);
    if (start < 0) return false;
    const end = declarations.indexOf("export interface", start + marker.length);
    const block = declarations.slice(start, end === -1 ? undefined : end);
    return block.includes("latencyTolerance");
  } catch {
    return false;
  }
}

function formatApiError(error) {
  const data = error?.response?.data;
  if (data?.error) {
    const body = data.error;
    const pieces = [body.message, ...(body.errors || []).map((item) => item.message)].filter(Boolean);
    if (body.details) pieces.push(JSON.stringify(body.details));
    return pieces.join("\n") || JSON.stringify(data);
  }
  if (data) return typeof data === "string" ? data : JSON.stringify(data);
  if (error instanceof Error) return error.message;
  return String(error);
}

function regionsNamedByError(error, rows) {
  const text = formatApiError(error);
  const known = new Set(rows.map((row) => row.region));
  const found = new Set();
  for (const region of known) {
    if (new RegExp(`\\b${region}\\b`).test(text)) found.add(region);
  }
  const amounts = new Map();
  for (const row of rows) {
    const label = formatMoneyAmount(row.money);
    const list = amounts.get(label) || [];
    list.push(row.region);
    amounts.set(label, list);
  }
  for (const match of text.match(/\d+(?:\.\d+)?/g) || []) {
    const regions = amounts.get(match);
    if (regions?.length === 1) found.add(regions[0]);
  }
  return [...found];
}

function googleapisVersion() {
  const packageJson = JSON.parse(readFileSync(require.resolve("googleapis/package.json"), "utf8"));
  return packageJson.version;
}

function assertClientSupportsOneTimeProducts(androidpublisher) {
  const oneTimeProducts = androidpublisher.monetization?.onetimeproducts;
  if (
    typeof oneTimeProducts?.get !== "function" ||
    typeof oneTimeProducts?.patch !== "function" ||
    typeof androidpublisher.monetization?.convertRegionPrices !== "function"
  ) {
    throw new Error(
      `googleapis ${googleapisVersion()} does not expose monetization.onetimeproducts. Install googleapis@189 or newer.`,
    );
  }
  console.log(
    `googleapis ${googleapisVersion()}: monetization.onetimeproducts get/patch and convertRegionPrices are available`,
  );
}

function createClient() {
  const serviceAccountJson = process.env.PLAY_SERVICE_ACCOUNT_JSON;
  if (!serviceAccountJson) {
    throw new Error("PLAY_SERVICE_ACCOUNT_JSON is not set.");
  }
  let serviceAccount;
  try {
    serviceAccount = JSON.parse(serviceAccountJson);
  } catch {
    throw new Error("PLAY_SERVICE_ACCOUNT_JSON is not valid JSON.");
  }
  if (!serviceAccount.client_email || !serviceAccount.private_key) {
    throw new Error("PLAY_SERVICE_ACCOUNT_JSON is missing client_email or private_key.");
  }
  const auth = new google.auth.JWT({
    email: serviceAccount.client_email,
    key: serviceAccount.private_key,
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  return google.androidpublisher({ version: "v3", auth });
}

async function getProduct(androidpublisher) {
  const response = await androidpublisher.monetization.onetimeproducts.get({
    packageName: PACKAGE_NAME,
    productId: PRODUCT_ID,
  });
  return response.data;
}

async function convertUsdPrice(androidpublisher) {
  const response = await androidpublisher.monetization.convertRegionPrices({
    packageName: PACKAGE_NAME,
    requestBody: {
      price: amountToMoney("USD", TARGET_USD, false),
    },
  });
  const converted = response.data.convertedRegionPrices || {};
  const playPrices = new Map();
  for (const [regionCode, value] of Object.entries(converted)) {
    const region = value.regionCode || regionCode;
    if (!value.price) {
      throw new Error(`convertRegionPrices did not return a price for ${region}.`);
    }
    playPrices.set(region, normalizeMoney(value.price));
  }
  if (playPrices.size === 0) {
    throw new Error("convertRegionPrices returned no convertedRegionPrices.");
  }
  const regionVersion = response.data.regionVersion?.version || REGIONS_VERSION;
  console.log(
    `Play converted USD ${TARGET_USD} for ${playPrices.size} regions (region version ${regionVersion}).`,
  );
  return { playPrices, regionVersion };
}

async function patchProduct(androidpublisher, product, regionVersion) {
  const version = regionVersion || REGIONS_VERSION;
  console.log(`Patching one-time product with regions version ${version}.`);
  const params = {
    packageName: PACKAGE_NAME,
    productId: PRODUCT_ID,
    updateMask: "purchaseOptions",
    "regionsVersion.version": version,
    allowMissing: false,
    requestBody: requestBody(product),
  };
  if (patchSupportsLatencyTolerance()) {
    params.latencyTolerance = LATENCY_TOLERANCE;
  } else {
    console.log("Installed googleapis client has no latencyTolerance parameter; omitting it.");
  }
  const response = await androidpublisher.monetization.onetimeproducts.patch(params);
  return response.data;
}

function verifyProduct(before, after, rows) {
  const failures = [];
  const beforeBuy = findBuyOption(before);
  const afterBuy = findBuyOption(after);
  if (afterBuy.state !== "ACTIVE") {
    failures.push(`purchase option ${PURCHASE_OPTION_ID} state is ${afterBuy.state ?? "(none)"}, expected ACTIVE`);
  }
  if (after.state != null && after.state !== "ACTIVE") {
    failures.push(`product state is ${after.state}, expected ACTIVE`);
  }
  const beforeListings = before.listings || [];
  const afterListings = after.listings || [];
  if (afterListings.length !== beforeListings.length) {
    failures.push(`listings count changed from ${beforeListings.length} to ${afterListings.length}`);
  }
  const beforeTitle = enUsTitle(before);
  const afterTitle = enUsTitle(after);
  if (beforeTitle !== afterTitle) {
    failures.push(`en-US title changed from ${JSON.stringify(beforeTitle)} to ${JSON.stringify(afterTitle)}`);
  }

  const afterByRegion = new Map(
    (afterBuy.regionalPricingAndAvailabilityConfigs || []).map((config) => [config.regionCode, config]),
  );
  for (const row of rows) {
    const config = afterByRegion.get(row.region);
    if (!config) {
      failures.push(`${row.region} is missing after update`);
      continue;
    }
    if (!sameMoney(config.price, row.money)) {
      failures.push(
        `${row.region} price is ${config.price?.currencyCode ?? "?"} ${config.price ? formatMoneyAmount(config.price) : "?"} , expected ${row.currency} ${formatMoneyAmount(row.money)}`,
      );
    }
  }
  const expectedRegions = new Set(rows.map((row) => row.region));
  for (const region of afterByRegion.keys()) {
    if (!expectedRegions.has(region)) {
      failures.push(`${region} is present on Play but not in the resolved prices`);
    }
  }

  console.log("Final prices:");
  for (const region of REPORT_REGIONS) {
    const config = afterByRegion.get(region);
    if (!config?.price) {
      console.log(`${region} MISSING`);
      failures.push(`report region ${region} is missing`);
    } else {
      console.log(`${region} ${config.price.currencyCode} ${formatMoneyAmount(config.price)}`);
    }
  }

  if (failures.length > 0) {
    console.error("Verification failed:");
    for (const failure of failures) console.error(`- ${failure}`);
    return false;
  }
  console.log("Verification passed.");
  return true;
}

async function main() {
  const androidpublisher = createClient();
  assertClientSupportsOneTimeProducts(androidpublisher);

  const original = await getProduct(androidpublisher);
  printSummary(original);
  const buy = findBuyOption(original);
  const existingConfigs = buy.regionalPricingAndAvailabilityConfigs || [];

  const { playPrices, regionVersion } = await convertUsdPrice(androidpublisher);
  const csvByRegion = readDonationCsv(CSV_PATH);
  let rows = buildResolvedRows({ playPrices, existingConfigs, csvByRegion });
  writeResolvedCsv(rows);
  console.log(`Wrote ${rows.length} rows to ${OUT_PATH}`);

  let patched = buildPatchedProduct(original, rows);
  printDiff(original, patched, rows);

  if (process.env.DRY_RUN !== "false") {
    console.log("DRY_RUN is not 'false'; not updating Play.");
    return;
  }

  const fellBack = [];
  for (let attempt = 0; ; attempt++) {
    try {
      await patchProduct(androidpublisher, patched, regionVersion);
      break;
    } catch (error) {
      const message = formatApiError(error);
      if (attempt >= MAX_PATCH_RETRIES) {
        throw new Error(message);
      }
      const named = regionsNamedByError(error, rows);
      const applied = applyFallback(rows, named, playPrices);
      if (applied.length === 0) {
        throw new Error(message);
      }
      fellBack.push(...applied);
      console.log(`Play rejected the update (${message})`);
      console.log(`Falling back to Play's converted USD ${TARGET_USD} price for: ${applied.join(", ")}`);
      writeResolvedCsv(rows);
      patched = buildPatchedProduct(original, rows);
      printDiff(original, patched, rows);
    }
  }
  if (fellBack.length > 0) {
    console.log(`Regions set to fallback-api: ${[...new Set(fellBack)].join(", ")}`);
  }

  const updated = await getProduct(androidpublisher);
  if (!verifyProduct(original, updated, rows)) {
    process.exitCode = 1;
  }
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : "";
if (invokedPath === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(formatApiError(error));
    process.exit(1);
  });
}

export {
  OUT_PATH,
  ZERO_DECIMAL_CURRENCIES,
  applyFallback,
  assertUntouched,
  buildPatchedProduct,
  buildResolvedRows,
  decidePrice,
  diffCounts,
  floorInt2Sig,
  formatApiError,
  formatMoneyAmount,
  nearestCharm,
  parseCsv,
  readDonationCsv,
  regionsNamedByError,
  roundInt2Sig,
  sameMoney,
};
