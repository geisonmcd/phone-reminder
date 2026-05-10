package com.geison.phonereminder.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.geison.phonereminder.data.MAX_NOTIFICATIONS_PER_DAY
import com.geison.phonereminder.data.MAX_NOTIFICATIONS_PER_WEEK
import com.geison.phonereminder.data.ReminderStorage
import com.geison.phonereminder.data.ScheduleSettings

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SEE_LESS_REMINDER -> {
                adjustReminderSchedule(
                    context = context,
                    intent = intent,
                    transform = ::reduceDailyNotifications,
                )
                return
            }

            ACTION_SEE_MORE_REMINDER -> {
                adjustReminderSchedule(
                    context = context,
                    intent = intent,
                    transform = ::increaseWeeklyNotifications,
                )
                return
            }
        }

        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
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

    private fun adjustReminderSchedule(
        context: Context,
        intent: Intent,
        transform: (ScheduleSettings) -> ScheduleSettings,
    ) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val state = ReminderStorage.load(context)
        val updatedReminders = state.reminders.map { reminder ->
            if (reminder.id == reminderId) {
                reminder.copy(schedule = transform(reminder.schedule))
            } else {
                reminder
            }
        }

        ReminderStorage.save(context, state.copy(reminders = updatedReminders))
        NotificationManagerCompat.from(context).cancel(notificationId)
        NotificationScheduler.scheduleToday(context)
    }

    private fun reduceDailyNotifications(schedule: ScheduleSettings): ScheduleSettings {
        val notificationsPerDay = (schedule.notificationsPerDay / 2)
            .coerceAtLeast(1)
            .coerceAtMost(MAX_NOTIFICATIONS_PER_DAY)
        return schedule.copy(
            notificationsPerDay = notificationsPerDay,
            notificationsPerWeek = snapWeeklyCount(
                value = schedule.notificationsPerWeek,
                notificationsPerDay = notificationsPerDay,
            ),
        )
    }

    private fun increaseWeeklyNotifications(schedule: ScheduleSettings): ScheduleSettings {
        val notificationsPerDay = schedule.notificationsPerDay.coerceIn(1, MAX_NOTIFICATIONS_PER_DAY)
        return schedule.copy(
            notificationsPerDay = notificationsPerDay,
            notificationsPerWeek = snapWeeklyCount(
                value = schedule.notificationsPerWeek * 2,
                notificationsPerDay = notificationsPerDay,
            ),
        )
    }

    private fun snapWeeklyCount(
        value: Int,
        notificationsPerDay: Int,
    ): Int {
        val minValue = notificationsPerDay
        val maxValue = minOf(MAX_NOTIFICATIONS_PER_WEEK, notificationsPerDay * 7)
        val coerced = value.coerceIn(minValue, maxValue)
        val remainder = coerced % notificationsPerDay
        return if (remainder == 0) {
            coerced
        } else {
            (coerced + notificationsPerDay - remainder).coerceAtMost(maxValue)
        }
    }

    companion object {
        const val ACTION_SHOW_REMINDER = "com.geison.phonereminder.action.SHOW_REMINDER"
        const val ACTION_SEE_LESS_REMINDER = "com.geison.phonereminder.action.SEE_LESS_REMINDER"
        const val ACTION_SEE_MORE_REMINDER = "com.geison.phonereminder.action.SEE_MORE_REMINDER"
        const val EXTRA_REMINDER_TEXT = "extra_reminder_text"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}
