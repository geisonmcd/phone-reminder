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
)

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
)
