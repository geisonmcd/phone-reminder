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
    val reminderChannelImportance: String,
    val ignoringBatteryOptimizations: Boolean,
    val powerSaveMode: Boolean,
    val deviceIdleMode: Boolean,
) {
    fun attributes(): Map<String, String> {
        return linkedMapOf(
            "notifications_enabled" to notificationsEnabled.toString(),
            "post_notifications_granted" to postNotificationsGranted.toString(),
            "reminder_channel_blocked" to reminderChannelBlocked.toString(),
            "reminder_channel_importance" to reminderChannelImportance,
            "ignoring_battery_optimizations" to ignoringBatteryOptimizations.toString(),
            "power_save_mode" to powerSaveMode.toString(),
            "device_idle_mode" to deviceIdleMode.toString(),
        )
    }

    companion object {
        fun capture(context: Context): DeliveryReadiness {
            NotificationChannels.ensureCreated(context)
            val channel = reminderChannel(context)
            val power = powerState(context)
            return DeliveryReadiness(
                notificationsEnabled = notificationsEnabled(context),
                postNotificationsGranted = postNotificationsGranted(context),
                reminderChannelBlocked = channel.blocked,
                reminderChannelImportance = channel.importance,
                ignoringBatteryOptimizations = power.ignoringBatteryOptimizations,
                powerSaveMode = power.powerSaveMode,
                deviceIdleMode = power.deviceIdleMode,
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

        private fun reminderChannel(context: Context): ChannelState {
            val manager = context.getSystemService(NotificationManager::class.java)
                ?: return ChannelState(blocked = false, importance = "unknown")
            val channel = manager.getNotificationChannel(NotificationChannels.REMINDER_CHANNEL_ID)
                ?: return ChannelState(blocked = false, importance = "missing")
            return ChannelState(
                blocked = channel.importance == NotificationManager.IMPORTANCE_NONE,
                importance = importanceToken(channel.importance),
            )
        }

        private fun importanceToken(importance: Int): String {
            return when (importance) {
                NotificationManager.IMPORTANCE_NONE -> "none"
                NotificationManager.IMPORTANCE_MIN -> "min"
                NotificationManager.IMPORTANCE_LOW -> "low"
                NotificationManager.IMPORTANCE_DEFAULT -> "default"
                NotificationManager.IMPORTANCE_HIGH -> "high"
                NotificationManager.IMPORTANCE_MAX -> "max"
                else -> "unknown"
            }
        }

        private fun powerState(context: Context): PowerState {
            val powerManager = context.getSystemService(PowerManager::class.java)
                ?: return PowerState(
                    ignoringBatteryOptimizations = false,
                    powerSaveMode = false,
                    deviceIdleMode = false,
                )
            return PowerState(
                ignoringBatteryOptimizations = runCatching {
                    powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }.getOrDefault(false),
                powerSaveMode = runCatching { powerManager.isPowerSaveMode }.getOrDefault(false),
                deviceIdleMode = runCatching { powerManager.isDeviceIdleMode }.getOrDefault(false),
            )
        }
    }
}

private data class ChannelState(
    val blocked: Boolean,
    val importance: String,
)

private data class PowerState(
    val ignoringBatteryOptimizations: Boolean,
    val powerSaveMode: Boolean,
    val deviceIdleMode: Boolean,
)
