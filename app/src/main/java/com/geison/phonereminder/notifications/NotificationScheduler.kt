package com.geison.phonereminder.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.geison.phonereminder.data.AppState
import com.geison.phonereminder.data.ReminderItem
import com.geison.phonereminder.data.ReminderStorage
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.min
import kotlin.random.Random

object NotificationScheduler {
    private const val REMINDER_REQUEST_CODE_BASE = 2_000
    private const val REFRESH_REQUEST_CODE = 9_000
    private const val MAX_NOTIFICATIONS_PER_REMINDER = 8
    private const val MAX_SCHEDULED_ALARMS = 256
    private val notificationWindowMillis = Duration.ofMinutes(10).toMillis()

    fun scheduleToday(context: Context) {
        NotificationChannels.ensureCreated(context)

        val appState = ReminderStorage.load(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        cancelReminderAlarms(context, alarmManager)
        scheduleTomorrowRefresh(context, alarmManager)

        val dayPlan = createDayPlan(
            state = appState,
            day = LocalDate.now(),
        )

        val now = LocalDateTime.now()
        dayPlan.filter { it.triggerAt.isAfter(now.plusMinutes(1)) }
            .forEachIndexed { index, plan ->
                val intent = Intent(context, NotificationReceiver::class.java)
                    .setAction(NotificationReceiver.ACTION_SHOW_REMINDER)
                    .putExtra(NotificationReceiver.EXTRA_REMINDER_TEXT, plan.reminder.text)
                    .putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, plan.notificationId)

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    REMINDER_REQUEST_CODE_BASE + index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    plan.triggerAt.toEpochMillis(),
                    notificationWindowMillis,
                    pendingIntent,
                )
            }
    }

    private fun cancelReminderAlarms(context: Context, alarmManager: AlarmManager) {
        repeat(MAX_SCHEDULED_ALARMS) { index ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REMINDER_REQUEST_CODE_BASE + index,
                Intent(context, NotificationReceiver::class.java)
                    .setAction(NotificationReceiver.ACTION_SHOW_REMINDER),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun scheduleTomorrowRefresh(context: Context, alarmManager: AlarmManager) {
        val nextRefresh = LocalDate.now()
            .plusDays(1)
            .atStartOfDay()
            .plusMinutes(5)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REFRESH_REQUEST_CODE,
            Intent(context, ScheduleRefreshReceiver::class.java)
                .setAction(ScheduleRefreshReceiver.ACTION_REFRESH_SCHEDULE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager.cancel(pendingIntent)
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            nextRefresh.toEpochMillis(),
            notificationWindowMillis,
            pendingIntent,
        )
    }

    private fun createDayPlan(state: AppState, day: LocalDate): List<ScheduledReminder> {
        val plans = state.reminders
            .filter { it.text.isNotBlank() }
            .flatMap { reminder -> createReminderPlan(reminder, day) }
            .sortedBy { it.triggerAt }

        return plans.take(MAX_SCHEDULED_ALARMS)
            .mapIndexed { index, scheduledReminder ->
                scheduledReminder.copy(notificationId = 5_000 + index)
            }
    }

    private fun createReminderPlan(
        reminder: ReminderItem,
        day: LocalDate,
    ): List<ScheduledReminder> {
        val settings = reminder.schedule
        val start = LocalDateTime.of(day, LocalTime.of(settings.startHour, 0))
        val end = LocalDateTime.of(day, LocalTime.of(settings.endHour, 0))
        if (!start.isBefore(end)) {
            return emptyList()
        }

        val startMinute = settings.startHour * 60
        val endMinuteExclusive = settings.endHour * 60
        val availableMinutes = (startMinute until endMinuteExclusive).toMutableList()
        if (availableMinutes.isEmpty()) {
            return emptyList()
        }

        val notificationsPerWeek = settings.notificationsPerWeek.coerceIn(1, 7)
        val weekStart = day.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val dayIndex = (day.toEpochDay() - weekStart.toEpochDay()).toInt()
        val scheduledDays = pickScheduledDays(weekStart, reminder, notificationsPerWeek)
        if (dayIndex !in scheduledDays) {
            return emptyList()
        }

        val minuteSeed = buildDaySeed(day, reminder)
        availableMinutes.shuffle(Random(minuteSeed.toInt()))
        val minuteOfDay = availableMinutes.first()

        return listOf(
            ScheduledReminder(
                notificationId = 0,
                reminder = reminder,
                triggerAt = day.atStartOfDay().plusMinutes(minuteOfDay.toLong()),
            ),
        )
    }

    private fun pickScheduledDays(
        weekStart: LocalDate,
        reminder: ReminderItem,
        notificationsPerWeek: Int,
    ): Set<Int> {
        val days = (0..6).toMutableList()
        val weekSeed = buildWeekSeed(weekStart, reminder)
        days.shuffle(Random(weekSeed.toInt()))
        return days.take(notificationsPerWeek).toSet()
    }

    private fun buildWeekSeed(
        weekStart: LocalDate,
        reminder: ReminderItem,
    ): Long {
        val reminderHash = "${reminder.id}:${reminder.text}:${reminder.schedule}".hashCode()
        return weekStart.toEpochDay() xor reminderHash.toLong()
    }

    private fun buildDaySeed(
        day: LocalDate,
        reminder: ReminderItem,
    ): Long {
        return buildWeekSeed(day.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)), reminder) xor
            day.dayOfWeek.value.toLong()
    }

    private fun LocalDateTime.toEpochMillis(): Long {
        return atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}

data class ScheduledReminder(
    val notificationId: Int,
    val reminder: ReminderItem,
    val triggerAt: LocalDateTime,
)
