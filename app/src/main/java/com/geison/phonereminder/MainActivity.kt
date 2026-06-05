package com.geison.phonereminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.geison.phonereminder.diagnostics.Diagnostics
import com.geison.phonereminder.notifications.NotificationChannels
import com.geison.phonereminder.ui.ReminderApp

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel> {
        MainViewModel.factory(application)
    }
    private var pendingOpenReminderId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenReminderId = intent.getStringExtra(EXTRA_OPEN_REMINDER_ID)
        Diagnostics.log("main_activity_on_create")
        Diagnostics.setKey("opened_from_notification", pendingOpenReminderId != null)
        enableEdgeToEdge()
        NotificationChannels.ensureCreated(this)
        viewModel.rescheduleNow()

        setContent {
            val requestPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                Diagnostics.setKey("notifications_permission_granted", granted)
                if (granted) {
                    viewModel.rescheduleNow()
                }
            }

            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED

            var permissionSkipped by remember { mutableStateOf(false) }
            var showRationale by remember { mutableStateOf(needsPermission) }

            if (showRationale && needsPermission) {
                AlertDialog(
                    onDismissRequest = {
                        showRationale = false
                        permissionSkipped = true
                        Diagnostics.setKey("notification_permission_skipped", true)
                    },
                    title = { Text(stringResource(R.string.notification_rationale_title)) },
                    text = { Text(stringResource(R.string.notification_rationale_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRationale = false
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                        ) {
                            Text(stringResource(R.string.action_grant_permission))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showRationale = false
                                permissionSkipped = true
                                Diagnostics.setKey("notification_permission_skipped", true)
                            },
                        ) {
                            Text(stringResource(R.string.action_skip))
                        }
                    },
                )
            }

            if (!needsPermission || !showRationale || permissionSkipped) {
                ReminderApp(
                    viewModel = viewModel,
                    openReminderId = pendingOpenReminderId,
                    onOpenReminderHandled = {
                        pendingOpenReminderId = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenReminderId = intent.getStringExtra(EXTRA_OPEN_REMINDER_ID)
        Diagnostics.log("main_activity_on_new_intent")
        Diagnostics.setKey("opened_from_notification", pendingOpenReminderId != null)
    }

    companion object {
        const val EXTRA_OPEN_REMINDER_ID = "extra_open_reminder_id"
    }
}
