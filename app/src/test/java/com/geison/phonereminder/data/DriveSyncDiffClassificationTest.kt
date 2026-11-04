package com.geison.phonereminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * Classifies same-id content mismatches as phone-only, Drive-only, or true conflicts using a
 * post-sync baseline and a last-sync timestamp fallback for upgrades.
 */
class DriveSyncDiffClassificationTest {
    private val day = 24L * 60 * 60 * 1000
    private val lastSync = 1_800_000_000_000L
    private val beforeSync = lastSync - 5 * day
    private val afterSyncPhone = lastSync + day
    private val afterSyncDrive = lastSync + 2 * day

    private fun item(
        id: String,
        text: String,
        updated: Long = beforeSync,
        perDay: Int = 1,
        cadence: ScheduleCadence = ScheduleCadence.DAILY,
        days: Set<DayOfWeek>? = null,
    ) = ReminderItem(
        id = id,
        text = text,
        createdAtEpochMillis = beforeSync,
        schedule = ScheduleSettings(notificationsPerDay = perDay, reminderDays = days, cadence = cadence),
        updatedAtEpochMillis = updated,
    )

    private fun state(vararg reminders: ReminderItem) = AppState(reminders = reminders.toList())

    private fun syncedPair(base: AppState): Pair<AppState, DriveSync.Baseline> {
        val baseline = DriveSync.baselineFrom(base)
        return base to baseline
    }

    @Test
    fun phoneOnlyTextEditAfterSyncIsEditedOnPhone() {
        val atSync = state(item("r1", "Anonymized reminder text"))
        val (_, baseline) = syncedPair(atSync)
        val local = atSync.copy(
            reminders = listOf(item("r1", "Anonymized reminder text edited", updated = afterSyncPhone)),
        )
        val remote = atSync

        assertEquals(
            DriveSync.Differences(editedOnPhone = 1),
            DriveSync.diff(local, remote, lastSyncAtEpochMillis = lastSync, baseline = baseline),
        )
    }

    @Test
    fun driveOnlyContentChangeAfterSyncIsEditedOnDrive() {
        val atSync = state(item("r1", "Shared baseline text"))
        val (_, baseline) = syncedPair(atSync)
        val local = atSync
        val remote = atSync.copy(
            reminders = listOf(item("r1", "Shared baseline text from Drive", updated = afterSyncDrive)),
        )

        assertEquals(
            DriveSync.Differences(editedOnDrive = 1),
            DriveSync.diff(local, remote, lastSyncAtEpochMillis = lastSync, baseline = baseline),
        )
    }

    @Test
    fun bothSidesChangedSinceBaseIsEditedOnBoth() {
        val atSync = state(item("r1", "Original"))
        val (_, baseline) = syncedPair(atSync)
        val local = state(item("r1", "Phone version", updated = afterSyncPhone))
        val remote = state(item("r1", "Drive version", updated = afterSyncDrive))

        assertEquals(
            DriveSync.Differences(editedOnBoth = 1),
            DriveSync.diff(local, remote, lastSyncAtEpochMillis = lastSync, baseline = baseline),
        )
    }

    @Test
    fun syncNowThenCompareShowsZeroDifferences() {
        val local = state(
            item("a", "Alpha"),
            item("b", "Beta", cadence = ScheduleCadence.WEEKLY),
        )
        val merged = local
        val uploaded = roundTrip(merged, merged)
        val baseline = DriveSync.baselineFrom(merged)
        val syncAt = lastSync

        assertEquals(
            DriveSync.Differences(),
            DriveSync.diff(merged, uploaded, lastSyncAtEpochMillis = syncAt, baseline = baseline),
        )
    }

    @Test
    fun timestampOnlyDifferencesAreNotReportedWithBaseline() {
        val atSync = state(item("a", "Same text", updated = beforeSync))
        val (_, baseline) = syncedPair(atSync)
        val local = atSync.copy(reminders = listOf(item("a", "Same text", updated = afterSyncPhone)))
        val remote = atSync.copy(reminders = listOf(item("a", "Same text", updated = afterSyncDrive)))

        assertTrue(
            DriveSync.diff(local, remote, lastSyncAtEpochMillis = lastSync, baseline = baseline).isInSync,
        )
    }

    @Test
    fun notificationCadenceChangeCountsAsEditedOnPhone() {
        val atSync = state(item("n1", "Notify me", perDay = 2))
        val (_, baseline) = syncedPair(atSync)
        val local = atSync.copy(
            reminders = listOf(
                item("n1", "Notify me", updated = afterSyncPhone, perDay = 4),
            ),
        )
        val remote = atSync

        assertEquals(
            DriveSync.Differences(editedOnPhone = 1),
            DriveSync.diff(local, remote, lastSyncAtEpochMillis = lastSync, baseline = baseline),
        )
    }

    @Test
    fun upgradePathWithoutBaselineUsesLastSyncHeuristicForPhoneEdit() {
        val local = state(item("r1", "Phone edit", updated = afterSyncPhone))
        val remote = state(item("r1", "Older remote", updated = beforeSync))

        assertEquals(
            DriveSync.Differences(editedOnPhone = 1),
            DriveSync.diff(local, remote, lastSyncAtEpochMillis = lastSync, baseline = null),
        )
    }

    private fun roundTrip(local: AppState, state: AppState): AppState {
        val parsed = ReminderExchange.parse(ReminderExchange.export(state, includeSyncMetadata = true))
        return DriveSync.resolveRemote(local, parsed, legacyTimestampMillis = lastSync)
    }
}
