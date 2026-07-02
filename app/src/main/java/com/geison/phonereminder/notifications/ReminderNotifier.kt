package com.geison.phonereminder.notifications

import android.Manifest
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
import com.geison.phonereminder.data.ReminderStorage
import com.geison.phonereminder.data.ScheduleCadence
import com.geison.phonereminder.data.ScheduleSettings
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

        val schedule = ReminderStorage.load(context).reminders
            .firstOrNull { it.id == reminderId }
            ?.schedule
        val lessOftenSchedule = schedule?.let { adjustScheduleFrequency(it, FrequencyAdjustment.LESS_OFTEN) }
        val moreOftenSchedule = schedule?.let { adjustScheduleFrequency(it, FrequencyAdjustment.MORE_OFTEN) }

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title_reminder))
            .setContentText(reminderText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminderText))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.ic_notification,
                lessOftenSchedule?.let { adjustedSchedule ->
                    context.getString(
                        R.string.notification_action_schedule_less,
                        adjustedSchedule.notificationActionLabel(context),
                    )
                } ?: context.getString(R.string.notification_action_less_often),
                frequencyActionIntent(
                    context = context,
                    requestCode = notificationId xor LESS_OFTEN_REQUEST_CODE_MASK,
                    action = NotificationReceiver.ACTION_DECREASE_FREQUENCY,
                    reminderId = reminderId,
                    notificationId = notificationId,
                ),
            )
            .addAction(
                R.drawable.ic_notification,
                moreOftenSchedule?.let { adjustedSchedule ->
                    context.getString(
                        R.string.notification_action_schedule_more,
                        adjustedSchedule.notificationActionLabel(context),
                    )
                } ?: context.getString(R.string.notification_action_more_often),
                frequencyActionIntent(
                    context = context,
                    requestCode = notificationId xor MORE_OFTEN_REQUEST_CODE_MASK,
                    action = NotificationReceiver.ACTION_INCREASE_FREQUENCY,
                    reminderId = reminderId,
                    notificationId = notificationId,
                ),
            )
            .build()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Diagnostics.log("show_reminder_notification_skipped_permission")
            return
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
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

    private fun frequencyActionIntent(
        context: Context,
        requestCode: Int,
        action: String,
        reminderId: String,
        notificationId: Int,
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationReceiver::class.java).apply {
                setAction(action)
                putExtra(NotificationReceiver.EXTRA_REMINDER_ID, reminderId)
                putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val LESS_OFTEN_REQUEST_CODE_MASK = 0x4A71
    private const val MORE_OFTEN_REQUEST_CODE_MASK = 0x6B92
}

private fun ScheduleSettings.notificationActionLabel(context: Context): String {
    if (cadence == ScheduleCadence.PAUSED) {
        return context.getString(R.string.notification_schedule_paused)
    }

    return when (cadence) {
        ScheduleCadence.DAILY -> {
            if (notificationsPerDay == 1) {
                context.getString(R.string.notification_schedule_daily)
            } else {
                context.getString(R.string.notification_schedule_daily_count, notificationsPerDay)
            }
        }
        ScheduleCadence.FIVE_TIMES_PER_WEEK -> context.getString(R.string.notification_schedule_five_weekly)
        ScheduleCadence.THREE_TIMES_PER_WEEK -> context.getString(R.string.notification_schedule_three_weekly)
        ScheduleCadence.WEEKLY -> context.getString(R.string.notification_schedule_weekly)
        ScheduleCadence.TWICE_MONTHLY -> context.getString(R.string.notification_schedule_twice_monthly)
        ScheduleCadence.MONTHLY -> context.getString(R.string.notification_schedule_monthly)
        ScheduleCadence.PAUSED -> context.getString(R.string.notification_schedule_paused)
    }
}
