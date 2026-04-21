package com.geison.phonereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderExchangeTest {
    @Test
    fun exportAndImportRoundTripPreservesReminderContent() {
        val exported = ReminderExchange.export(
            AppState(
                notificationWindow = NotificationWindowSettings(
                    startHour = 8,
                    endHour = 19,
                ),
                reminders = listOf(
                    ReminderItem(
                        id = "first",
                        text = "Protect your attention.",
                        schedule = ScheduleSettings(
                            notificationsPerWeek = 4,
                            notificationsPerDay = 1,
                        ),
                    ),
                    ReminderItem(
                        id = "second",
                        text = "Slow down before reacting.\nTake one breath first.",
                        schedule = ScheduleSettings(
                            notificationsPerWeek = 4,
                            notificationsPerDay = 2,
                        ),
                    ),
                ),
            ),
        )

        val imported = ReminderExchange.import(exported)

        assertEquals(2, imported.reminders.size)
        assertEquals(8, imported.notificationWindow.startHour)
        assertEquals(19, imported.notificationWindow.endHour)
        assertEquals("Protect your attention.", imported.reminders[0].text)
        assertEquals(4, imported.reminders[0].schedule.notificationsPerWeek)
        assertEquals(1, imported.reminders[0].schedule.notificationsPerDay)
        assertEquals("Slow down before reacting.\nTake one breath first.", imported.reminders[1].text)
        assertEquals(4, imported.reminders[1].schedule.notificationsPerWeek)
        assertEquals(2, imported.reminders[1].schedule.notificationsPerDay)
        assertTrue(imported.reminders.all { it.id.isNotBlank() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun importRejectsFilesWithWrongHeader() {
        ReminderExchange.import(
            """
            Not a valid export

            Default start hour: 9
            Default end hour: 20

            ---
            Reminder:
            Protect your attention.
            End reminder
            Notifications per week: 3
            Notifications per day: 1
            """.trimIndent(),
        )
    }

    @Test
    fun importSupportsUpToFiftyNotificationsPerDay() {
        val imported = ReminderExchange.import(
            """
            Smart Random Reminder Export v1

            Default start hour: 9
            Default end hour: 20

            ---
            Reminder:
            Deep work.
            End reminder
            Notifications per week: 350
            Notifications per day: 50
            """.trimIndent(),
        )

        assertEquals(50, imported.reminders.single().schedule.notificationsPerDay)
        assertEquals(350, imported.reminders.single().schedule.notificationsPerWeek)
    }
}
