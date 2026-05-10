package com.geison.phonereminder.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.geison.phonereminder.MainActivity
import com.geison.phonereminder.R
import com.geison.phonereminder.data.MAX_NOTIFICATIONS_PER_DAY
import com.geison.phonereminder.data.MAX_NOTIFICATIONS_PER_WEEK
import com.geison.phonereminder.data.ReminderStorage
import com.geison.phonereminder.data.ScheduleSettings

object ReminderNotifier {
    private const val LESS_ACTION_REQUEST_CODE_BASE = 20_000
    private const val MORE_ACTION_REQUEST_CODE_BASE = 30_000

    fun showReminder(
        context: Context,
        notificationId: Int,
        reminderId: String,
        reminderText: String,
    ) {
        NotificationChannels.ensureCreated(context)
        val currentSchedule = ReminderStorage.load(context)
            .reminders
            .firstOrNull { it.id == reminderId }
            ?.schedule
            ?: ScheduleSettings()
        val lessSchedule = reduceDailyNotifications(currentSchedule)
        val moreSchedule = increaseWeeklyNotifications(currentSchedule)

        val openAppIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val seeLessIntent = reminderActionIntent(
            context = context,
            action = NotificationReceiver.ACTION_SEE_LESS_REMINDER,
            requestCode = LESS_ACTION_REQUEST_CODE_BASE + notificationId,
            notificationId = notificationId,
            reminderId = reminderId,
        )
        val seeMoreIntent = reminderActionIntent(
            context = context,
            action = NotificationReceiver.ACTION_SEE_MORE_REMINDER,
            requestCode = MORE_ACTION_REQUEST_CODE_BASE + notificationId,
            notificationId = notificationId,
            reminderId = reminderId,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title_reminder))
            .setContentText(reminderText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminderText))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .addAction(
                0,
                context.getString(
                    R.string.notification_action_see_less,
                    lessSchedule.notificationsPerWeek,
                    lessSchedule.notificationsPerDay,
                ),
                seeLessIntent,
            )
            .addAction(
                0,
                context.getString(
                    R.string.notification_action_see_more,
                    moreSchedule.notificationsPerWeek,
                    moreSchedule.notificationsPerDay,
                ),
                seeMoreIntent,
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun reminderActionIntent(
        context: Context,
        action: String,
        requestCode: Int,
        notificationId: Int,
        reminderId: String,
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationReceiver::class.java)
                .setAction(action)
                .putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(NotificationReceiver.EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
}
