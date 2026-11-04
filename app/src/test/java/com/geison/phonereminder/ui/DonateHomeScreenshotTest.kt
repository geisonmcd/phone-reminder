package com.geison.phonereminder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geison.phonereminder.DriveSyncStatus
import com.geison.phonereminder.DriveSyncUiState
import com.geison.phonereminder.data.DriveSync
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
        ReminderItem(
            id = "preview-3",
            text = "Take medication",
            createdAtEpochMillis = 1_698_000_000_000L,
            updatedAtEpochMillis = 1_698_000_000_000L,
            schedule = ScheduleSettings(),
        ),
        ReminderItem(
            id = "preview-4",
            text = "Walk around the block",
            createdAtEpochMillis = 1_697_000_000_000L,
            updatedAtEpochMillis = 1_697_000_000_000L,
            schedule = ScheduleSettings(),
        ),
    )

    private val driveSyncDifferencesState = DriveSyncUiState(
        isEnabled = true,
        status = DriveSyncStatus.Compared(
            differences = DriveSync.Differences(
                onlyOnPhone = 1,
                onlyOnDrive = 1,
                editedOnBoth = 1,
            ),
            hasRemoteFile = true,
        ),
        lastSyncAtEpochMillis = 1_700_000_000_000L,
    )

    private val driveSyncNotConnectedState = DriveSyncUiState(
        isEnabled = true,
        status = DriveSyncStatus.NotConnected,
        lastSyncAtEpochMillis = null,
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
                    driveSyncState = driveSyncDifferencesState,
                    onDriveConnect = {},
                    onDriveSyncNow = {},
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun captureHomeNotConnectedPreview() {
        captureRoboImage(filePath = "/workspace/donate_home_notconnected.png") {
            ReminderPreviewTheme {
                HomeScreen(
                    reminderCount = sampleReminders.size,
                    reminders = sampleReminders,
                    reminderFilter = "",
                    onFilterChange = {},
                    onConfig = {},
                    onNewReminder = {},
                    onEdit = {},
                    driveSyncState = driveSyncNotConnectedState,
                    onDriveConnect = {},
                    onDriveSyncNow = {},
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h120dp-xhdpi")
    fun captureDriveBeerRowPreview() {
        captureRoboImage(filePath = "/workspace/donate_row_preview.png") {
            ReminderPreviewTheme {
                HomeDriveBeerRow(
                    driveSyncState = driveSyncDifferencesState,
                    donationBusy = false,
                    onConnect = {},
                    onSyncNow = {},
                    onBuyBeer = {},
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
        }
    }
}
