package com.geison.phonereminder.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.geison.phonereminder.R

object NotificationChannels {
    const val REMINDER_CHANNEL_ID = "reminders"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_description)
        }
        manager.createNotificationChannel(channel)
    }
}
