package com.geison.phonereminder.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.geison.phonereminder.DriveSyncStatus
import com.geison.phonereminder.DriveSyncUiState
import com.geison.phonereminder.R
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
    @Config(qualifiers = "w412dp-h120dp-xhdpi")
    fun captureTopBarPreview() {
        captureRoboImage(filePath = "/workspace/donate_topbar_preview.png") {
            ReminderPreviewTheme {
                FixedMenuBar(
                    title = stringResource(R.string.screen_title_home),
                    action = { HomeTopBarActionsPreview(onConfig = {}) },
                )
            }
        }
    }
}

@Composable
private fun HomeTopBarActionsPreview(onConfig: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Outlined.SportsBar,
                contentDescription = stringResource(R.string.action_buy_me_a_beer),
                tint = Color.White,
            )
        }
        IconButton(onClick = onConfig) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.action_open_settings),
                tint = Color.White,
            )
        }
    }
}
