package com.geison.phonereminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geison.phonereminder.R
import com.geison.phonereminder.billing.DonationCatalogResult
import com.geison.phonereminder.billing.DonationProduct
import com.geison.phonereminder.billing.DonationProducts
import com.geison.phonereminder.billing.DonationPurchaseEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DonationBottomSheet(
    visible: Boolean,
    isLoading: Boolean,
    catalogResult: DonationCatalogResult?,
    purchaseEvent: DonationPurchaseEvent?,
    onDismiss: () -> Unit,
    onProductClick: (String) -> Unit,
) {
    if (!visible) {
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = stringResource(R.string.action_buy_me_a_beer),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            DonationSheetContent(
                isLoading = isLoading,
                catalogResult = catalogResult,
                purchaseEvent = purchaseEvent,
                onProductClick = onProductClick,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
internal fun DonationSheetContent(
    isLoading: Boolean,
    catalogResult: DonationCatalogResult?,
    purchaseEvent: DonationPurchaseEvent?,
    onProductClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            purchaseEvent is DonationPurchaseEvent.ThankYou -> {
                Text(
                    text = stringResource(R.string.donation_thank_you),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            purchaseEvent is DonationPurchaseEvent.Pending -> {
                Text(
                    text = stringResource(R.string.donation_pending),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            purchaseEvent is DonationPurchaseEvent.Failed -> {
                Text(
                    text = purchaseEvent.message?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.donation_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            isLoading -> {
                Text(
                    text = stringResource(R.string.donation_sheet_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            catalogResult is DonationCatalogResult.Ready -> {
                Text(
                    text = stringResource(R.string.donation_sheet_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                catalogResult.products.forEachIndexed { index, product ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    DonationProductRow(
                        product = product,
                        onClick = { onProductClick(product.productId) },
                    )
                }
            }

            catalogResult is DonationCatalogResult.Error -> {
                Text(
                    text = catalogResult.message?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.donation_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            else -> {
                Text(
                    text = stringResource(R.string.donation_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun DonationProductRow(
    product: DonationProduct,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = donationProductLabel(product.productId),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = product.formattedPrice,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun donationProductLabel(productId: String): String {
    return when (productId) {
        DonationProducts.SMALL -> stringResource(R.string.donation_beer_small)
        DonationProducts.MEDIUM -> stringResource(R.string.donation_beer_medium)
        DonationProducts.LARGE -> stringResource(R.string.donation_beer_large)
        else -> productId
    }
}
