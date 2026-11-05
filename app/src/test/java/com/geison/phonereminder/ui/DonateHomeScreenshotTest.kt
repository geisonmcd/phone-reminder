package com.geison.phonereminder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geison.phonereminder.DriveSyncStatus
import com.geison.phonereminder.DriveSyncUiState
import com.geison.phonereminder.data.DriveSync
import com.geison.phonereminder.data.ReminderItem
import com.geison.phonereminder.data.ScheduleSettings
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
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

    private fun roborazziPreviewPath(fileName: String): String {
        File("build/roborazzi-previews").mkdirs()
        return "build/roborazzi-previews/$fileName"
    }

    private fun mirrorPreviewToWorkspace(fileName: String, destFileName: String = fileName) {
        val workspaceDir = File("/workspace")
        if (!workspaceDir.isDirectory) return
        val source = File("build/roborazzi-previews", fileName)
        if (source.isFile) {
            source.copyTo(File(workspaceDir, destFileName), overwrite = true)
        }
    }

    @Composable
    private fun HomePreview(driveSyncState: DriveSyncUiState) {
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
        val fileName = "donate_home_preview.png"
        captureRoboImage(filePath = roborazziPreviewPath(fileName)) {
            HomePreview(driveSyncDifferencesState)
        }
        mirrorPreviewToWorkspace(fileName)
        mirrorPreviewToWorkspace(fileName, "coffee_home_preview.png")
        mirrorPreviewToWorkspace(fileName, "coffee_home_preview_en.png")
    }

    @Test
    @Config(qualifiers = "pt-rBR-w412dp-h915dp-xhdpi")
    fun captureHomeScreenPreviewPt() {
        val fileName = "coffee_home_preview_pt.png"
        captureRoboImage(filePath = roborazziPreviewPath(fileName)) {
            HomePreview(driveSyncDifferencesState)
        }
        mirrorPreviewToWorkspace(fileName)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun captureHomeNotConnectedPreview() {
        val fileName = "donate_home_notconnected.png"
        captureRoboImage(filePath = roborazziPreviewPath(fileName)) {
            HomePreview(driveSyncNotConnectedState)
        }
        mirrorPreviewToWorkspace(fileName)
    }

    @Test
    @Config(qualifiers = "w412dp-h120dp-xhdpi")
    fun captureDriveDonateRowPreview() {
        val fileName = "donate_row_preview.png"
        captureRoboImage(filePath = roborazziPreviewPath(fileName)) {
            ReminderPreviewTheme {
                HomeDriveDonateRow(
                    driveSyncState = driveSyncDifferencesState,
                    donationBusy = false,
                    onConnect = {},
                    onSyncNow = {},
                    onBuyCoffee = {},
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
        }
        mirrorPreviewToWorkspace(fileName)
    }
}
