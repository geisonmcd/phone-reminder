package com.geison.phonereminder.data

import java.util.UUID

/**
 * Pure two-way sync rules between the phone state and the Google Drive sync file.
 *
 * Rules:
 * - Reminders are matched by id. A reminder on only one side is copied to the other side.
 * - Same id on both sides with different content: the most recent [ReminderItem.updatedAtEpochMillis]
 *   wins; on a tie the phone wins.
 * - Deletions are tombstones (id + deletedAt). A tombstone wins over an edit that is older than or
 *   as old as the deletion and loses to a newer edit (the reminder is then kept everywhere).
 * - Global settings (notification window + reminder days) move together: the side with the newest
 *   [AppState.settingsUpdatedAtEpochMillis] wins; on a tie the phone wins.
 * - A reminder missing from Drive without a tombstone (Drive file deleted or hand-edited) is
 *   "only on phone" and is uploaded again; nothing on the phone is lost.
 *
 * No Android dependencies so it can be unit tested on the JVM.
 */
object DriveSync {

    data class Differences(
        val onlyOnPhone: Int = 0,
        val onlyOnDrive: Int = 0,
        val editedOnBoth: Int = 0,
        /** Reminders on the phone that Drive has deleted (sync will delete them on the phone). */
        val deletedOnDrive: Int = 0,
        /** Reminders on Drive that the phone has deleted (sync will delete them on Drive). */
        val deletedOnPhone: Int = 0,
        val settingsDiffer: Boolean = false,
    ) {
        val total: Int
            get() = onlyOnPhone + onlyOnDrive + editedOnBoth + deletedOnDrive + deletedOnPhone +
                (if (settingsDiffer) 1 else 0)

        val isInSync: Boolean
            get() = total == 0
    }

    /**
     * Turns a parsed Drive file into an [AppState] comparable with [local].
     *
     * Legacy backups (written before sync existed) carry no ids or timestamps: their reminders are
     * matched to phone reminders with the exact same text, and otherwise get a stable id derived
     * from the text so repeated comparisons give the same result. Missing timestamps fall back to
     * createdAt (phone reminder) or [legacyTimestampMillis] (e.g. the Drive file's modified time).
     */
    fun resolveRemote(
        local: AppState,
        parsed: ReminderExchange.ParsedExport,
        legacyTimestampMillis: Long,
    ): AppState {
        val localById = local.reminders.associateBy { it.id }
        val explicitIds = mutableSetOf<String>()
        val firstPass = parsed.reminders.map { reminder ->
            val id = reminder.id?.takeIf { explicitIds.add(it) }
            reminder to id
        }
        val claimedLocalIds = explicitIds.toMutableSet()
        val legacyOccurrences = mutableMapOf<String, Int>()

        val reminders = firstPass.map { (reminder, explicitId) ->
            val id = explicitId
                ?: local.reminders.firstOrNull { candidate ->
                    candidate.id !in claimedLocalIds && candidate.text.trim() == reminder.text.trim()
                }?.id?.also { claimedLocalIds += it }
                ?: legacyId(reminder.text, legacyOccurrences)
            val createdAt = reminder.createdAtEpochMillis
                ?: localById[id]?.createdAtEpochMillis
                ?: legacyTimestampMillis
            ReminderItem(
                id = id,
                text = reminder.text,
                createdAtEpochMillis = createdAt,
                schedule = reminder.schedule,
                updatedAtEpochMillis = reminder.updatedAtEpochMillis ?: createdAt,
            )
        }

        return AppState(
            reminders = reminders,
            notificationWindow = parsed.notificationWindow,
            reminderDays = parsed.reminderDays,
            settingsUpdatedAtEpochMillis = parsed.settingsUpdatedAtEpochMillis ?: 0L,
            deletedReminders = parsed.deletedReminders,
        ).normalized()
    }

    /** Compares without changing anything. [remote] is null when there is no Drive file yet. */
    fun diff(local: AppState, remote: AppState?): Differences {
        val remoteState = remote ?: AppState(
            notificationWindow = local.notificationWindow,
            reminderDays = local.reminderDays,
        )
        val localById = local.reminders.associateBy { it.id }
        val remoteById = remoteState.reminders.associateBy { it.id }
        val localTombstones = latestTombstones(local.deletedReminders)
        val remoteTombstones = latestTombstones(remoteState.deletedReminders)

        var onlyOnPhone = 0
        var onlyOnDrive = 0
        var editedOnBoth = 0
        var deletedOnDrive = 0
        var deletedOnPhone = 0

        (localById.keys + remoteById.keys).forEach { id ->
            val l = localById[id]
            val r = remoteById[id]
            val localTomb = localTombstones[id]
            val remoteTomb = remoteTombstones[id]
            val live = newest(l, r)
            val tomb = listOfNotNull(localTomb, remoteTomb).maxByOrNull { it.deletedAtEpochMillis }
            val deletionWins = tomb != null && live != null && tomb.deletedAtEpochMillis >= live.updatedAtEpochMillis
            when {
                l != null && r == null ->
                    if (deletionWins) deletedOnDrive++ else onlyOnPhone++
                l == null && r != null ->
                    if (deletionWins) deletedOnPhone++ else onlyOnDrive++
                l != null && r != null -> when {
                    deletionWins && tomb === remoteTomb -> deletedOnDrive++
                    deletionWins -> deletedOnPhone++
                    !sameContent(l, r) -> editedOnBoth++
                }
            }
        }

        val settingsDiffer = remote != null && (
            local.notificationWindow.normalized() != remoteState.notificationWindow.normalized() ||
                local.reminderDays != remoteState.reminderDays
            )

        return Differences(
            onlyOnPhone = onlyOnPhone,
            onlyOnDrive = onlyOnDrive,
            editedOnBoth = editedOnBoth,
            deletedOnDrive = deletedOnDrive,
            deletedOnPhone = deletedOnPhone,
            settingsDiffer = settingsDiffer,
        )
    }

    /** Two-way merge. The result is written to the phone and uploaded to Drive. */
    fun merge(local: AppState, remote: AppState?, nowMillis: Long): AppState {
        if (remote == null) {
            return local.prunedTombstones(nowMillis)
        }
        val localById = local.reminders.associateBy { it.id }
        val remoteById = remote.reminders.associateBy { it.id }
        val localTombstones = latestTombstones(local.deletedReminders)
        val remoteTombstones = latestTombstones(remote.deletedReminders)

        val orderedIds = LinkedHashSet<String>().apply {
            local.reminders.forEach { add(it.id) }
            remote.reminders.forEach { add(it.id) }
            addAll(localTombstones.keys)
            addAll(remoteTombstones.keys)
        }

        val reminders = mutableListOf<ReminderItem>()
        val tombstones = mutableListOf<ReminderTombstone>()
        orderedIds.forEach { id ->
            val live = newest(localById[id], remoteById[id])
            val tomb = listOfNotNull(localTombstones[id], remoteTombstones[id])
                .maxByOrNull { it.deletedAtEpochMillis }
            if (tomb != null && (live == null || tomb.deletedAtEpochMillis >= live.updatedAtEpochMillis)) {
                tombstones += tomb
            } else if (live != null) {
                reminders += live
            }
        }

        val remoteSettingsWin = remote.settingsUpdatedAtEpochMillis > local.settingsUpdatedAtEpochMillis
        val settingsSource = if (remoteSettingsWin) remote else local

        return AppState(
            reminders = reminders,
            notificationWindow = settingsSource.notificationWindow,
            reminderDays = settingsSource.reminderDays,
            settingsUpdatedAtEpochMillis = settingsSource.settingsUpdatedAtEpochMillis,
            deletedReminders = tombstones,
        ).normalized().prunedTombstones(nowMillis)
    }

    private fun newest(local: ReminderItem?, remote: ReminderItem?): ReminderItem? {
        return when {
            local == null -> remote
            remote == null -> local
            remote.updatedAtEpochMillis > local.updatedAtEpochMillis -> remote
            else -> local
        }
    }

    private fun sameContent(a: ReminderItem, b: ReminderItem): Boolean {
        return a.text.trim() == b.text.trim() && a.schedule == b.schedule
    }

    private fun latestTombstones(tombstones: List<ReminderTombstone>): Map<String, ReminderTombstone> {
        return tombstones.groupBy { it.id }.mapValues { (_, list) -> list.maxBy { it.deletedAtEpochMillis } }
    }

    private fun legacyId(text: String, occurrences: MutableMap<String, Int>): String {
        val key = text.trim()
        val occurrence = occurrences.getOrDefault(key, 0)
        occurrences[key] = occurrence + 1
        return "legacy-" + UUID.nameUUIDFromBytes("$key\u0000$occurrence".toByteArray(Charsets.UTF_8))
    }
}
