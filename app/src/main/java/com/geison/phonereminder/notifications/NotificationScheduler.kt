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
    private const val MAX_NOTIFICATIONS_PER_DAY = 5
    private const val MAX_NOTIFICATIONS_PER_WEEK = MAX_NOTIFICATIONS_PER_DAY * 7
    private const val SCHEDULE_HORIZON_DAYS = 7
    private const val MAX_SCHEDULED_ALARMS = 256
    private val notificationWindowMillis = Duration.ofMinutes(10).toMillis()

    fun scheduleToday(context: Context) {
        NotificationChannels.ensureCreated(context)

        val appState = ReminderStorage.load(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        cancelReminderAlarms(context, alarmManager)
        scheduleTomorrowRefresh(context, alarmManager)

        val dayPlan = createSchedulePlan(
            state = appState,
            startDay = LocalDate.now(),
            totalDays = SCHEDULE_HORIZON_DAYS,
        )

        val now = LocalDateTime.now()
        dayPlan.filter { it.triggerAt.isAfter(now.plusMinutes(1)) }
            .forEachIndexed { index, plan ->
                val intent = Intent(context, NotificationReceiver::class.java)
                    .setAction(NotificationReceiver.ACTION_SHOW_REMINDER)
                    .putExtra(NotificationReceiver.EXTRA_REMINDER_ID, plan.reminder.id)
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

    internal fun createSchedulePlan(
        state: AppState,
        startDay: LocalDate,
        totalDays: Int = SCHEDULE_HORIZON_DAYS,
    ): List<ScheduledReminder> {
        val plans = (0 until totalDays.coerceAtLeast(0))
            .asSequence()
            .map { offset -> startDay.plusDays(offset.toLong()) }
            .flatMap { day -> createDayPlan(state, day).asSequence() }
            .sortedBy { it.triggerAt }
            .toList()

        return plans.take(MAX_SCHEDULED_ALARMS)
            .mapIndexed { index, scheduledReminder ->
                scheduledReminder.copy(notificationId = 5_000 + index)
            }
    }

    private fun createDayPlan(
        state: AppState,
        day: LocalDate,
    ): List<ScheduledReminder> {
        val occurrences = state.reminders
            .asSequence()
            .filter { it.text.isNotBlank() }
            .flatMap { reminder -> buildOccurrencesForDay(reminder, state, day).asSequence() }
            .sortedWith(
                compareBy<ReminderOccurrence> { it.endMinuteExclusive - it.startMinute }
                    .thenBy { it.orderSeed },
            )
            .toList()

        if (occurrences.isEmpty()) {
            return emptyList()
        }

        val occupiedMinutes = mutableSetOf<Int>()
        return occurrences.mapNotNull { occurrence ->
            val candidateMinutes = (occurrence.startMinute until occurrence.endMinuteExclusive)
                .filterNot(occupiedMinutes::contains)
            if (candidateMinutes.isEmpty()) {
                return@mapNotNull null
            }

            val minute = candidateMinutes.random(Random(occurrence.orderSeed))
            occupiedMinutes += minute
            ScheduledReminder(
                notificationId = 0,
                reminder = occurrence.reminder,
                triggerAt = day.atStartOfDay().plusMinutes(minute.toLong()),
            )
        }
    }

    private fun buildOccurrencesForDay(
        reminder: ReminderItem,
        state: AppState,
        day: LocalDate,
    ): List<ReminderOccurrence> {
        val settings = reminder.schedule
        val window = state.notificationWindow
        val start = LocalDateTime.of(day, LocalTime.of(window.startHour, 0))
        val end = LocalDateTime.of(day, LocalTime.of(window.endHour, 0))
        if (!start.isBefore(end)) {
            return emptyList()
        }

        val startMinute = window.startHour * 60
        val endMinuteExclusive = window.endHour * 60
        if (startMinute >= endMinuteExclusive) {
            return emptyList()
        }

        val weekStart = day.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val dayIndex = (day.toEpochDay() - weekStart.toEpochDay()).toInt()
        val notificationsPerDay = settings.notificationsPerDay.coerceIn(1, MAX_NOTIFICATIONS_PER_DAY)
        val notificationsPerWeek = settings.notificationsPerWeek.coerceIn(
            notificationsPerDay,
            min(MAX_NOTIFICATIONS_PER_WEEK, notificationsPerDay * 7),
        )
        val remindersToday = countScheduledOccurrencesForDay(
            weekStart = weekStart,
            reminder = reminder,
            notificationsPerWeek = notificationsPerWeek,
            notificationsPerDay = notificationsPerDay,
            dayIndex = dayIndex,
        )

        return List(remindersToday) { occurrenceIndex ->
            ReminderOccurrence(
                reminder = reminder,
                startMinute = startMinute,
                endMinuteExclusive = endMinuteExclusive,
                orderSeed = buildOccurrenceSeed(day, reminder, occurrenceIndex),
            )
        }
    }

    private fun countScheduledOccurrencesForDay(
        weekStart: LocalDate,
        reminder: ReminderItem,
        notificationsPerWeek: Int,
        notificationsPerDay: Int,
        dayIndex: Int,
    ): Int {
        val activeDays = (notificationsPerWeek / notificationsPerDay).coerceIn(1, 7)
        val slots = (0 until 7).toMutableList()
        val weekSeed = buildWeekSeed(weekStart, reminder)
        slots.shuffle(Random(weekSeed.toInt()))
        return if (dayIndex in slots.take(activeDays)) notificationsPerDay else 0
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

    private fun buildOccurrenceSeed(
        day: LocalDate,
        reminder: ReminderItem,
        occurrenceIndex: Int,
    ): Long {
        return buildDaySeed(day, reminder) xor (occurrenceIndex.toLong() shl 16)
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

private data class ReminderOccurrence(
    val reminder: ReminderItem,
    val startMinute: Int,
    val endMinuteExclusive: Int,
    val orderSeed: Long,
)
