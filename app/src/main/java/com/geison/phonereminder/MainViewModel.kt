package com.geison.phonereminder

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.geison.phonereminder.data.ReminderExchange
import com.geison.phonereminder.data.ReminderItem
import com.geison.phonereminder.data.ReminderRepository
import com.geison.phonereminder.data.ScheduleSettings
import com.geison.phonereminder.notifications.NotificationScheduler
import com.geison.phonereminder.notifications.ReminderNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReminderRepository(application)
    private val mutableOpenReminderRequest = MutableStateFlow<String?>(null)

    val state = repository.state
    val openReminderRequest = mutableOpenReminderRequest.asStateFlow()

    fun addReminder(text: String): String? {
        val reminderId = repository.addReminder(text)
        if (reminderId != null) {
            NotificationScheduler.scheduleToday(getApplication())
        }
        return reminderId
    }

    fun deleteReminder(id: String) {
        repository.deleteReminder(id)
        NotificationScheduler.scheduleToday(getApplication())
    }

    fun findReminder(id: String): ReminderItem? {
        return repository.findReminder(id)
    }

    fun saveReminder(
        reminderId: String,
        text: String,
        notificationsPerWeek: Int,
        notificationsPerDay: Int,
    ) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) {
            return
        }

        val safeNotificationsPerDay = notificationsPerDay.coerceIn(1, 5)
        val safeNotificationsPerWeek = snapWeeklyCount(
            value = notificationsPerWeek,
            notificationsPerDay = safeNotificationsPerDay,
        )

        repository.updateReminder(reminderId) { current ->
            current.copy(
                text = trimmedText,
                schedule = ScheduleSettings(
                    notificationsPerWeek = safeNotificationsPerWeek,
                    notificationsPerDay = safeNotificationsPerDay,
                ),
            )
        }
        NotificationScheduler.scheduleToday(getApplication())
    }

    fun updateNotificationWindow(
        startHour: Int,
        endHour: Int,
    ) {
        val safeStartHour = startHour.coerceIn(0, 22)
        val safeEndHour = endHour.coerceIn(safeStartHour + 1, 23)
        repository.updateNotificationWindow(
            startHour = safeStartHour,
            endHour = safeEndHour,
        )
        NotificationScheduler.scheduleToday(getApplication())
    }

    fun testReminder(
        reminderId: String,
        text: String,
    ) {
        if (text.isBlank()) {
            return
        }

        ReminderNotifier.showReminder(
            context = getApplication(),
            notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            reminderId = reminderId,
            reminderText = text.trim(),
        )
    }

    fun requestOpenReminder(reminderId: String?) {
        mutableOpenReminderRequest.value = reminderId
    }

    fun clearOpenReminderRequest() {
        mutableOpenReminderRequest.value = null
    }

    fun rescheduleNow() {
        NotificationScheduler.scheduleToday(getApplication())
    }

    fun exportReminders(uri: Uri): String {
        val content = ReminderExchange.export(state.value)
        return runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(content)
            } ?: error("Could not open the selected file.")
        }.fold(
            onSuccess = {
                val count = state.value.reminders.size
                if (count == 1) {
                    "Exported 1 reminder."
                } else {
                    "Exported $count reminders."
                }
            },
            onFailure = { error ->
                "Export failed: ${error.message ?: "Unknown error."}"
            },
        )
    }

    fun importReminders(uri: Uri): String {
        return runCatching {
            val content = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error("Could not open the selected file.")

            val importedState = ReminderExchange.import(content)
            repository.replaceState(importedState)
            NotificationScheduler.scheduleToday(getApplication())

            val count = importedState.reminders.size
            if (count == 1) {
                "Imported 1 reminder."
            } else {
                "Imported $count reminders."
            }
        }.getOrElse { error ->
            "Import failed: ${error.message ?: "Unknown error."}"
        }
    }

    private fun snapWeeklyCount(
        value: Int,
        notificationsPerDay: Int,
    ): Int {
        val minValue = notificationsPerDay
        val maxValue = notificationsPerDay * 7
        val coerced = value.coerceIn(minValue, maxValue)
        val remainder = coerced % notificationsPerDay
        return if (remainder == 0) {
            coerced
        } else {
            (coerced + notificationsPerDay - remainder).coerceAtMost(maxValue)
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(application) as T
                }
            }
        }
    }
}
