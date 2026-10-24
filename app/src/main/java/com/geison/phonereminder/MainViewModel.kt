package com.geison.phonereminder

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.geison.phonereminder.R
import com.geison.phonereminder.backup.DriveApiException
import com.geison.phonereminder.backup.DriveAuthorization
import com.geison.phonereminder.backup.GoogleDriveAuthorizationException
import com.geison.phonereminder.backup.GoogleDriveBackupManager
import com.geison.phonereminder.backup.GoogleDriveConsentRequiredException
import com.geison.phonereminder.backup.GoogleDriveFailure
import com.geison.phonereminder.backup.RemoteSyncFile
import com.geison.phonereminder.data.AppState
import com.geison.phonereminder.data.DriveSync
import com.geison.phonereminder.data.MAX_NOTIFICATIONS_PER_DAY
import com.geison.phonereminder.data.ReminderExchange
import com.geison.phonereminder.data.ReminderImportMerge
import com.geison.phonereminder.data.ReminderItem
import com.geison.phonereminder.data.ReminderRepository
import com.geison.phonereminder.data.ReminderStorage
import com.geison.phonereminder.data.ScheduleCadence
import com.geison.phonereminder.data.ScheduleSettings
import com.geison.phonereminder.diagnostics.Diagnostics
import com.geison.phonereminder.notifications.NotificationScheduler
import com.geison.phonereminder.notifications.ReminderNotifier
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReminderRepository(application)
    private val googleDriveManager = GoogleDriveBackupManager(application)
    private val mutableOpenReminderRequest = MutableStateFlow<String?>(null)
    private val mutableGoogleDriveConsentRequest = MutableStateFlow<PendingIntent?>(null)
    private val mutableDrivePhase = MutableStateFlow<DrivePhase>(DrivePhase.NotConnected)
    private val mutableRemoteSnapshot = MutableStateFlow<RemoteSnapshot?>(null)
    private val mutableLastSyncAt = MutableStateFlow(ReminderStorage.loadLastDriveSyncAt(application))
    private var pendingGoogleDriveConsentAction: GoogleDriveAction? = null
    private var lastAutomaticCompareAt = 0L
    private var scheduleNotificationsJob: Job? = null

    val state = repository.state
    val openReminderRequest = mutableOpenReminderRequest.asStateFlow()
    val googleDriveConsentRequest = mutableGoogleDriveConsentRequest.asStateFlow()

    /** Home screen Drive card. Differences are recomputed from the cached Drive copy on every local edit. */
    val driveSyncUiState: StateFlow<DriveSyncUiState> = combine(
        repository.state,
        mutableDrivePhase,
        mutableRemoteSnapshot,
        mutableLastSyncAt,
    ) { local, phase, snapshot, lastSyncAt ->
        val status = when (phase) {
            DrivePhase.NotConnected -> DriveSyncStatus.NotConnected
            DrivePhase.Checking -> DriveSyncStatus.Checking
            DrivePhase.Syncing -> DriveSyncStatus.Syncing
            is DrivePhase.Error -> DriveSyncStatus.Error(phase.message)
            DrivePhase.Ready -> if (snapshot == null) {
                DriveSyncStatus.NotConnected
            } else {
                DriveSyncStatus.Compared(
                    differences = DriveSync.diff(local, snapshot.resolve(local)),
                    hasRemoteFile = snapshot.parsed != null,
                )
            }
        }
        DriveSyncUiState(
            isEnabled = BuildConfig.GOOGLE_DRIVE_BACKUP_ENABLED,
            status = status,
            lastSyncAtEpochMillis = lastSyncAt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DriveSyncUiState(
            isEnabled = BuildConfig.GOOGLE_DRIVE_BACKUP_ENABLED,
            status = DriveSyncStatus.NotConnected,
            lastSyncAtEpochMillis = mutableLastSyncAt.value,
        ),
    )

    enum class GoogleDriveAction { CONNECT, SYNC }

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
        cadence: ScheduleCadence,
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
                cadence = cadence,
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
        cadence: ScheduleCadence,
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
                    cadence = cadence,
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

    /** Called when the app is opened or returns to the foreground: read-only compare, never consent UI. */
    fun onAppForeground() {
        if (!BuildConfig.GOOGLE_DRIVE_BACKUP_ENABLED) {
            return
        }
        repository.reloadFromStorage()
        val now = System.currentTimeMillis()
        if (isDriveBusy() || now - lastAutomaticCompareAt < AUTOMATIC_COMPARE_THROTTLE_MILLIS) {
            return
        }
        lastAutomaticCompareAt = now
        mutableDrivePhase.value = DrivePhase.Checking
        viewModelScope.launch {
            val authorization = runCatching { googleDriveManager.authorize() }.getOrElse { error ->
                handleAutomaticCompareFailure(error)
                return@launch
            }
            when (authorization) {
                is DriveAuthorization.Granted -> performCompare(authorization.accessToken, interactive = false)
                // Never show consent automatically; the card offers a Connect button instead.
                is DriveAuthorization.NeedsConsent -> mutableDrivePhase.value = DrivePhase.NotConnected
            }
        }
    }

    /** "Connect" button: may show the Google consent screen, then compares (read-only). */
    fun connectGoogleDrive() = startInteractiveDriveAction(GoogleDriveAction.CONNECT)

    /** "Sync now" button: two-way merge, then writes the phone and Drive. */
    fun syncGoogleDriveNow() = startInteractiveDriveAction(GoogleDriveAction.SYNC)

    private fun startInteractiveDriveAction(action: GoogleDriveAction) {
        if (!BuildConfig.GOOGLE_DRIVE_BACKUP_ENABLED || isDriveBusy()) {
            return
        }
        mutableDrivePhase.value = if (action == GoogleDriveAction.SYNC) DrivePhase.Syncing else DrivePhase.Checking
        viewModelScope.launch {
            runCatching { googleDriveManager.authorize() }.fold(
                onSuccess = { handleGoogleDriveAuthorization(it, action) },
                onFailure = { error -> finishGoogleDriveFailure(action, "google_drive_authorize_failed", error) },
            )
        }
    }

    /** Called with the result of the consent [android.app.PendingIntent] launched by the UI. */
    fun onGoogleDriveConsentResult(result: ActivityResult) {
        if (!BuildConfig.GOOGLE_DRIVE_BACKUP_ENABLED) {
            return
        }
        val action = pendingGoogleDriveConsentAction
        pendingGoogleDriveConsentAction = null
        mutableGoogleDriveConsentRequest.value = null
        if (action == null) {
            restoreIdlePhase()
            return
        }
        if (result.resultCode != Activity.RESULT_OK) {
            Diagnostics.log("google_drive_consent_cancelled_result_${result.resultCode}")
            restoreIdlePhase()
            return
        }
        runCatching { googleDriveManager.authorizationFromIntent(result.data) }.fold(
            onSuccess = { authorization ->
                if (authorization is DriveAuthorization.Granted) {
                    runGoogleDriveAction(authorization.accessToken, action)
                } else {
                    finishGoogleDriveFailure(
                        action,
                        "google_drive_consent_incomplete",
                        GoogleDriveAuthorizationException("Consent finished without a token."),
                    )
                }
            },
            onFailure = { error -> finishGoogleDriveFailure(action, "google_drive_consent_failed", error) },
        )
    }

    fun onGoogleDriveConsentLaunched() {
        mutableGoogleDriveConsentRequest.value = null
    }

    fun onGoogleDriveConsentLaunchFailed(error: Throwable) {
        val action = pendingGoogleDriveConsentAction ?: GoogleDriveAction.CONNECT
        pendingGoogleDriveConsentAction = null
        mutableGoogleDriveConsentRequest.value = null
        finishGoogleDriveFailure(action, "google_drive_consent_launch_failed", error)
    }

    private fun isDriveBusy(): Boolean {
        val phase = mutableDrivePhase.value
        return phase == DrivePhase.Checking || phase == DrivePhase.Syncing ||
            mutableGoogleDriveConsentRequest.value != null || pendingGoogleDriveConsentAction != null
    }

    private fun restoreIdlePhase() {
        mutableDrivePhase.value = if (mutableRemoteSnapshot.value != null) DrivePhase.Ready else DrivePhase.NotConnected
    }

    private fun handleGoogleDriveAuthorization(authorization: DriveAuthorization, action: GoogleDriveAction) {
        when (authorization) {
            is DriveAuthorization.Granted -> runGoogleDriveAction(authorization.accessToken, action)
            is DriveAuthorization.NeedsConsent -> {
                Diagnostics.log("google_drive_consent_requested")
                pendingGoogleDriveConsentAction = action
                mutableGoogleDriveConsentRequest.value = authorization.pendingIntent
            }
        }
    }

    private fun runGoogleDriveAction(accessToken: String, action: GoogleDriveAction) {
        viewModelScope.launch {
            when (action) {
                GoogleDriveAction.CONNECT -> performCompare(accessToken, interactive = true)
                GoogleDriveAction.SYNC -> performSync(accessToken)
            }
        }
    }

    /** Read-only: downloads the Drive file and caches it; differences are recomputed from it. */
    private suspend fun performCompare(accessToken: String, interactive: Boolean) {
        mutableDrivePhase.value = DrivePhase.Checking
        Diagnostics.log("google_drive_compare_start")
        runCatching { googleDriveManager.fetchSyncFile(accessToken) }.fold(
            onSuccess = { file ->
                mutableRemoteSnapshot.value = RemoteSnapshot.from(file)
                mutableDrivePhase.value = DrivePhase.Ready
            },
            onFailure = { error ->
                if (interactive) {
                    finishGoogleDriveFailure(GoogleDriveAction.CONNECT, "google_drive_compare_failed", error)
                } else {
                    handleAutomaticCompareFailure(error)
                }
            },
        )
    }

    private suspend fun performSync(accessToken: String) {
        mutableDrivePhase.value = DrivePhase.Syncing
        Diagnostics.log("google_drive_sync_start")
        runCatching {
            val remoteFile = googleDriveManager.fetchSyncFile(accessToken)
            val localBefore = state.value
            val now = System.currentTimeMillis()
            val remote = RemoteSnapshot.from(remoteFile).resolve(localBefore)
            val merged = DriveSync.merge(localBefore, remote, now)
            val content = ReminderExchange.export(merged, includeSyncMetadata = true)
            googleDriveManager.backup(accessToken, content)
            Triple(localBefore, merged, content)
        }.fold(
            onSuccess = { (localBefore, merged, content) ->
                val now = System.currentTimeMillis()
                val localNow = state.value
                // Edits made while uploading are kept; the next sync sends them to Drive.
                val finalState = if (localNow == localBefore) merged else DriveSync.merge(localNow, merged, now)
                repository.replaceWithSyncedState(finalState)
                ReminderStorage.saveLastDriveSyncAt(getApplication(), now)
                mutableLastSyncAt.value = now
                mutableRemoteSnapshot.value = RemoteSnapshot(
                    parsed = ReminderExchange.parse(content),
                    legacyTimestampMillis = now,
                )
                mutableDrivePhase.value = DrivePhase.Ready
                Diagnostics.setKey("reminder_count", finalState.reminders.size)
                Diagnostics.setKey("last_google_drive_sync_count", merged.reminders.size)
                scheduleNotifications()
            },
            onFailure = { error ->
                finishGoogleDriveFailure(GoogleDriveAction.SYNC, "google_drive_sync_failed", error)
            },
        )
    }

    private fun handleAutomaticCompareFailure(error: Throwable) {
        when {
            error is CancellationException -> restoreIdlePhase()
            error is GoogleDriveConsentRequiredException -> mutableDrivePhase.value = DrivePhase.NotConnected
            else -> finishGoogleDriveFailure(GoogleDriveAction.CONNECT, "google_drive_auto_compare_failed", error, allowConsent = false)
        }
    }

    private fun finishGoogleDriveFailure(
        action: GoogleDriveAction,
        area: String,
        error: Throwable,
        allowConsent: Boolean = true,
    ) {
        if (error is CancellationException) {
            restoreIdlePhase()
            return
        }
        // A cached grant was revoked mid-call: ask for consent again (only after a button tap).
        if (error is GoogleDriveConsentRequiredException) {
            if (allowConsent) {
                pendingGoogleDriveConsentAction = action
                mutableGoogleDriveConsentRequest.value = error.pendingIntent
            } else {
                mutableDrivePhase.value = DrivePhase.NotConnected
            }
            return
        }
        val statusCode = (error as? ApiException)?.statusCode
        val failure = GoogleDriveFailure.classify(error, statusCode)
        if (failure == GoogleDriveFailure.CANCELLED) {
            restoreIdlePhase()
            return
        }
        // Automatic checks run on every app open; only report failures from button taps.
        if (allowConsent && failure != GoogleDriveFailure.NETWORK) {
            Diagnostics.recordNonFatal(
                area = area,
                throwable = error,
                keys = buildMap {
                    put("failure", failure.name)
                    statusCode?.let { put("status_code", it.toString()) }
                    (error as? DriveApiException)?.let {
                        put("http_code", it.httpCode.toString())
                        put("reason", it.reason.orEmpty())
                    }
                },
            )
        }
        mutableDrivePhase.value = DrivePhase.Error(googleDriveFailureMessage(action, failure, error, statusCode))
    }

    private fun googleDriveFailureMessage(
        action: GoogleDriveAction,
        failure: GoogleDriveFailure,
        error: Throwable,
        statusCode: Int?,
    ): String {
        val app = getApplication<Application>()
        return when (failure) {
            GoogleDriveFailure.CANCELLED -> app.getString(R.string.message_google_drive_sign_in_cancelled)
            GoogleDriveFailure.APP_NOT_REGISTERED ->
                app.getString(R.string.message_google_drive_app_not_registered, statusCode ?: 10)
            GoogleDriveFailure.DRIVE_API_DISABLED -> app.getString(R.string.message_google_drive_api_disabled)
            GoogleDriveFailure.PERMISSION_MISSING -> app.getString(R.string.message_google_drive_auth_required)
            GoogleDriveFailure.NO_BACKUP -> app.getString(R.string.message_google_drive_no_backup_found)
            GoogleDriveFailure.STORAGE_FULL -> app.getString(R.string.message_google_drive_storage_full)
            GoogleDriveFailure.NETWORK -> app.getString(R.string.message_google_drive_network)
            GoogleDriveFailure.UNKNOWN -> {
                val detail = statusCode?.let { app.getString(R.string.message_google_drive_status_code, it) }
                    ?: error.message
                    ?: app.getString(R.string.message_unknown_error)
                app.getString(
                    when (action) {
                        GoogleDriveAction.SYNC -> R.string.message_google_drive_sync_failed
                        GoogleDriveAction.CONNECT -> R.string.message_google_drive_check_failed
                    },
                    detail,
                )
            }
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
        private const val AUTOMATIC_COMPARE_THROTTLE_MILLIS = 30_000L

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

sealed interface DriveSyncStatus {
    data object NotConnected : DriveSyncStatus
    data object Checking : DriveSyncStatus
    data object Syncing : DriveSyncStatus
    data class Compared(
        val differences: DriveSync.Differences,
        val hasRemoteFile: Boolean,
    ) : DriveSyncStatus
    data class Error(val message: String) : DriveSyncStatus
}

data class DriveSyncUiState(
    val isEnabled: Boolean,
    val status: DriveSyncStatus,
    val lastSyncAtEpochMillis: Long?,
)

private sealed interface DrivePhase {
    data object NotConnected : DrivePhase
    data object Checking : DrivePhase
    data object Syncing : DrivePhase
    data object Ready : DrivePhase
    data class Error(val message: String) : DrivePhase
}

/** Last Drive file downloaded; [parsed] is null when Drive has no sync file yet. */
private data class RemoteSnapshot(
    val parsed: ReminderExchange.ParsedExport?,
    val legacyTimestampMillis: Long,
) {
    fun resolve(local: AppState): AppState? = parsed?.let {
        DriveSync.resolveRemote(local, it, legacyTimestampMillis)
    }

    companion object {
        fun from(file: RemoteSyncFile?): RemoteSnapshot = RemoteSnapshot(
            parsed = file?.let { ReminderExchange.parse(it.content) },
            legacyTimestampMillis = file?.modifiedTimeEpochMillis ?: 0L,
        )
    }
}
