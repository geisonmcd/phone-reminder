package com.geison.phonereminder.billing

import android.app.Activity

data class DonationProduct(
    val productId: String,
    val formattedPrice: String,
)

sealed interface DonationCatalogResult {
    data class Ready(val products: List<DonationProduct>) : DonationCatalogResult
    data object Unavailable : DonationCatalogResult
    data class Error(val message: String?) : DonationCatalogResult
}

sealed interface DonationPurchaseEvent {
    data object ThankYou : DonationPurchaseEvent
    data object Pending : DonationPurchaseEvent
    data object Cancelled : DonationPurchaseEvent
    data object Unavailable : DonationPurchaseEvent
    data class Failed(val message: String?) : DonationPurchaseEvent
}

object DonationBillingResponseCode {
    const val OK = 0
    const val USER_CANCELED = 1
}

object DonationPurchaseState {
    const val PURCHASED = 1
    const val PENDING = 2
}

fun mapQueriedProducts(found: Map<String, String>): DonationCatalogResult {
    if (!found.keys.any(DonationProducts::isDonationProduct)) {
        return DonationCatalogResult.Unavailable
    }
    val products = DonationProducts.orderedIds().mapNotNull { productId ->
        found[productId]?.let { price -> DonationProduct(productId, price) }
    }
    return if (products.isEmpty()) {
        DonationCatalogResult.Unavailable
    } else {
        DonationCatalogResult.Ready(products)
    }
}

fun mapPurchaseUpdate(
    responseCode: Int,
    purchaseStates: List<Int>,
    productIds: List<String>,
): DonationPurchaseEvent? {
    if (responseCode == DonationBillingResponseCode.USER_CANCELED) {
        return DonationPurchaseEvent.Cancelled
    }
    if (responseCode != DonationBillingResponseCode.OK) {
        return DonationPurchaseEvent.Failed(message = null)
    }
    if (!productIds.any(DonationProducts::isDonationProduct)) {
        return null
    }
    val state = purchaseStates.firstOrNull() ?: return null
    return when (state) {
        DonationPurchaseState.PURCHASED -> DonationPurchaseEvent.ThankYou
        DonationPurchaseState.PENDING -> DonationPurchaseEvent.Pending
        else -> DonationPurchaseEvent.Failed(message = null)
    }
}

interface DonationBillingController {
    fun start(onEvent: (DonationPurchaseEvent) -> Unit)
    fun queryProducts(callback: (DonationCatalogResult) -> Unit)
    fun purchase(activity: Activity, productId: String)
    /** Queries product details if needed, then launches billing for the single coffee tip. */
    fun purchaseDonation(activity: Activity)
    fun consumeUnconsumedPurchases()
    fun close()
}
