package com.geison.phonereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderExchangeTest {
    @Test
    fun exportAndImportRoundTripPreservesReminderContent() {
        val exported = ReminderExchange.export(
            AppState(
                reminders = listOf(
                    ReminderItem(
                        id = "first",
                        text = "Protect your attention.",
                        schedule = ScheduleSettings(
                            notificationsPerWeek = 3,
                            startHour = 9,
                            endHour = 20,
                        ),
                    ),
                    ReminderItem(
                        id = "second",
                        text = "Slow down before reacting.\nTake one breath first.",
                        schedule = ScheduleSettings(
                            notificationsPerWeek = 1,
                            startHour = 7,
                            endHour = 11,
                        ),
                    ),
                ),
            ),
        )

        val imported = ReminderExchange.import(exported)

        assertEquals(2, imported.size)
        assertEquals("Protect your attention.", imported[0].text)
        assertEquals(3, imported[0].schedule.notificationsPerWeek)
        assertEquals(9, imported[0].schedule.startHour)
        assertEquals(20, imported[0].schedule.endHour)
        assertEquals("Slow down before reacting.\nTake one breath first.", imported[1].text)
        assertEquals(1, imported[1].schedule.notificationsPerWeek)
        assertEquals(7, imported[1].schedule.startHour)
        assertEquals(11, imported[1].schedule.endHour)
        assertTrue(imported.all { it.id.isNotBlank() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun importRejectsFilesWithWrongHeader() {
        ReminderExchange.import(
            """
            Not a valid export

            ---
            Reminder:
            Protect your attention.
            End reminder
            Notifications per week: 3
            Start hour: 9
            End hour: 20
            """.trimIndent(),
        )
    }
}
