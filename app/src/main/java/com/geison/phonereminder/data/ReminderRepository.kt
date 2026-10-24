package com.geison.phonereminder.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.util.UUID

class ReminderRepository(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(ReminderStorage.load(context))
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    fun addReminder(text: String): String? {
        return addReminder(
            text = text,
            schedule = ScheduleSettings(),
            createdAtEpochMillis = clock(),
        )
    }

    fun addReminder(
        text: String,
        schedule: ScheduleSettings,
        createdAtEpochMillis: Long,
    ): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val reminderId = UUID.randomUUID().toString()
        updateState {
            copy(
                reminders = reminders + ReminderItem(
                    id = reminderId,
                    text = trimmed,
                    createdAtEpochMillis = createdAtEpochMillis,
                    schedule = schedule,
                    updatedAtEpochMillis = maxOf(createdAtEpochMillis, clock()),
                ),
            )
        }
        return reminderId
    }

    fun deleteReminder(id: String) {
        updateState {
            copy(reminders = reminders.filterNot { it.id == id })
        }
    }

    /** Applies [transform] and bumps updatedAt when the reminder content actually changed. */
    fun updateReminder(
        id: String,
        transform: (ReminderItem) -> ReminderItem,
    ) {
        val now = clock()
        updateState {
            copy(
                reminders = reminders.map { reminder ->
                    if (reminder.id == id) {
                        val updated = transform(reminder)
                        if (updated == reminder) reminder else updated.touched(now)
                    } else {
                        reminder
                    }
                },
            )
        }
    }

    fun findReminder(id: String): ReminderItem? {
        return mutableState.value.reminders.firstOrNull { it.id == id }
    }

    fun replaceReminders(reminders: List<ReminderItem>) {
        updateState {
            copy(reminders = reminders)
        }
    }

    /**
     * Replaces everything from a .txt import. Reminders that disappear get tombstones so Drive sync
     * deletes them there too, and imported settings count as a fresh settings edit.
     */
    fun replaceState(state: AppState) {
        updateState {
            state.copy(
                settingsUpdatedAtEpochMillis = this.settingsUpdatedAtEpochMillis,
                deletedReminders = this.deletedReminders,
            ).normalized()
        }
    }

    /** Stores the result of a Drive sync merge as-is (its tombstones and timestamps are authoritative). */
    fun replaceWithSyncedState(state: AppState) {
        val normalized = state.normalized().prunedTombstones(clock())
        mutableState.value = normalized
        ReminderStorage.save(context, normalized)
    }

    /** Re-reads storage, e.g. after a notification action changed a reminder in the background. */
    fun reloadFromStorage() {
        mutableState.value = ReminderStorage.load(context)
    }

    fun updateNotificationWindow(
        startHour: Int,
        endHour: Int,
    ) {
        updateState {
            copy(
                notificationWindow = NotificationWindowSettings(
                    startHour = startHour,
                    endHour = endHour,
                ).normalized(),
            )
        }
    }

    fun updateReminderDays(reminderDays: Set<DayOfWeek>) {
        updateState {
            copy(reminderDays = reminderDays)
        }
    }

    private fun updateState(transform: AppState.() -> AppState) {
        val current = mutableState.value
        val updated = LocalChangeTracker.apply(current, current.transform(), clock())
        if (updated == current) {
            return
        }
        mutableState.value = updated
        ReminderStorage.save(context, updated)
    }
}

/** Bookkeeping for Drive sync on every local edit: tombstones for deletes, settings timestamp. */
object LocalChangeTracker {
    fun apply(before: AppState, after: AppState, nowMillis: Long): AppState {
        val remainingIds = after.reminders.mapTo(HashSet()) { it.id }
        val newTombstones = before.reminders
            .filter { it.id !in remainingIds }
            .map { ReminderTombstone(id = it.id, deletedAtEpochMillis = nowMillis) }
        val settingsChanged = before.notificationWindow != after.notificationWindow ||
            before.reminderDays != after.reminderDays
        val withSettings = if (settingsChanged && after.settingsUpdatedAtEpochMillis == before.settingsUpdatedAtEpochMillis) {
            after.copy(settingsUpdatedAtEpochMillis = maxOf(nowMillis, before.settingsUpdatedAtEpochMillis + 1))
        } else {
            after
        }
        val withTombstones = if (newTombstones.isEmpty()) {
            withSettings
        } else {
            withSettings.copy(deletedReminders = withSettings.deletedReminders + newTombstones)
        }
        return withTombstones.prunedTombstones(nowMillis)
    }
}
