package com.geison.phonereminder.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class ReminderImportMergeTest {
    @Test
    fun mergeAppendsImportedRemindersAndKeepsCurrentSettings() {
        val currentState = AppState(
            reminders = listOf(
                ReminderItem(
                    id = "current",
                    text = "Existing reminder",
                ),
            ),
            notificationWindow = NotificationWindowSettings(
                startHour = 7,
                endHour = 18,
            ),
            reminderDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        )
        val importedState = AppState(
            reminders = listOf(
                ReminderItem(
                    id = "imported",
                    text = "Imported reminder",
                ),
            ),
            notificationWindow = NotificationWindowSettings(
                startHour = 10,
                endHour = 22,
            ),
            reminderDays = setOf(DayOfWeek.FRIDAY),
        )

        val merged = ReminderImportMerge.merge(
            currentState = currentState,
            importedState = importedState,
        )

        assertEquals(
            listOf("Existing reminder", "Imported reminder"),
            merged.reminders.map { it.text },
        )
        assertEquals(currentState.notificationWindow, merged.notificationWindow)
        assertEquals(currentState.reminderDays, merged.reminderDays)
    }
}
