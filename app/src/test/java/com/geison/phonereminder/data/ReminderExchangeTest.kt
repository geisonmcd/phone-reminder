package com.geison.phonereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ReminderExchangeTest {
    @Test
    fun exportFormatV1IsStableContract() {
        val exported = ReminderExchange.export(
            AppState(
                reminderDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.MONDAY),
                notificationWindow = NotificationWindowSettings(
                    startHour = 8,
                    endHour = 21,
                ),
                reminders = listOf(
                    ReminderItem(
                        id = "ignored-id",
                        text = "First reminder",
                        schedule = ScheduleSettings(notificationsPerDay = 1),
                    ),
                    ReminderItem(
                        id = "ignored-id-too",
                        text = "Second reminder\nwith two lines",
                        schedule = ScheduleSettings(notificationsPerDay = 2),
                    ),
                ),
            ),
        )

        assertEquals(
            """
            Smart Random Reminder Export v1

            This file can be imported back into Smart Random Reminder.
            Keep each block in the same format when editing by hand.

            Default start hour: 8
            Default end hour: 21
            Reminder days: MONDAY,TUESDAY

            ---
            Reminder:
            First reminder
            End reminder
            Notifications per day: 1

            ---
            Reminder:
            Second reminder
            with two lines
            End reminder
            Notifications per day: 2
            """.trimIndent() + "\n",
            exported,
        )
    }

    @Test
    fun exportAndImportRoundTripPreservesReminderContent() {
        val exported = ReminderExchange.export(
            AppState(
                reminderDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                notificationWindow = NotificationWindowSettings(
                    startHour = 8,
                    endHour = 19,
                ),
                reminders = listOf(
                    ReminderItem(
                        id = "first",
                        text = "Protect your attention.",
                        schedule = ScheduleSettings(notificationsPerDay = 1),
                    ),
                    ReminderItem(
                        id = "second",
                        text = "Slow down before reacting.\nTake one breath first.",
                        schedule = ScheduleSettings(notificationsPerDay = 2),
                    ),
                ),
            ),
        )

        val imported = ReminderExchange.import(exported)

        assertEquals(2, imported.reminders.size)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            imported.reminderDays,
        )
        assertEquals(8, imported.notificationWindow.startHour)
        assertEquals(19, imported.notificationWindow.endHour)
        assertEquals("Protect your attention.", imported.reminders[0].text)
        assertEquals(1, imported.reminders[0].schedule.notificationsPerDay)
        assertEquals("Slow down before reacting.\nTake one breath first.", imported.reminders[1].text)
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
            Notifications per day: 50
            """.trimIndent(),
        )

        assertEquals(50, imported.reminders.single().schedule.notificationsPerDay)
    }

    @Test
    fun exportAndImportPreservesReminderDayOverride() {
        val exported = ReminderExchange.export(
            AppState(
                reminderDays = setOf(DayOfWeek.MONDAY),
                reminders = listOf(
                    ReminderItem(
                        id = "custom-days",
                        text = "Custom schedule.",
                        schedule = ScheduleSettings(
                            notificationsPerDay = 2,
                            reminderDays = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                        ),
                    ),
                ),
            ),
        )

        val imported = ReminderExchange.import(exported)

        assertEquals(2, imported.reminders.single().schedule.notificationsPerDay)
        assertEquals(
            setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            imported.reminders.single().schedule.reminderDays,
        )
    }

    @Test
    fun importSkipsObsoleteNotificationCountBeforeDailyCount() {
        val imported = ReminderExchange.import(
            """
            Smart Random Reminder Export v1

            Default start hour: 9
            Default end hour: 20

            ---
            Reminder:
            Review your priorities.
            End reminder
            Notifications per old total: 52
            Notifications per day: 26
            """.trimIndent(),
        )

        assertEquals(26, imported.reminders.single().schedule.notificationsPerDay)
    }

    @Test
    fun importSupportsLegacyPhoneReminderHeader() {
        val imported = ReminderExchange.import(
            """
            Phone Reminder Export v1

            This file can be imported back into Phone Reminder.
            Keep each block in the same format when editing by hand.

            Default start hour: 7
            Default end hour: 22

            ---
            Reminder:
            Remain in me and I'll remain in you
            End reminder
            Notifications per day: 1
            """.trimIndent(),
        )

        assertEquals(7, imported.notificationWindow.startHour)
        assertEquals(22, imported.notificationWindow.endHour)
        assertEquals("Remain in me and I'll remain in you", imported.reminders.single().text)
        assertEquals(1, imported.reminders.single().schedule.notificationsPerDay)
    }

    @Test
    fun importIgnoresFutureOptionalReminderMetadata() {
        val imported = ReminderExchange.import(
            """
            Smart Random Reminder Export v1

            Default start hour: 9
            Default end hour: 20
            Reminder days: MONDAY

            ---
            Reminder:
            First reminder
            End reminder
            Notifications per day: 1
            Created at: 123456789
            Color: blue

            ---
            Reminder:
            Second reminder
            End reminder
            Notifications per day: 2
            Stable id: future-id
            """.trimIndent(),
        )

        assertEquals(2, imported.reminders.size)
        assertEquals("First reminder", imported.reminders[0].text)
        assertEquals(1, imported.reminders[0].schedule.notificationsPerDay)
        assertEquals("Second reminder", imported.reminders[1].text)
        assertEquals(2, imported.reminders[1].schedule.notificationsPerDay)
    }
}
