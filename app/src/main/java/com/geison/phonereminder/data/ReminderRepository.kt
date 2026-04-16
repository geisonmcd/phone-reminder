package com.geison.phonereminder.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ReminderRepository(private val context: Context) {
    private val mutableState = MutableStateFlow(ReminderStorage.load(context))
    val state: StateFlow<AppState> = mutableState.asStateFlow()

    fun addReminder(text: String): String? {
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

    fun updateReminder(
        id: String,
        transform: (ReminderItem) -> ReminderItem,
    ) {
        updateState {
            copy(
                reminders = reminders.map { reminder ->
                    if (reminder.id == id) {
                        transform(reminder)
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

    fun replaceState(state: AppState) {
        mutableState.value = state
        ReminderStorage.save(context, state)
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
                ),
            )
        }
    }

    private fun updateState(transform: AppState.() -> AppState) {
        val updated = mutableState.value.transform()
        mutableState.value = updated
        ReminderStorage.save(context, updated)
    }
}
