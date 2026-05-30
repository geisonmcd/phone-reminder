package com.geison.phonereminder

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.geison.phonereminder.R
import com.geison.phonereminder.data.AppState
import com.geison.phonereminder.data.MAX_NOTIFICATIONS_PER_DAY
import com.geison.phonereminder.data.ReminderExchange
import com.geison.phonereminder.data.ReminderImportMerge
import com.geison.phonereminder.data.ReminderItem
import com.geison.phonereminder.data.ReminderRepository
import com.geison.phonereminder.data.ScheduleSettings
import com.geison.phonereminder.diagnostics.Diagnostics
import com.geison.phonereminder.notifications.NotificationScheduler
import com.geison.phonereminder.notifications.ReminderNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReminderRepository(application)
    private val mutableOpenReminderRequest = MutableStateFlow<String?>(null)

    val state = repository.state
    val openReminderRequest = mutableOpenReminderRequest.asStateFlow()

    fun addReminder(text: String): String? {
        val reminderId = repository.addReminder(text)
        if (reminderId != null) {
            Diagnostics.setKey("reminder_count", state.value.reminders.size)
            NotificationScheduler.scheduleToday(getApplication())
        }
        return reminderId
    }

    fun addReminder(
        text: String,
        notificationsPerWeek: Int,
        notificationsPerDay: Int,
        createdAtEpochMillis: Long,
    ): String? {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) {
            return null
        }

        val safeNotificationsPerDay = notificationsPerDay.coerceIn(1, MAX_NOTIFICATIONS_PER_DAY)
        val safeNotificationsPerWeek = snapWeeklyCount(
            value = notificationsPerWeek,
            notificationsPerDay = safeNotificationsPerDay,
        )
        val reminderId = repository.addReminder(
            text = trimmedText,
            schedule = ScheduleSettings(
                notificationsPerWeek = safeNotificationsPerWeek,
                notificationsPerDay = safeNotificationsPerDay,
            ),
            createdAtEpochMillis = createdAtEpochMillis,
        )
        if (reminderId != null) {
            Diagnostics.setKey("reminder_count", state.value.reminders.size)
            NotificationScheduler.scheduleToday(getApplication())
        }
        return reminderId
    }

    fun deleteReminder(id: String) {
        repository.deleteReminder(id)
        Diagnostics.setKey("reminder_count", state.value.reminders.size)
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

        val safeNotificationsPerDay = notificationsPerDay.coerceIn(1, MAX_NOTIFICATIONS_PER_DAY)
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

    fun updateReminderDay(
        dayOfWeek: DayOfWeek,
        isEnabled: Boolean,
    ) {
        val currentDays = state.value.reminderDays
        val updatedDays = if (isEnabled) {
            currentDays + dayOfWeek
        } else {
            currentDays - dayOfWeek
        }
        repository.updateReminderDays(updatedDays)
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
            Diagnostics.log("export_reminders")
            getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(content)
            } ?: error(getApplication<Application>().getString(R.string.message_file_open_failed))
        }.fold(
            onSuccess = {
                val count = state.value.reminders.size
                Diagnostics.setKey("last_export_reminder_count", count)
                getApplication<Application>().resources.getQuantityString(
                    R.plurals.message_exported_reminders,
                    count,
                    count,
                )
            },
            onFailure = { error ->
                Diagnostics.recordNonFatal(
                    area = "export_failed",
                    throwable = error,
                    keys = mapOf(
                        "reminder_count" to state.value.reminders.size.toString(),
                    ),
                )
                getApplication<Application>().getString(
                    R.string.message_export_failed,
                    error.message ?: getApplication<Application>().getString(R.string.message_unknown_error),
                )
            },
        )
    }

    fun previewImport(uri: Uri): ImportPreviewResult {
        return runCatching {
            Diagnostics.log("import_reminders")
            val content = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: error(getApplication<Application>().getString(R.string.message_file_open_failed))

            ImportPreviewResult.Ready(
                importedState = ReminderExchange.import(content),
                currentReminderCount = state.value.reminders.size,
            )
        }.getOrElse { error ->
            Diagnostics.recordNonFatal(
                area = "import_failed",
                throwable = error,
            )
            ImportPreviewResult.Error(
                message = getApplication<Application>().getString(
                    R.string.message_import_failed,
                    error.message ?: getApplication<Application>().getString(R.string.message_unknown_error),
                ),
            )
        }
    }

    fun importReminders(importedState: AppState): String {
        repository.replaceState(importedState)
        recordImportSuccess(
            mode = "replace",
            importedReminderCount = importedState.reminders.size,
            totalReminderCount = importedState.reminders.size,
        )

        val count = importedState.reminders.size
        return getApplication<Application>().resources.getQuantityString(
            R.plurals.message_imported_reminders,
            count,
            count,
        )
    }

    fun mergeImportedReminders(importedState: AppState): String {
        val mergedState = ReminderImportMerge.merge(
            currentState = state.value,
            importedState = importedState,
        )
        repository.replaceState(mergedState)
        recordImportSuccess(
            mode = "merge",
            importedReminderCount = importedState.reminders.size,
            totalReminderCount = mergedState.reminders.size,
        )

        val count = importedState.reminders.size
        return getApplication<Application>().resources.getQuantityString(
            R.plurals.message_merged_reminders,
            count,
            count,
        )
    }

    private fun recordImportSuccess(
        mode: String,
        importedReminderCount: Int,
        totalReminderCount: Int,
    ) {
        Diagnostics.setKey("reminder_count", totalReminderCount)
        Diagnostics.setKey("last_import_reminder_count", importedReminderCount)
        Diagnostics.setKey("last_import_mode", mode)
        NotificationScheduler.scheduleToday(getApplication())
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

sealed interface ImportPreviewResult {
    data class Ready(
        val importedState: AppState,
        val currentReminderCount: Int,
    ) : ImportPreviewResult

    data class Error(
        val message: String,
    ) : ImportPreviewResult
}
