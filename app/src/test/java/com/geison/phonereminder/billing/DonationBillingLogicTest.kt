package com.geison.phonereminder.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationBillingLogicTest {

    @Test
    fun orderedProductIds() {
        assertEquals(
            listOf(DonationProducts.SMALL),
            DonationProducts.orderedIds(),
        )
    }

    @Test
    fun mapQueriedProducts_empty_returnsUnavailable() {
        assertEquals(DonationCatalogResult.Unavailable, mapQueriedProducts(emptyMap()))
    }

    @Test
    fun mapQueriedProducts_small_returnsReady() {
        val result = mapQueriedProducts(
            mapOf(DonationProducts.SMALL to "R$ 4,99"),
        )
        assertTrue(result is DonationCatalogResult.Ready)
        val products = (result as DonationCatalogResult.Ready).products
        assertEquals(listOf(DonationProducts.SMALL), products.map { it.productId })
        assertEquals("R$ 4,99", products.single().formattedPrice)
    }

    @Test
    fun mapQueriedProducts_unknownProduct_returnsUnavailable() {
        assertEquals(
            DonationCatalogResult.Unavailable,
            mapQueriedProducts(mapOf("other_product" to "R$ 1,00")),
        )
    }

    @Test
    fun mapPurchaseUpdate_userCanceled_returnsCancelled() {
        assertEquals(
            DonationPurchaseEvent.Cancelled,
            mapPurchaseUpdate(
                DonationBillingResponseCode.USER_CANCELED,
                emptyList(),
                emptyList(),
            ),
        )
    }

    @Test
    fun mapPurchaseUpdate_purchasedDonation_returnsThankYou() {
        assertEquals(
            DonationPurchaseEvent.ThankYou,
            mapPurchaseUpdate(
                DonationBillingResponseCode.OK,
                listOf(DonationPurchaseState.PURCHASED),
                listOf(DonationProducts.SMALL),
            ),
        )
    }

    @Test
    fun mapPurchaseUpdate_pending_returnsPending() {
        assertEquals(
            DonationPurchaseEvent.Pending,
            mapPurchaseUpdate(
                DonationBillingResponseCode.OK,
                listOf(DonationPurchaseState.PENDING),
                listOf(DonationProducts.SMALL),
            ),
        )
    }

    @Test
    fun mapPurchaseUpdate_unknownError_returnsFailed() {
        assertEquals(
            DonationPurchaseEvent.Failed(null),
            mapPurchaseUpdate(
                responseCode = 6,
                purchaseStates = emptyList(),
                productIds = listOf(DonationProducts.SMALL),
            ),
        )
    }

    @Test
    fun mapPurchaseUpdate_nonDonationProduct_returnsNull() {
        assertNull(
            mapPurchaseUpdate(
                DonationBillingResponseCode.OK,
                listOf(DonationPurchaseState.PURCHASED),
                listOf("unrelated"),
            ),
        )
    }
}
