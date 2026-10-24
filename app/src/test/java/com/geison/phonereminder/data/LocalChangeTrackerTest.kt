package com.geison.phonereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalChangeTrackerTest {
    private val now = 1_800_000_000_000L

    @Test
    fun deletingReminderAddsTombstone() {
        val before = AppState(reminders = listOf(ReminderItem(id = "a", text = "A", createdAtEpochMillis = 1L)))
        val after = LocalChangeTracker.apply(before, before.copy(reminders = emptyList()), now)
        assertEquals(listOf(ReminderTombstone("a", now)), after.deletedReminders)
    }

    @Test
    fun changingSettingsBumpsSettingsTimestamp() {
        val before = AppState()
        val after = LocalChangeTracker.apply(
            before,
            before.copy(notificationWindow = NotificationWindowSettings(10, 20)),
            now,
        )
        assertEquals(now, after.settingsUpdatedAtEpochMillis)
    }

    @Test
    fun recreatedIdDropsItsTombstone() {
        val state = AppState(
            reminders = listOf(ReminderItem(id = "a", text = "A", createdAtEpochMillis = 1L)),
            deletedReminders = listOf(ReminderTombstone("a", now - 1)),
        )
        assertTrue(state.prunedTombstones(now).deletedReminders.isEmpty())
    }

    @Test
    fun legacyStoredStateDefaultsUpdatedAtToCreatedAt() {
        val raw = """{"reminders":[{"id":"a","text":"A","createdAtEpochMillis":1234,"schedule":{"notificationsPerDay":2}}]}"""
        val state = ReminderStorage.decode(raw)
        val reminder = state.reminders.single()
        assertEquals(1234L, reminder.updatedAtEpochMillis)
        assertTrue(state.deletedReminders.isEmpty())
        assertEquals(0L, state.settingsUpdatedAtEpochMillis)
        assertEquals(state, ReminderStorage.decode(ReminderStorage.encode(state)))
    }

    @Test
    fun touchedNeverMovesUpdatedAtBackwards() {
        val reminder = ReminderItem(id = "a", text = "A", createdAtEpochMillis = 1L, updatedAtEpochMillis = now)
        assertEquals(now, reminder.touched(now - 10).updatedAtEpochMillis)
        assertEquals(now + 5, reminder.touched(now + 5).updatedAtEpochMillis)
    }
}
