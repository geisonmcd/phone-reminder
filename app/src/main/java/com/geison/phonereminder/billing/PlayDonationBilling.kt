package com.geison.phonereminder.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

class PlayDonationBilling(context: Context) : DonationBillingController {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val detailsByProductId = mutableMapOf<String, ProductDetails>()
    private var purchaseEventListener: ((DonationPurchaseEvent) -> Unit)? = null
    private var connecting = false
    private val pendingOnReady = mutableListOf<() -> Unit>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (purchases.isNullOrEmpty()) {
            mapPurchaseUpdate(result.responseCode, emptyList(), emptyList())?.let(::emitPurchaseEvent)
            return@PurchasesUpdatedListener
        }
        purchases.forEach { purchase ->
            val event = mapPurchaseUpdate(
                responseCode = result.responseCode,
                purchaseStates = listOf(purchase.purchaseState),
                productIds = purchase.products,
            ) ?: return@forEach
            when (event) {
                DonationPurchaseEvent.ThankYou -> consumeAndEmitThankYou(purchase)
                DonationPurchaseEvent.Cancelled -> emitPurchaseEvent(event)
                else -> emitPurchaseEvent(event)
            }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    override fun start(onEvent: (DonationPurchaseEvent) -> Unit) {
        purchaseEventListener = onEvent
        ensureConnected {
            consumeUnconsumedPurchases()
        }
    }

    override fun queryProducts(callback: (DonationCatalogResult) -> Unit) {
        if (!client.isReady) {
            ensureConnected { queryProducts(callback) }
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                DonationProducts.ALL.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                },
            )
            .build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                post {
                    callback(
                        if (result.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                            DonationCatalogResult.Unavailable
                        } else {
                            DonationCatalogResult.Error(result.debugMessage)
                        },
                    )
                }
                return@queryProductDetailsAsync
            }
            val priceById = mutableMapOf<String, String>()
            queryResult.productDetailsList.forEach { product ->
                val offer = product.oneTimePurchaseOfferDetailsList?.firstOrNull()
                    ?: return@forEach
                detailsByProductId[product.productId] = product
                priceById[product.productId] = offer.formattedPrice
            }
            post { callback(mapQueriedProducts(priceById)) }
        }
    }

    override fun purchase(activity: Activity, productId: String) {
        if (!DonationProducts.isDonationProduct(productId)) {
            emitPurchaseEvent(DonationPurchaseEvent.Failed(message = null))
            return
        }
        if (!client.isReady) {
            emitPurchaseEvent(DonationPurchaseEvent.Failed(message = null))
            ensureConnected()
            return
        }
        val details = detailsByProductId[productId]
        if (details == null) {
            queryProducts { result ->
                if (result !is DonationCatalogResult.Ready) {
                    emitPurchaseEvent(DonationPurchaseEvent.Failed(message = null))
                    return@queryProducts
                }
                launchBillingFlow(activity, productId)
            }
            return
        }
        launchBillingFlow(activity, productId)
    }

    override fun consumeUnconsumedPurchases() {
        if (!client.isReady) {
            ensureConnected { consumeUnconsumedPurchases() }
            return
        }
        queryInAppPurchases { purchases ->
            purchases
                .filter { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        purchase.products.any(DonationProducts::isDonationProduct)
                }
                .forEach(::consumePurchaseSilently)
        }
    }

    override fun close() {
        client.endConnection()
        connecting = false
    }

    private fun launchBillingFlow(activity: Activity, productId: String) {
        val details = detailsByProductId[productId]
        if (details == null) {
            emitPurchaseEvent(DonationPurchaseEvent.Failed(message = null))
            return
        }
        val offerToken = details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                offerToken?.takeIf { it.isNotBlank() }?.let(::setOfferToken)
            }
            .build()
        val flowResult = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build(),
        )
        if (flowResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            emitPurchaseEvent(DonationPurchaseEvent.Cancelled)
        } else if (flowResult.responseCode != BillingClient.BillingResponseCode.OK) {
            emitPurchaseEvent(DonationPurchaseEvent.Failed(flowResult.debugMessage))
        }
    }

    private fun consumeAndEmitThankYou(purchase: Purchase) {
        if (!purchase.products.any(DonationProducts::isDonationProduct)) {
            return
        }
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.consumeAsync(params) { result, _ ->
            post {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    emitPurchaseEvent(DonationPurchaseEvent.ThankYou)
                } else {
                    emitPurchaseEvent(DonationPurchaseEvent.Failed(result.debugMessage))
                }
            }
        }
    }

    private fun consumePurchaseSilently(purchase: Purchase) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.consumeAsync(params) { _, _ -> }
    }

    private fun queryInAppPurchases(onPurchases: (List<Purchase>) -> Unit) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            post {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onPurchases(purchases)
                }
            }
        }
    }

    private fun ensureConnected(onReady: (() -> Unit)? = null) {
        if (client.isReady) {
            onReady?.let { post(it) }
            return
        }
        onReady?.let { pendingOnReady.add(it) }
        if (connecting) {
            return
        }
        connecting = true
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    post {
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            val callbacks = pendingOnReady.toList()
                            pendingOnReady.clear()
                            callbacks.forEach { it.invoke() }
                        } else {
                            pendingOnReady.clear()
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                }
            },
        )
    }

    private fun emitPurchaseEvent(event: DonationPurchaseEvent) {
        post { purchaseEventListener?.invoke(event) }
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
