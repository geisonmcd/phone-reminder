package com.geison.phonereminder.notifications

import com.geison.phonereminder.data.AppState
import com.geison.phonereminder.data.NotificationWindowSettings
import com.geison.phonereminder.data.ReminderItem
import com.geison.phonereminder.data.ScheduleSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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
                        notificationsPerWeek = 7,
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

    private fun reminder(id: String): ReminderItem {
        return ReminderItem(
            id = id,
            text = "Reminder $id",
            schedule = ScheduleSettings(
                notificationsPerWeek = 7,
                notificationsPerDay = 1,
            ),
        )
    }
}
