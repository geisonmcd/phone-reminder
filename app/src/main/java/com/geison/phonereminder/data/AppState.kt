package com.geison.phonereminder.data

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleSettings(
    val notificationsPerWeek: Int = 3,
    val startHour: Int = 9,
    val endHour: Int = 20,
)

@Serializable
data class ReminderItem(
    val id: String,
    val text: String,
    val schedule: ScheduleSettings = ScheduleSettings(),
)

@Serializable
data class AppState(
    val reminders: List<ReminderItem> = emptyList(),
)
