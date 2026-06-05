package com.geison.phonereminder.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek

const val MAX_NOTIFICATIONS_PER_DAY = 50
val DEFAULT_REMINDER_DAYS: Set<DayOfWeek> = DayOfWeek.values().toSet()

@Serializable
data class ScheduleSettings(
    val notificationsPerDay: Int = 1,
)

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
