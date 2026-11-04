package com.geison.phonereminder.billing

object DonationProducts {
    const val SMALL = "donation_beer_small"
    const val MEDIUM = "donation_beer_medium"
    const val LARGE = "donation_beer_large"
    val ALL: List<String> = listOf(SMALL, MEDIUM, LARGE)

    /** Display order for the sheet. */
    fun orderedIds(): List<String> = ALL

    fun isDonationProduct(productId: String): Boolean = productId in ALL
}
