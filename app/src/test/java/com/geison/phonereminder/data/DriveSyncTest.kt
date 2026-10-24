package com.geison.phonereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class DriveSyncTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun item(id: String, text: String, created: Long = now - 10 * day, updated: Long = created, perDay: Int = 1) =
        ReminderItem(
            id = id,
            text = text,
            createdAtEpochMillis = created,
            schedule = ScheduleSettings(notificationsPerDay = perDay),
            updatedAtEpochMillis = updated,
        )

    private fun state(
        vararg reminders: ReminderItem,
        tombstones: List<ReminderTombstone> = emptyList(),
        window: NotificationWindowSettings = NotificationWindowSettings(),
        days: Set<DayOfWeek> = DEFAULT_REMINDER_DAYS,
        settingsUpdated: Long = 0L,
    ) = AppState(
        reminders = reminders.toList(),
        notificationWindow = window,
        reminderDays = days,
        settingsUpdatedAtEpochMillis = settingsUpdated,
        deletedReminders = tombstones,
    )

    /** Simulates upload + download through the real Drive file format. */
    private fun roundTrip(local: AppState, state: AppState, legacyTimestamp: Long = 0L): AppState {
        val parsed = ReminderExchange.parse(ReminderExchange.export(state, includeSyncMetadata = true))
        return DriveSync.resolveRemote(local, parsed, legacyTimestamp)
    }

    @Test
    fun onlyLocalReminderIsUploaded() {
        val local = state(item("a", "Phone only"), item("b", "Both"))
        val remote = state(item("b", "Both"))

        assertEquals(DriveSync.Differences(onlyOnPhone = 1), DriveSync.diff(local, remote))
        val merged = DriveSync.merge(local, remote, now)
        assertEquals(listOf("a", "b"), merged.reminders.map { it.id })
    }

    @Test
    fun onlyRemoteReminderIsDownloaded() {
        val local = state(item("b", "Both"))
        val remote = state(item("b", "Both"), item("c", "Drive only"))

        assertEquals(DriveSync.Differences(onlyOnDrive = 1), DriveSync.diff(local, remote))
        val merged = DriveSync.merge(local, remote, now)
        assertEquals(listOf("b", "c"), merged.reminders.map { it.id })
        assertEquals("Drive only", merged.reminders.last().text)
    }

    @Test
    fun editedOnBothNewestRemoteWins() {
        val local = state(item("a", "Phone edit", updated = now - 2 * day))
        val remote = state(item("a", "Drive edit", updated = now - day))

        assertEquals(DriveSync.Differences(editedOnBoth = 1), DriveSync.diff(local, remote))
        assertEquals("Drive edit", DriveSync.merge(local, remote, now).reminders.single().text)
    }

    @Test
    fun editedOnBothNewestLocalWins() {
        val local = state(item("a", "Phone edit", updated = now - day, perDay = 3))
        val remote = state(item("a", "Drive edit", updated = now - 2 * day))

        val merged = DriveSync.merge(local, remote, now).reminders.single()
        assertEquals("Phone edit", merged.text)
        assertEquals(3, merged.schedule.notificationsPerDay)
    }

    @Test
    fun editedOnBothTieKeepsPhoneVersion() {
        val local = state(item("a", "Phone", updated = now - day))
        val remote = state(item("a", "Drive", updated = now - day))

        assertEquals("Phone", DriveSync.merge(local, remote, now).reminders.single().text)
    }

    @Test
    fun deletionOnPhoneBeatsOlderDriveEdit() {
        val local = state(tombstones = listOf(ReminderTombstone("a", now - day)))
        val remote = state(item("a", "Old edit", updated = now - 2 * day))

        assertEquals(DriveSync.Differences(deletedOnPhone = 1), DriveSync.diff(local, remote))
        val merged = DriveSync.merge(local, remote, now)
        assertTrue(merged.reminders.isEmpty())
        assertEquals(listOf(ReminderTombstone("a", now - day)), merged.deletedReminders)
    }

    @Test
    fun newerDriveEditBeatsDeletionOnPhone() {
        val local = state(tombstones = listOf(ReminderTombstone("a", now - 2 * day)))
        val remote = state(item("a", "Newer edit", updated = now - day))

        assertEquals(DriveSync.Differences(onlyOnDrive = 1), DriveSync.diff(local, remote))
        val merged = DriveSync.merge(local, remote, now)
        assertEquals("Newer edit", merged.reminders.single().text)
        assertTrue(merged.deletedReminders.isEmpty())
    }

    @Test
    fun deletionOnDriveBeatsOlderPhoneEdit() {
        val local = state(item("a", "Phone", updated = now - 2 * day))
        val remote = state(tombstones = listOf(ReminderTombstone("a", now - day)))

        assertEquals(DriveSync.Differences(deletedOnDrive = 1), DriveSync.diff(local, remote))
        val merged = DriveSync.merge(local, remote, now)
        assertTrue(merged.reminders.isEmpty())
        assertEquals(1, merged.deletedReminders.size)
    }

    @Test
    fun newerPhoneEditBeatsDeletionOnDrive() {
        val local = state(item("a", "Phone", updated = now - day))
        val remote = state(tombstones = listOf(ReminderTombstone("a", now - 2 * day)))

        assertEquals(DriveSync.Differences(onlyOnPhone = 1), DriveSync.diff(local, remote))
        val merged = DriveSync.merge(local, remote, now)
        assertEquals("Phone", merged.reminders.single().text)
        assertTrue(merged.deletedReminders.isEmpty())
    }

    @Test
    fun deletionWinsWhenTimestampsTie() {
        val local = state(item("a", "Phone", updated = now - day))
        val remote = state(tombstones = listOf(ReminderTombstone("a", now - day)))

        assertTrue(DriveSync.merge(local, remote, now).reminders.isEmpty())
    }

    @Test
    fun newestSettingsWin() {
        val localWindow = NotificationWindowSettings(startHour = 9, endHour = 18)
        val remoteWindow = NotificationWindowSettings(startHour = 7, endHour = 22)
        val local = state(window = localWindow, settingsUpdated = now - 2 * day)
        val remote = state(window = remoteWindow, days = setOf(DayOfWeek.SATURDAY), settingsUpdated = now - day)

        assertEquals(DriveSync.Differences(settingsDiffer = true), DriveSync.diff(local, remote))
        val merged = DriveSync.merge(local, remote, now)
        assertEquals(remoteWindow, merged.notificationWindow)
        assertEquals(setOf(DayOfWeek.SATURDAY), merged.reminderDays)
        assertEquals(now - day, merged.settingsUpdatedAtEpochMillis)

        val reversed = DriveSync.merge(remote.copy(settingsUpdatedAtEpochMillis = 1), local, now)
        assertEquals(localWindow, reversed.notificationWindow)
    }

    @Test
    fun settingsTieKeepsPhoneSettings() {
        val local = state(window = NotificationWindowSettings(9, 18))
        val remote = state(window = NotificationWindowSettings(7, 22))

        assertEquals(NotificationWindowSettings(9, 18), DriveSync.merge(local, remote, now).notificationWindow)
    }

    @Test
    fun legacyDriveFileWithoutIdsOrTimestampsIsParsed() {
        val local = state(
            item("local-1", "Drink water", created = now - 20 * day, perDay = 2),
            item("local-2", "Phone only"),
        )
        // A backup written by the previous app version: no ids, timestamps or tombstones.
        val legacyContent = ReminderExchange.export(
            state(
                item("x", "Drink water", perDay = 2),
                item("y", "Stretch"),
                item("z", "Stretch"),
                window = NotificationWindowSettings(8, 20),
            ),
        )
        assertFalse(legacyContent.contains("Id: "))
        val parsed = ReminderExchange.parse(legacyContent)
        assertTrue(parsed.reminders.all { it.id == null && it.updatedAtEpochMillis == null })
        assertTrue(parsed.deletedReminders.isEmpty())
        assertEquals(null, parsed.settingsUpdatedAtEpochMillis)

        val legacyTime = now - 5 * day
        val remote = DriveSync.resolveRemote(local, parsed, legacyTime)
        // Same text is matched to the phone reminder, keeping its id and createdAt as updatedAt.
        val water = remote.reminders.first()
        assertEquals("local-1", water.id)
        assertEquals(now - 20 * day, water.updatedAtEpochMillis)
        // Unmatched legacy reminders get stable, distinct ids; updatedAt = createdAt = file time.
        val stretches = remote.reminders.filter { it.text == "Stretch" }
        assertEquals(2, stretches.map { it.id }.toSet().size)
        assertTrue(stretches.all { it.createdAtEpochMillis == legacyTime && it.updatedAtEpochMillis == legacyTime })
        assertEquals(stretches.map { it.id }, DriveSync.resolveRemote(local, parsed, legacyTime).reminders.drop(1).map { it.id })
        assertEquals(0L, remote.settingsUpdatedAtEpochMillis)

        assertEquals(
            DriveSync.Differences(onlyOnPhone = 1, onlyOnDrive = 2, settingsDiffer = true),
            DriveSync.diff(local, remote),
        )
        val merged = DriveSync.merge(local, remote, now)
        assertEquals(listOf("Drink water", "Phone only", "Stretch", "Stretch"), merged.reminders.map { it.text })
        // Legacy file has no settings timestamp: the phone keeps its settings.
        assertEquals(local.notificationWindow, merged.notificationWindow)
    }

    @Test
    fun syncingTwiceGivesZeroDifferences() {
        val local = state(
            item("a", "Phone only", updated = now - day),
            item("b", "Edited", updated = now - day),
            tombstones = listOf(ReminderTombstone("gone", now - day)),
            window = NotificationWindowSettings(9, 18),
            settingsUpdated = now - day,
        )
        val remote = state(
            item("b", "Older", updated = now - 2 * day),
            item("c", "Drive only"),
            item("gone", "Deleted on phone", updated = now - 3 * day),
        )

        val merged = DriveSync.merge(local, remote, now)
        val uploaded = roundTrip(merged, merged)
        assertTrue(DriveSync.diff(merged, uploaded).isInSync)
        assertEquals(merged, DriveSync.merge(merged, uploaded, now))
        // Second sync from the same state changes nothing.
        val second = DriveSync.merge(merged, uploaded, now + day)
        assertEquals(merged.reminders, second.reminders)
        assertTrue(DriveSync.diff(second, roundTrip(second, second)).isInSync)
        assertEquals(listOf("a", "b", "c"), merged.reminders.map { it.id })
    }

    @Test
    fun deletedDriveFileIsTreatedAsNoRemoteAndReuploadsEverything() {
        val local = state(
            item("a", "One"),
            item("b", "Two"),
            tombstones = listOf(ReminderTombstone("old", now - day)),
            window = NotificationWindowSettings(9, 18),
        )

        val differences = DriveSync.diff(local, remote = null)
        assertEquals(DriveSync.Differences(onlyOnPhone = 2), differences)

        val merged = DriveSync.merge(local, remote = null, nowMillis = now)
        assertEquals(local.reminders, merged.reminders)
        assertEquals(local.notificationWindow, merged.notificationWindow)
        // The re-uploaded file holds the full local state.
        val uploaded = roundTrip(merged, merged)
        assertEquals(local.reminders, uploaded.reminders)
        assertTrue(DriveSync.diff(merged, uploaded).isInSync)
    }

    @Test
    fun reminderRemovedByHandFromDriveFileIsOnlyOnPhoneAndReuploaded() {
        val local = state(item("a", "Keep me"), item("b", "Hand removed"))
        val fullFile = ReminderExchange.export(local, includeSyncMetadata = true)
        val blocks = fullFile.split("\n---\n")
        val handEdited = blocks.filterNot { it.contains("Hand removed") }.joinToString("\n---\n")
        assertFalse(handEdited.contains("Hand removed"))

        val remote = DriveSync.resolveRemote(local, ReminderExchange.parse(handEdited), now)
        assertEquals(listOf("a"), remote.reminders.map { it.id })
        assertEquals(DriveSync.Differences(onlyOnPhone = 1), DriveSync.diff(local, remote))

        val merged = DriveSync.merge(local, remote, now)
        assertEquals(listOf("a", "b"), merged.reminders.map { it.id })
        assertTrue(DriveSync.diff(merged, roundTrip(merged, merged)).isInSync)
    }

    @Test
    fun tombstonesOlderThanRetentionArePruned() {
        val local = state(
            tombstones = listOf(
                ReminderTombstone("old", now - TOMBSTONE_RETENTION_MILLIS - 1),
                ReminderTombstone("recent", now - day),
            ),
        )
        val merged = DriveSync.merge(local, state(), now)
        assertEquals(listOf("recent"), merged.deletedReminders.map { it.id })
    }

    @Test
    fun timestampOnlyDifferencesAreNotReported() {
        val local = state(item("a", "Same", updated = now - day))
        val remote = state(item("a", "Same", updated = now - 2 * day))
        assertTrue(DriveSync.diff(local, remote).isInSync)
    }

    @Test
    fun syncMetadataSurvivesExportRoundTrip() {
        val original = state(
            item("a", "Multi\nline", created = now - 3 * day, updated = now - day, perDay = 4),
            tombstones = listOf(ReminderTombstone("x", now - day)),
            settingsUpdated = now - 2 * day,
        )
        val parsed = ReminderExchange.parse(ReminderExchange.export(original, includeSyncMetadata = true))
        val reminder = parsed.reminders.single()
        assertEquals("a", reminder.id)
        assertEquals(now - 3 * day, reminder.createdAtEpochMillis)
        assertEquals(now - day, reminder.updatedAtEpochMillis)
        assertEquals(now - 2 * day, parsed.settingsUpdatedAtEpochMillis)
        assertEquals(original.deletedReminders, parsed.deletedReminders)

        // The regular txt import still ignores the sync lines and creates fresh reminders.
        val imported = ReminderExchange.import(ReminderExchange.export(original, includeSyncMetadata = true))
        assertEquals("Multi\nline", imported.reminders.single().text)
        assertNotNull(imported.reminders.single().id)
        assertFalse(imported.reminders.single().id == "a")
    }
}
