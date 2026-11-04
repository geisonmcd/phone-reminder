package com.geison.phonereminder.billing

object DonationProducts {
    const val SMALL = "donation_beer_small"
    val ALL: List<String> = listOf(SMALL)

    fun orderedIds(): List<String> = ALL

    fun isDonationProduct(productId: String): Boolean = productId == SMALL
}
