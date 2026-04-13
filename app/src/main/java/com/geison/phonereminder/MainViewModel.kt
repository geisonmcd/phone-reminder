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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReminderRepository(application)
    val state = repository.state

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
        startHour: Int,
        endHour: Int,
    ) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) {
            return
        }

        val safeStartHour = startHour.coerceIn(0, 22)
        val safeEndHour = endHour.coerceIn(safeStartHour + 1, 23)
        repository.updateReminder(reminderId) { current ->
            current.copy(
                text = trimmedText,
                schedule = ScheduleSettings(
                    notificationsPerWeek = notificationsPerWeek.coerceIn(1, 7),
                    startHour = safeStartHour,
                    endHour = safeEndHour,
                ),
            )
        }
        NotificationScheduler.scheduleToday(getApplication())
    }

    fun testReminder(text: String) {
        if (text.isBlank()) {
            return
        }
        ReminderNotifier.showReminder(
            context = getApplication(),
            notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            reminderText = text.trim(),
        )
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

            val reminders = ReminderExchange.import(content)
            repository.replaceReminders(reminders)
            NotificationScheduler.scheduleToday(getApplication())

            val count = reminders.size
            if (count == 1) {
                "Imported 1 reminder."
            } else {
                "Imported $count reminders."
            }
        }.getOrElse { error ->
            "Import failed: ${error.message ?: "Unknown error."}"
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
