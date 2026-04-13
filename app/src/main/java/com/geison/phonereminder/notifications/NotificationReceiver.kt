package com.geison.phonereminder.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val reminderText = intent.getStringExtra(EXTRA_REMINDER_TEXT) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        ReminderNotifier.showReminder(
            context = context,
            notificationId = notificationId,
            reminderText = reminderText,
        )
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.geison.phonereminder.action.SHOW_REMINDER"
        const val EXTRA_REMINDER_TEXT = "extra_reminder_text"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
