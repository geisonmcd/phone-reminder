package com.geison.phonereminder.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.geison.phonereminder.diagnostics.Diagnostics
import com.geison.phonereminder.data.AppState
import com.geison.phonereminder.data.MAX_NOTIFICATIONS_PER_DAY
import com.geison.phonereminder.data.ReminderItem
import com.geison.phonereminder.data.ReminderStorage
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

object NotificationScheduler {
    private const val REMINDER_REQUEST_CODE_BASE = 2_000
    private const val REFRESH_REQUEST_CODE = 9_000
    private const val SCHEDULE_HORIZON_DAYS = 7
    private const val MAX_SCHEDULED_ALARMS = 450
    private const val LEGACY_ALARM_CANCEL_COUNT = 2_048
    private val notificationWindowMillis = Duration.ofMinutes(10).toMillis()

    fun scheduleToday(context: Context) {
        NotificationChannels.ensureCreated(context)

        val appState = ReminderStorage.load(context)
        Diagnostics.log("schedule_today")
        Diagnostics.setKey("reminder_count", appState.reminders.size)
        Diagnostics.setKey("reminder_days_count", appState.reminderDays.size)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        cancelReminderAlarms(context, alarmManager)
        scheduleTomorrowRefresh(context, alarmManager)

        val dayPlan = createSchedulePlan(
            state = appState,
            startDay = LocalDate.now(),
            totalDays = SCHEDULE_HORIZON_DAYS,
        )

        val now = LocalDateTime.now()
        val upcomingPlans = dayPlan.filter { it.triggerAt.isAfter(now.plusMinutes(1)) }
        Diagnostics.setKey("scheduled_alarm_count", upcomingPlans.size)
        upcomingPlans.forEachIndexed { index, plan ->
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

            runCatching {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    plan.triggerAt.toEpochMillis(),
                    notificationWindowMillis,
                    pendingIntent,
                )
            }.onFailure { error ->
                Diagnostics.recordNonFatal(
                    area = "schedule_alarm_failed",
                    throwable = error,
                    keys = mapOf(
                        "scheduled_alarm_index" to index.toString(),
                        "planned_alarm_count" to upcomingPlans.size.toString(),
                    ),
                )
                return@forEachIndexed
            }
        }
    }

    private fun cancelReminderAlarms(context: Context, alarmManager: AlarmManager) {
        repeat(LEGACY_ALARM_CANCEL_COUNT) { index ->
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
        random: Random = Random.Default,
    ): List<ScheduledReminder> {
        val plans = (0 until totalDays.coerceAtLeast(0))
            .asSequence()
            .map { offset -> startDay.plusDays(offset.toLong()) }
            .flatMap { day -> createDayPlan(state, day, random).asSequence() }
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
        random: Random,
    ): List<ScheduledReminder> {
        if (day.dayOfWeek !in state.reminderDays) {
            return emptyList()
        }

        val occurrences = state.reminders
            .asSequence()
            .filter { it.text.isNotBlank() }
            .flatMap { reminder -> buildOccurrencesForDay(reminder, state, day).asSequence() }
            .shuffled(random)
            .toList()

        if (occurrences.isEmpty()) {
            return emptyList()
        }

        val scheduledMinutes = mutableListOf<Int>()
        return occurrences.mapNotNull { occurrence ->
            val candidateMinutes = (occurrence.startMinute until occurrence.endMinuteExclusive)
                .filterNot(scheduledMinutes::contains)
            if (candidateMinutes.isEmpty()) {
                return@mapNotNull null
            }

            val minute = candidateMinutes.random(random)
            scheduledMinutes += minute
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

        val remindersToday = settings.notificationsPerDay.coerceIn(1, MAX_NOTIFICATIONS_PER_DAY)

        return List(remindersToday) {
            ReminderOccurrence(
                reminder = reminder,
                startMinute = startMinute,
                endMinuteExclusive = endMinuteExclusive,
            )
        }
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
)
