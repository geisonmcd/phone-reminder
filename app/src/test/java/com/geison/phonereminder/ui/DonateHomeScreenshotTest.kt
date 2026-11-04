package com.geison.phonereminder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.geison.phonereminder.DriveSyncStatus
import com.geison.phonereminder.R
import com.geison.phonereminder.DriveSyncUiState
import com.geison.phonereminder.billing.DonationCatalogResult
import com.geison.phonereminder.billing.DonationProduct
import com.geison.phonereminder.billing.DonationProducts
import com.geison.phonereminder.data.ReminderItem
import com.geison.phonereminder.data.ScheduleSettings
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DonateHomeScreenshotTest {

    @Before
    fun enableRoborazziRecord() {
        System.setProperty("roborazzi.test.record", "true")
    }

    private val sampleReminders = listOf(
        ReminderItem(
            id = "preview-1",
            text = "Drink water",
            createdAtEpochMillis = 1_700_000_000_000L,
            updatedAtEpochMillis = 1_700_000_000_000L,
            schedule = ScheduleSettings(),
        ),
        ReminderItem(
            id = "preview-2",
            text = "Stretch for five minutes",
            createdAtEpochMillis = 1_699_000_000_000L,
            updatedAtEpochMillis = 1_699_000_000_000L,
            schedule = ScheduleSettings(),
        ),
    )

    private val driveSyncState = DriveSyncUiState(
        isEnabled = true,
        status = DriveSyncStatus.NotConnected,
        lastSyncAtEpochMillis = null,
    )

    private val fakeDonationProducts = DonationCatalogResult.Ready(
        products = listOf(
            DonationProduct(DonationProducts.SMALL, "R$ 4,99"),
            DonationProduct(DonationProducts.MEDIUM, "R$ 9,99"),
            DonationProduct(DonationProducts.LARGE, "R$ 19,99"),
        ),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun captureHomeScreenPreview() {
        captureRoboImage(filePath = "/workspace/donate_home_preview.png") {
            ReminderPreviewTheme {
                HomeScreen(
                    reminderCount = sampleReminders.size,
                    reminders = sampleReminders,
                    reminderFilter = "",
                    onFilterChange = {},
                    onConfig = {},
                    onNewReminder = {},
                    onEdit = {},
                    driveSyncState = driveSyncState,
                    onDriveConnect = {},
                    onDriveSyncNow = {},
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h640dp-xhdpi")
    fun captureDonationSheetPreview() {
        captureRoboImage(filePath = "/workspace/donate_sheet_preview.png") {
            ReminderPreviewTheme {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp, bottom = 32.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_buy_me_a_beer),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        DonationSheetContent(
                            isLoading = false,
                            catalogResult = fakeDonationProducts,
                            purchaseEvent = null,
                            onProductClick = {},
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
