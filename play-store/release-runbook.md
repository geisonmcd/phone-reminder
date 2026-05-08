# Play Store Release Runbook

Use this when preparing a Play Console release for Smart Random Reminder.

## 1. Build Locally

Run:

```sh
./gradlew test
./gradlew bundleRelease
```

Expected bundle:

```text
app/build/outputs/bundle/release/app-release.aab
```

Alternatively, run the `Android Closed Testing Release` workflow in GitHub Actions. It asks for `versionCode` and `versionName`, runs tests, builds a signed `.aab`, stores it as the `smart-random-reminder-release-aab` artifact for 14 days, and uploads it to the Play Console closed testing `alpha` track.

Use a `versionCode` higher than every previous upload in Play Console. For example, if the latest uploaded bundle is `1`, the next release should use `2`.

The workflow requires these GitHub repository secrets:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- `PLAY_SERVICE_ACCOUNT_JSON`

See `play-store/google-play-api-setup.md` if `PLAY_SERVICE_ACCOUNT_JSON` has not been configured yet.

## 2. Store Listing

Open the default store listing and fill:

- App name: `Smart Random Reminder`
- Short description: `play-store/short-description.txt`
- Full description: `play-store/full-description.txt`
- App icon: `play-store/graphics/app-icon-512.png`
- Feature graphic: `play-store/graphics/feature-graphic-1024x500.png`
- Phone screenshots: all files in `play-store/screenshots/`

Save the listing, then send it to Publishing overview when prompted.

## 3. App Content

Complete or verify these Play Console declarations:

- App access: all features are available without special access.
- Ads: the app does not contain ads.
- Advertising ID: the app does not use an advertising ID.
- Government apps: not a government app.
- Financial features: no financial features.
- Health apps: no health features.
- Privacy policy: `https://geisonmcd.github.io/phone-reminder/privacy-policy.html`
- Data safety: use `play-store/data-safety.md`.
- Content rating: submit the questionnaire.
- Target audience and content: select the correct audience for the app.

## 4. Closed Testing Track

The GitHub Actions workflow can create the release in `Test and release > Testing > Closed testing > Alpha` automatically.

For a manual fallback:

1. Select countries/regions.
2. Select testers.
3. Create a new release.
4. Add the app bundle from the library or upload `app-release.aab`.
5. Confirm the release name.
6. Paste release notes from `play-store/release-notes-en-US.txt` inside the `<en-US>` tags.
7. Review the release.
8. Save the release.

## 5. Publishing Overview

Open Publishing overview and wait for quick checks to complete.

If `View issues` appears, open it and fix the blocking item. Common blockers:

- Advertising ID declaration incomplete.
- Required app content declarations incomplete.
- Closed testing release not saved.

When the button is enabled, click:

```text
Submit changes for review
```

Confirm the modal. The expected state after submission is:

```text
Changes in review
```
