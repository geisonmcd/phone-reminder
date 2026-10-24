package com.geison.phonereminder.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek

const val MAX_NOTIFICATIONS_PER_DAY = 50
val DEFAULT_REMINDER_DAYS: Set<DayOfWeek> = DayOfWeek.values().toSet()

@Serializable
data class ScheduleSettings(
    val notificationsPerDay: Int = 1,
    val reminderDays: Set<DayOfWeek>? = null,
    val cadence: ScheduleCadence = ScheduleCadence.DAILY,
)

@Serializable
enum class ScheduleCadence {
    DAILY,
    FIVE_TIMES_PER_WEEK,
    THREE_TIMES_PER_WEEK,
    WEEKLY,
    TWICE_MONTHLY,
    MONTHLY,
    PAUSED,
}

fun ScheduleCadence.lessOften(): ScheduleCadence {
    return when (this) {
        ScheduleCadence.DAILY -> ScheduleCadence.FIVE_TIMES_PER_WEEK
        ScheduleCadence.FIVE_TIMES_PER_WEEK -> ScheduleCadence.THREE_TIMES_PER_WEEK
        ScheduleCadence.THREE_TIMES_PER_WEEK -> ScheduleCadence.WEEKLY
        ScheduleCadence.WEEKLY -> ScheduleCadence.TWICE_MONTHLY
        ScheduleCadence.TWICE_MONTHLY -> ScheduleCadence.MONTHLY
        ScheduleCadence.MONTHLY -> ScheduleCadence.PAUSED
        ScheduleCadence.PAUSED -> ScheduleCadence.PAUSED
    }
}

fun ScheduleCadence.moreOften(): ScheduleCadence {
    return when (this) {
        ScheduleCadence.DAILY -> ScheduleCadence.DAILY
        ScheduleCadence.FIVE_TIMES_PER_WEEK -> ScheduleCadence.DAILY
        ScheduleCadence.THREE_TIMES_PER_WEEK -> ScheduleCadence.FIVE_TIMES_PER_WEEK
        ScheduleCadence.WEEKLY -> ScheduleCadence.THREE_TIMES_PER_WEEK
        ScheduleCadence.TWICE_MONTHLY -> ScheduleCadence.WEEKLY
        ScheduleCadence.MONTHLY -> ScheduleCadence.TWICE_MONTHLY
        ScheduleCadence.PAUSED -> ScheduleCadence.MONTHLY
    }
}

@Serializable
data class NotificationWindowSettings(
    val startHour: Int = 9,
    val endHour: Int = 20,
) {
    /**
     * Returns a window where 0 <= startHour < endHour <= 23.
     *
     * Older app versions and hand-edited imports could persist an inverted or out-of-range window
     * (e.g. start 23 / end 23). The settings steppers then called `coerceIn` with an empty range,
     * which throws IllegalArgumentException and crashed the settings screen.
     */
    fun normalized(): NotificationWindowSettings {
        val safeStart = startHour.coerceIn(MIN_HOUR, MAX_HOUR - 1)
        val safeEnd = endHour.coerceIn(safeStart + 1, MAX_HOUR)
        return if (safeStart == startHour && safeEnd == endHour) this else copy(startHour = safeStart, endHour = safeEnd)
    }

    fun withStartHourStep(delta: Int): NotificationWindowSettings {
        val window = normalized()
        return window.copy(startHour = (window.startHour + delta).coerceIn(MIN_HOUR, window.endHour - 1))
    }

    fun withEndHourStep(delta: Int): NotificationWindowSettings {
        val window = normalized()
        return window.copy(endHour = (window.endHour + delta).coerceIn(window.startHour + 1, MAX_HOUR))
    }

    companion object {
        const val MIN_HOUR = 0
        const val MAX_HOUR = 23
    }
}

@Serializable
data class ReminderItem(
    val id: String,
    val text: String,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val schedule: ScheduleSettings = ScheduleSettings(),
    /**
     * Last time the text or schedule changed. Stored data from before Drive sync has no value,
     * so it defaults to [createdAtEpochMillis] (see also ReminderStorage migration).
     */
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
) {
    /** Returns this reminder marked as edited at [nowMillis] (never moving the clock backwards). */
    fun touched(nowMillis: Long): ReminderItem {
        return copy(updatedAtEpochMillis = maxOf(nowMillis, updatedAtEpochMillis))
    }
}

/** Records that a reminder was deleted so the deletion can propagate through Drive sync. */
@Serializable
data class ReminderTombstone(
    val id: String,
    val deletedAtEpochMillis: Long,
)

const val TOMBSTONE_RETENTION_MILLIS: Long = 90L * 24 * 60 * 60 * 1000

@Serializable
data class AppState(
    val reminders: List<ReminderItem> = emptyList(),
    val notificationWindow: NotificationWindowSettings = NotificationWindowSettings(),
    val reminderDays: Set<DayOfWeek> = DEFAULT_REMINDER_DAYS,
    /** Last change to the global settings (notification window + reminder days); 0 = never edited. */
    val settingsUpdatedAtEpochMillis: Long = 0L,
    /** Deleted reminder ids, kept for [TOMBSTONE_RETENTION_MILLIS] so Drive sync can propagate deletes. */
    val deletedReminders: List<ReminderTombstone> = emptyList(),
) {
    fun normalized(): AppState {
        val window = notificationWindow.normalized()
        return if (window == notificationWindow) this else copy(notificationWindow = window)
    }

    /** Drops tombstones older than the retention window and ones for reminders that exist again. */
    fun prunedTombstones(nowMillis: Long): AppState {
        val liveIds = reminders.mapTo(HashSet()) { it.id }
        val cutoff = nowMillis - TOMBSTONE_RETENTION_MILLIS
        val kept = deletedReminders
            .filter { it.id !in liveIds && it.deletedAtEpochMillis >= cutoff }
            .groupBy { it.id }
            .map { (_, tombstones) -> tombstones.maxBy { it.deletedAtEpochMillis } }
        return if (kept == deletedReminders) this else copy(deletedReminders = kept)
    }
}
