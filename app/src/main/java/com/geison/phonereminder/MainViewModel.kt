package com.geison.phonereminder

import android.app.Application
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.geison.phonereminder.R
import com.geison.phonereminder.backup.GoogleDriveBackupManager
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.DayOfWeek
import java.util.Date

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReminderRepository(application)
    private val googleDriveManager = GoogleDriveBackupManager(application)
    private val mutableOpenReminderRequest = MutableStateFlow<String?>(null)
    private val mutableGoogleDriveMessage = MutableStateFlow<String?>(null)
    private val mutableGoogleDriveRestorePreview = MutableStateFlow<ImportPreviewResult.Ready?>(null)
    private var scheduleNotificationsJob: Job? = null

    val state = repository.state
    val openReminderRequest = mutableOpenReminderRequest.asStateFlow()
    val googleDriveMessage = mutableGoogleDriveMessage.asStateFlow()
    val googleDriveRestorePreview = mutableGoogleDriveRestorePreview.asStateFlow()

    enum class GoogleDriveAction { BACKUP, RESTORE }

    fun addReminder(text: String): String? {
        val reminderId = repository.addReminder(text)
        if (reminderId != null) {
            Diagnostics.setKey("reminder_count", state.value.reminders.size)
            scheduleNotifications()
        }
        return reminderId
    }

    fun addReminder(
        text: String,
        notificationsPerDay: Int,
        reminderDays: Set<DayOfWeek>?,
        createdAtEpochMillis: Long,
    ): String? {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) {
            return null
        }

        val safeNotificationsPerDay = notificationsPerDay.coerceIn(1, MAX_NOTIFICATIONS_PER_DAY)
        val reminderId = repository.addReminder(
            text = trimmedText,
            schedule = ScheduleSettings(
                notificationsPerDay = safeNotificationsPerDay,
                reminderDays = reminderDays,
            ),
            createdAtEpochMillis = createdAtEpochMillis,
        )
        if (reminderId != null) {
            Diagnostics.setKey("reminder_count", state.value.reminders.size)
            scheduleNotifications()
        }
        return reminderId
    }

    fun deleteReminder(id: String) {
        repository.deleteReminder(id)
        Diagnostics.setKey("reminder_count", state.value.reminders.size)
        scheduleNotifications()
    }

    fun findReminder(id: String): ReminderItem? {
        return repository.findReminder(id)
    }

    fun saveReminder(
        reminderId: String,
        text: String,
        notificationsPerDay: Int,
        reminderDays: Set<DayOfWeek>?,
    ) {
        val trimmedText = text.trim()
        if (trimmedText.isEmpty()) {
            return
        }

        val safeNotificationsPerDay = notificationsPerDay.coerceIn(1, MAX_NOTIFICATIONS_PER_DAY)

        repository.updateReminder(reminderId) { current ->
            current.copy(
                text = trimmedText,
                schedule = ScheduleSettings(
                    notificationsPerDay = safeNotificationsPerDay,
                    reminderDays = reminderDays,
                ),
            )
        }
        scheduleNotifications()
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
        scheduleNotifications()
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
        scheduleNotifications()
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
        scheduleNotifications()
    }

    fun getGoogleDriveSignInIntent() = googleDriveManager.getSignInIntent()

    fun getLastSignedInAccount() = if (BuildConfig.GOOGLE_DRIVE_BACKUP_ENABLED) {
        googleDriveManager.getLastSignedInAccount()
    } else {
        null
    }

    fun performGoogleDriveAction(
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount,
        action: GoogleDriveAction,
    ) {
        if (!BuildConfig.GOOGLE_DRIVE_BACKUP_ENABLED) {
            return
        }

        when (action) {
            GoogleDriveAction.BACKUP -> performGoogleDriveBackup(account)
            GoogleDriveAction.RESTORE -> performGoogleDriveRestore(account)
        }
    }

    fun onGoogleDriveSignInResult(result: ActivityResult, action: GoogleDriveAction) {
        if (!BuildConfig.GOOGLE_DRIVE_BACKUP_ENABLED) {
            return
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account == null) {
                mutableGoogleDriveMessage.value = getApplication<Application>().getString(
                    R.string.message_google_drive_sign_in_failed,
                )
                return
            }
            performGoogleDriveAction(account, action)
        } catch (e: ApiException) {
            val message = if (e.statusCode == com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                getApplication<Application>().getString(R.string.message_google_drive_sign_in_cancelled)
            } else {
                getApplication<Application>().getString(
                    R.string.message_google_drive_sign_in_failed_with_code,
                    e.statusCode,
                )
            }
            mutableGoogleDriveMessage.value = message
            Diagnostics.setKey("google_drive_sign_in_status_code", e.statusCode)
            Diagnostics.recordNonFatal(
                area = "google_drive_sign_in_failed",
                throwable = e,
            )
        }
    }

    fun clearGoogleDriveMessage() {
        mutableGoogleDriveMessage.value = null
    }

    fun clearGoogleDriveRestorePreview() {
        mutableGoogleDriveRestorePreview.value = null
    }

    fun importGoogleDriveReminders(importedState: AppState) {
        repository.replaceState(importedState)
        recordImportSuccess(
            mode = "google_drive_replace",
            importedReminderCount = importedState.reminders.size,
            totalReminderCount = importedState.reminders.size,
        )
        val count = importedState.reminders.size
        mutableGoogleDriveMessage.value = getApplication<Application>().resources.getQuantityString(
            R.plurals.message_imported_reminders,
            count,
            count,
        )
    }

    fun mergeGoogleDriveReminders(importedState: AppState) {
        val mergedState = ReminderImportMerge.merge(
            currentState = state.value,
            importedState = importedState,
        )
        repository.replaceState(mergedState)
        recordImportSuccess(
            mode = "google_drive_merge",
            importedReminderCount = importedState.reminders.size,
            totalReminderCount = mergedState.reminders.size,
        )
        val count = importedState.reminders.size
        mutableGoogleDriveMessage.value = getApplication<Application>().resources.getQuantityString(
            R.plurals.message_merged_reminders,
            count,
            count,
        )
    }

    private fun performGoogleDriveBackup(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        viewModelScope.launch {
            val content = ReminderExchange.export(state.value)
            val backupResult = googleDriveManager.backup(account, content)
            mutableGoogleDriveMessage.value = backupResult.fold(
                onSuccess = {
                    val count = state.value.reminders.size
                    val syncedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date())
                    getApplication<Application>().resources.getQuantityString(
                        R.plurals.message_google_drive_backup_status,
                        count,
                        count,
                        syncedAt,
                    )
                },
                onFailure = { error ->
                    Diagnostics.recordNonFatal(
                        area = "google_drive_backup_failed",
                        throwable = error,
                    )
                    getApplication<Application>().getString(
                        R.string.message_google_drive_backup_failed,
                        error.message ?: getApplication<Application>().getString(R.string.message_unknown_error),
                    )
                },
            )
        }
    }

    private fun performGoogleDriveRestore(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        viewModelScope.launch {
            val restoreResult = googleDriveManager.restore(account)
            restoreResult.fold(
                onSuccess = { content ->
                    try {
                        val importedState = ReminderExchange.import(content)
                        mutableGoogleDriveRestorePreview.value = ImportPreviewResult.Ready(
                            importedState = importedState,
                            currentReminderCount = state.value.reminders.size,
                        )
                    } catch (error: Exception) {
                        mutableGoogleDriveMessage.value = getApplication<Application>().getString(
                            R.string.message_google_drive_restore_failed,
                            error.message ?: getApplication<Application>().getString(R.string.message_unknown_error),
                        )
                    }
                },
                onFailure = { error ->
                    Diagnostics.recordNonFatal(
                        area = "google_drive_restore_failed",
                        throwable = error,
                    )
                    mutableGoogleDriveMessage.value = getApplication<Application>().getString(
                        R.string.message_google_drive_restore_failed,
                        error.message ?: getApplication<Application>().getString(R.string.message_unknown_error),
                    )
                },
            )
        }
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
        scheduleNotifications()
    }

    private fun scheduleNotifications() {
        val application = getApplication<Application>()
        scheduleNotificationsJob?.cancel()
        scheduleNotificationsJob = viewModelScope.launch(Dispatchers.IO) {
            NotificationScheduler.scheduleToday(application)
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
