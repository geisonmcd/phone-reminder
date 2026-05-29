package com.geison.phonereminder.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.geison.phonereminder.MainActivity
import com.geison.phonereminder.R
import com.geison.phonereminder.diagnostics.Diagnostics

object ReminderNotifier {
    fun showReminder(
        context: Context,
        notificationId: Int,
        reminderId: String,
        reminderText: String,
    ) {
        NotificationChannels.ensureCreated(context)
        Diagnostics.log("show_reminder_notification")

        val openAppIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title_reminder))
            .setContentText(reminderText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminderText))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (!canPostNotifications(context)) {
            Diagnostics.log("show_reminder_notification_skipped_permission")
            return
        }

        runCatching {
            postNotification(context, notificationId, notification)
        }.onFailure { error ->
            if (error is SecurityException) {
                Diagnostics.recordNonFatal(
                    area = "notification_permission_rejected",
                    throwable = error,
                )
            } else {
                throw error
            }
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(
        context: Context,
        notificationId: Int,
        notification: Notification,
    ) {
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
