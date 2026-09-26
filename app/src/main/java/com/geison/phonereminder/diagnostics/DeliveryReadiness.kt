package com.geison.phonereminder.diagnostics

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.geison.phonereminder.notifications.NotificationChannels

internal data class DeliveryReadiness(
    val notificationsEnabled: Boolean,
    val postNotificationsGranted: Boolean,
    val reminderChannelBlocked: Boolean,
    val ignoringBatteryOptimizations: Boolean,
) {
    fun attributes(): Map<String, String> {
        return linkedMapOf(
            "notifications_enabled" to notificationsEnabled.toString(),
            "post_notifications_granted" to postNotificationsGranted.toString(),
            "reminder_channel_blocked" to reminderChannelBlocked.toString(),
            "ignoring_battery_optimizations" to ignoringBatteryOptimizations.toString(),
        )
    }

    companion object {
        fun capture(context: Context): DeliveryReadiness {
            NotificationChannels.ensureCreated(context)
            return DeliveryReadiness(
                notificationsEnabled = notificationsEnabled(context),
                postNotificationsGranted = postNotificationsGranted(context),
                reminderChannelBlocked = reminderChannelBlocked(context),
                ignoringBatteryOptimizations = ignoringBatteryOptimizations(context),
            )
        }

        private fun notificationsEnabled(context: Context): Boolean {
            return runCatching {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }.getOrDefault(false)
        }

        private fun postNotificationsGranted(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return true
            }
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }

        private fun reminderChannelBlocked(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            val channel = manager.getNotificationChannel(NotificationChannels.REMINDER_CHANNEL_ID)
                ?: return false
            return channel.importance == NotificationManager.IMPORTANCE_NONE
        }

        private fun ignoringBatteryOptimizations(context: Context): Boolean {
            val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
            return runCatching {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }.getOrDefault(false)
        }
    }
}
