package com.geison.phonereminder.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationBillingLogicTest {

    @Test
    fun orderedProductIds() {
        assertEquals(
            listOf(
                DonationProducts.SMALL,
                DonationProducts.MEDIUM,
                DonationProducts.LARGE,
            ),
            DonationProducts.orderedIds(),
        )
    }

    @Test
    fun mapQueriedProducts_empty_returnsUnavailable() {
        assertEquals(DonationCatalogResult.Unavailable, mapQueriedProducts(emptyMap()))
    }

    @Test
    fun mapQueriedProducts_allThree_returnsReadySorted() {
        val result = mapQueriedProducts(
            mapOf(
                DonationProducts.LARGE to "R$ 19,99",
                DonationProducts.SMALL to "R$ 4,99",
                DonationProducts.MEDIUM to "R$ 9,99",
            ),
        )
        assertTrue(result is DonationCatalogResult.Ready)
        val products = (result as DonationCatalogResult.Ready).products
        assertEquals(
            listOf(
                DonationProducts.SMALL,
                DonationProducts.MEDIUM,
                DonationProducts.LARGE,
            ),
            products.map { it.productId },
        )
    }

    @Test
    fun mapQueriedProducts_partial_returnsReadySubset() {
        val result = mapQueriedProducts(
            mapOf(DonationProducts.MEDIUM to "R$ 9,99"),
        )
        assertTrue(result is DonationCatalogResult.Ready)
        val products = (result as DonationCatalogResult.Ready).products
        assertEquals(1, products.size)
        assertEquals(DonationProducts.MEDIUM, products.single().productId)
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
                listOf(DonationProducts.LARGE),
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
}
