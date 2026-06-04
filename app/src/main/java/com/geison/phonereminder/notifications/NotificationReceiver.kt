package com.geison.phonereminder.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.geison.phonereminder.data.MAX_NOTIFICATIONS_PER_DAY
import com.geison.phonereminder.data.ReminderStorage
import com.geison.phonereminder.data.ScheduleSettings
import com.geison.phonereminder.diagnostics.Diagnostics

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Diagnostics.log("notification_receiver_on_receive")

        when (intent.action) {
            ACTION_DECREASE_FREQUENCY -> {
                updateReminderFrequency(context, intent, FrequencyAdjustment.LESS_OFTEN)
                return
            }
            ACTION_INCREASE_FREQUENCY -> {
                updateReminderFrequency(context, intent, FrequencyAdjustment.MORE_OFTEN)
                return
            }
        }

        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Diagnostics.setKey("notifications_permission_granted", false)
            return
        }

        val reminderText = intent.getStringExtra(EXTRA_REMINDER_TEXT) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return

        ReminderNotifier.showReminder(
            context = context,
            notificationId = notificationId,
            reminderId = reminderId,
            reminderText = reminderText,
        )
    }

    private fun updateReminderFrequency(
        context: Context,
        intent: Intent,
        adjustment: FrequencyAdjustment,
    ) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val state = ReminderStorage.load(context)
        val updatedReminders = state.reminders.map { reminder ->
            if (reminder.id == reminderId) {
                reminder.copy(schedule = adjustScheduleFrequency(reminder.schedule, adjustment))
            } else {
                reminder
            }
        }
        if (updatedReminders == state.reminders) {
            return
        }

        ReminderStorage.save(context, state.copy(reminders = updatedReminders))
        Diagnostics.log("notification_frequency_adjusted")
        Diagnostics.setKey("notification_frequency_adjustment", adjustment.name.lowercase())
        NotificationManagerCompat.from(context).cancel(notificationId)
        NotificationScheduler.scheduleToday(context)
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.geison.phonereminder.action.SHOW_REMINDER"
        const val ACTION_DECREASE_FREQUENCY = "com.geison.phonereminder.action.DECREASE_FREQUENCY"
        const val ACTION_INCREASE_FREQUENCY = "com.geison.phonereminder.action.INCREASE_FREQUENCY"
        const val EXTRA_REMINDER_TEXT = "extra_reminder_text"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}

internal enum class FrequencyAdjustment {
    LESS_OFTEN,
    MORE_OFTEN,
}

internal fun adjustScheduleFrequency(
    schedule: ScheduleSettings,
    adjustment: FrequencyAdjustment,
): ScheduleSettings {
    val notificationsPerDay = schedule.notificationsPerDay.coerceIn(1, MAX_NOTIFICATIONS_PER_DAY)
    val currentNotificationsPerWeek = schedule.notificationsPerWeek.coerceIn(
        notificationsPerDay,
        notificationsPerDay * 7,
    )
    val adjustedNotificationsPerWeek = when (adjustment) {
        FrequencyAdjustment.LESS_OFTEN -> currentNotificationsPerWeek - notificationsPerDay
        FrequencyAdjustment.MORE_OFTEN -> currentNotificationsPerWeek + notificationsPerDay
    }.coerceIn(notificationsPerDay, notificationsPerDay * 7)

    return schedule.copy(
        notificationsPerWeek = adjustedNotificationsPerWeek,
        notificationsPerDay = notificationsPerDay,
    )
}
