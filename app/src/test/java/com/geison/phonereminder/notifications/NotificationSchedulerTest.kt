package com.geison.phonereminder.notifications

import com.geison.phonereminder.data.AppState
import com.geison.phonereminder.data.NotificationWindowSettings
import com.geison.phonereminder.data.ReminderItem
import com.geison.phonereminder.data.ScheduleCadence
import com.geison.phonereminder.data.ScheduleSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

class NotificationSchedulerTest {
    @Test
    fun createSchedulePlanQueuesFutureDays() {
        val startDay = LocalDate.of(2026, 4, 13) // Monday
        val state = AppState(
            notificationWindow = NotificationWindowSettings(
                startHour = 9,
                endHour = 10,
            ),
            reminders = listOf(
                ReminderItem(
                    id = "daily",
                    text = "Stay focused.",
                    schedule = ScheduleSettings(
                        notificationsPerDay = 1,
                    ),
                ),
            ),
        )

        val plan = NotificationScheduler.createSchedulePlan(
            state = state,
            startDay = startDay,
            totalDays = 7,
        )

        assertEquals(7, plan.size)
        assertEquals(
            (0L..6L).map { startDay.plusDays(it) },
            plan.map { it.triggerAt.toLocalDate() },
        )
    }

    @Test
    fun createSchedulePlanAssignsDifferentTimesAcrossRemindersOnSameDay() {
        val startDay = LocalDate.of(2026, 4, 13) // Monday
        val state = AppState(
            notificationWindow = NotificationWindowSettings(
                startHour = 9,
                endHour = 10,
            ),
            reminders = listOf(
                reminder("first"),
                reminder("second"),
                reminder("third"),
            ),
        )

        val plan = NotificationScheduler.createSchedulePlan(
            state = state,
            startDay = startDay,
            totalDays = 1,
        )

        assertEquals(3, plan.size)
        assertEquals(3, plan.map { it.triggerAt.toLocalTime() }.distinct().size)
    }

    @Test
    fun createSchedulePlanAssignsRandomTimesInsideNotificationWindow() {
        val startDay = LocalDate.of(2026, 4, 13) // Monday
        val state = AppState(
            notificationWindow = NotificationWindowSettings(
                startHour = 9,
                endHour = 21,
            ),
            reminders = (1..6).map { index -> reminder("reminder-$index") },
        )

        val plan = NotificationScheduler.createSchedulePlan(
            state = state,
            startDay = startDay,
            totalDays = 1,
            random = Random(1),
        )

        assertEquals(6, plan.size)
        val scheduledMinutes = plan
            .map { it.triggerAt.hour * 60 + it.triggerAt.minute }
            .sorted()
        assertEquals(6, scheduledMinutes.distinct().size)
        scheduledMinutes.forEach { minute ->
            assert(minute in (9 * 60) until (21 * 60))
        }
    }

    @Test
    fun createSchedulePlanSupportsFiftyNotificationsPerDay() {
        val startDay = LocalDate.of(2026, 4, 13) // Monday
        val state = AppState(
            notificationWindow = NotificationWindowSettings(
                startHour = 9,
                endHour = 10,
            ),
            reminders = listOf(
                ReminderItem(
                    id = "intense",
                    text = "Stay on task.",
                    schedule = ScheduleSettings(
                        notificationsPerDay = 50,
                    ),
                ),
            ),
        )

        val plan = NotificationScheduler.createSchedulePlan(
            state = state,
            startDay = startDay,
            totalDays = 7,
        )

        assertEquals(350, plan.size)
    }

    @Test
    fun createSchedulePlanUsesDailyCountOnEachConfiguredDay() {
        val startDay = LocalDate.of(2026, 4, 13) // Monday
        val state = AppState(
            reminderDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            notificationWindow = NotificationWindowSettings(
                startHour = 9,
                endHour = 21,
            ),
            reminders = listOf(
                ReminderItem(
                    id = "independent-counts",
                    text = "Stay aware.",
                    schedule = ScheduleSettings(
                        notificationsPerDay = 3,
                    ),
                ),
            ),
        )

        val plan = NotificationScheduler.createSchedulePlan(
            state = state,
            startDay = startDay,
            totalDays = 7,
        )

        assertEquals(6, plan.size)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            plan.map { it.triggerAt.dayOfWeek }.toSet(),
        )
        assertEquals(
            listOf(3, 3),
            plan.groupingBy { it.triggerAt.toLocalDate() }.eachCount().values.sorted(),
        )
    }

    @Test
    fun createSchedulePlanOnlyUsesConfiguredReminderDays() {
        val startDay = LocalDate.of(2026, 4, 13) // Monday
        val state = AppState(
            reminderDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            notificationWindow = NotificationWindowSettings(
                startHour = 9,
                endHour = 10,
            ),
            reminders = listOf(
                ReminderItem(
                    id = "limited-days",
                    text = "Stay focused.",
                    schedule = ScheduleSettings(
                        notificationsPerDay = 1,
                    ),
                ),
            ),
        )

        val plan = NotificationScheduler.createSchedulePlan(
            state = state,
            startDay = startDay,
            totalDays = 7,
        )

        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            plan.map { it.triggerAt.dayOfWeek }.toSet(),
        )
    }

    @Test
    fun createSchedulePlanUsesReminderDayOverrideInsteadOfGlobalDays() {
        val startDay = LocalDate.of(2026, 4, 13) // Monday
        val state = AppState(
            reminderDays = setOf(DayOfWeek.MONDAY),
            notificationWindow = NotificationWindowSettings(
                startHour = 9,
                endHour = 10,
            ),
            reminders = listOf(
                ReminderItem(
                    id = "custom-days",
                    text = "Use custom days.",
                    schedule = ScheduleSettings(
                        notificationsPerDay = 1,
                        reminderDays = setOf(DayOfWeek.WEDNESDAY),
                    ),
                ),
            ),
        )

        val plan = NotificationScheduler.createSchedulePlan(
            state = state,
            startDay = startDay,
            totalDays = 7,
        )

        assertEquals(1, plan.size)
        assertEquals(DayOfWeek.WEDNESDAY, plan.single().triggerAt.dayOfWeek)
    }

    @Test
    fun createSchedulePlanSupportsThreeTimesPerWeekCadence() {
        val startDay = LocalDate.of(2026, 4, 13) // Monday
        val state = AppState(
            notificationWindow = NotificationWindowSettings(
                startHour = 9,
                endHour = 21,
            ),
            reminders = listOf(
                ReminderItem(
                    id = "three-weekly",
                    text = "Show a few times each week.",
                    schedule = ScheduleSettings(
                        notificationsPerDay = 1,
                        cadence = ScheduleCadence.THREE_TIMES_PER_WEEK,
                    ),
                ),
            ),
        )

        val plan = NotificationScheduler.createSchedulePlan(
            state = state,
            startDay = startDay,
            totalDays = 7,
        )

        assertEquals(3, plan.size)
        assertEquals(3, plan.map { it.triggerAt.toLocalDate() }.distinct().size)
    }

    @Test
    fun createSchedulePlanSupportsTwiceMonthlyCadence() {
        val startDay = LocalDate.of(2026, 4, 1)
        val state = AppState(
            notificationWindow = NotificationWindowSettings(
                startHour = 9,
                endHour = 21,
            ),
            reminders = listOf(
                ReminderItem(
                    id = "twice-monthly",
                    text = "Show twice this month.",
                    schedule = ScheduleSettings(
                        notificationsPerDay = 1,
                        cadence = ScheduleCadence.TWICE_MONTHLY,
                    ),
                ),
            ),
        )

        val plan = NotificationScheduler.createSchedulePlan(
            state = state,
            startDay = startDay,
            totalDays = 30,
        )

        assertEquals(2, plan.size)
        assertEquals(setOf(4), plan.map { it.triggerAt.monthValue }.toSet())
    }

    @Test
    fun createSchedulePlanSkipsPausedReminders() {
        val startDay = LocalDate.of(2026, 4, 13) // Monday
        val state = AppState(
            reminders = listOf(
                ReminderItem(
                    id = "paused",
                    text = "Do not show.",
                    schedule = ScheduleSettings(
                        cadence = ScheduleCadence.PAUSED,
                    ),
                ),
            ),
        )

        val plan = NotificationScheduler.createSchedulePlan(
            state = state,
            startDay = startDay,
            totalDays = 30,
        )

        assertEquals(0, plan.size)
    }

    private fun reminder(id: String): ReminderItem {
        return ReminderItem(
            id = id,
            text = "Reminder $id",
            schedule = ScheduleSettings(
                notificationsPerDay = 1,
            ),
        )
    }
}
