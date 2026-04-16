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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.geison.phonereminder.notifications.NotificationChannels
import com.geison.phonereminder.notifications.NotificationScheduler
import com.geison.phonereminder.ui.ReminderApp

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel> {
        MainViewModel.factory(application)
    }
    private var pendingOpenReminderId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenReminderId = intent.getStringExtra(EXTRA_OPEN_REMINDER_ID)
        enableEdgeToEdge()
        NotificationChannels.ensureCreated(this)
        NotificationScheduler.scheduleToday(this)

        setContent {
            val requestPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    viewModel.rescheduleNow()
                }
            }

            LaunchedEffect(Unit) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            ReminderApp(
                viewModel = viewModel,
                openReminderId = pendingOpenReminderId,
                onOpenReminderHandled = {
                    pendingOpenReminderId = null
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenReminderId = intent.getStringExtra(EXTRA_OPEN_REMINDER_ID)
    }

    companion object {
        const val EXTRA_OPEN_REMINDER_ID = "extra_open_reminder_id"
    }
}
