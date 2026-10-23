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
)

@Serializable
data class AppState(
    val reminders: List<ReminderItem> = emptyList(),
    val notificationWindow: NotificationWindowSettings = NotificationWindowSettings(),
    val reminderDays: Set<DayOfWeek> = DEFAULT_REMINDER_DAYS,
) {
    fun normalized(): AppState {
        val window = notificationWindow.normalized()
        return if (window == notificationWindow) this else copy(notificationWindow = window)
    }
}
