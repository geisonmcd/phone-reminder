package com.geison.phonereminder.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Outcome of asking Google Play services for a Drive access token. */
sealed interface DriveAuthorization {
    data class Granted(val accessToken: String) : DriveAuthorization

    /** The user must approve the Drive permission (account picker + consent screen). */
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthorization
}

/**
 * Google Drive backup/restore built on the current Identity AuthorizationClient API.
 *
 * The legacy GoogleSignIn + GoogleAccountCredential flow is deprecated and, on Android 8+,
 * `GoogleAccountCredential.setSelectedAccountName` silently drops the account when it is not
 * visible through AccountManager, so token requests failed after a "successful" sign-in.
 */
class GoogleDriveBackupManager(
    context: Context,
    private val driveClient: DriveBackupClient = DriveBackupClient(),
) {
    private val appContext = context.applicationContext
    private val authorizationClient: AuthorizationClient by lazy {
        Identity.getAuthorizationClient(appContext)
    }

    private val authorizationRequest: AuthorizationRequest by lazy {
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
    }

    /** Silently returns a token when the user already granted access; otherwise a consent intent. */
    suspend fun authorize(): DriveAuthorization {
        return authorizationClient.authorize(authorizationRequest).await().toDriveAuthorization()
    }

    /** Parses the result of the consent [PendingIntent]. Throws ApiException on failure/cancel. */
    fun authorizationFromIntent(data: Intent?): DriveAuthorization {
        return authorizationClient.getAuthorizationResultFromIntent(data).toDriveAuthorization()
    }

    suspend fun backup(accessToken: String, content: String): String = withTokenRefresh(accessToken) { token ->
        driveClient.uploadBackup(
            accessToken = token,
            fileName = BACKUP_FILE_NAME,
            mimeType = MIME_TYPE,
            content = content.toByteArray(Charsets.UTF_8),
        )
    }

    suspend fun restore(accessToken: String): String = withTokenRefresh(accessToken) { token ->
        val file = driveClient.findBackupFile(token, BACKUP_FILE_NAME)
            ?: throw GoogleDriveNoBackupException()
        driveClient.download(token, file.id).toString(Charsets.UTF_8)
    }

    /** Read-only: downloads the sync file, or returns null when Drive has no file yet. */
    suspend fun fetchSyncFile(accessToken: String): RemoteSyncFile? = withTokenRefresh(accessToken) { token ->
        val file = driveClient.findBackupFile(token, BACKUP_FILE_NAME) ?: return@withTokenRefresh null
        RemoteSyncFile(
            content = driveClient.download(token, file.id).toString(Charsets.UTF_8),
            modifiedTimeEpochMillis = file.modifiedTime?.let { raw ->
                runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
            },
        )
    }

    private suspend fun <T> withTokenRefresh(accessToken: String, block: (String) -> T): T {
        return withContext(Dispatchers.IO) {
            DriveTokenRetry.run(
                initialToken = accessToken,
                refreshToken = { staleToken -> refreshToken(staleToken) },
                block = block,
            )
        }
    }

    /** Drops a rejected (expired/revoked) token from the cache and silently asks for a new one. */
    private suspend fun refreshToken(staleToken: String): String? {
        runCatching {
            authorizationClient.clearToken(
                ClearTokenRequest.builder().setToken(staleToken).build(),
            ).await()
        }
        return when (val auth = authorize()) {
            is DriveAuthorization.Granted -> auth.accessToken.takeIf { it != staleToken }
            is DriveAuthorization.NeedsConsent -> throw GoogleDriveConsentRequiredException(auth.pendingIntent)
        }
    }

    private fun AuthorizationResult.toDriveAuthorization(): DriveAuthorization {
        val pendingIntent = pendingIntent
        if (hasResolution() && pendingIntent != null) {
            return DriveAuthorization.NeedsConsent(pendingIntent)
        }
        val token = accessToken
        if (token.isNullOrBlank() || DRIVE_FILE_SCOPE !in grantedScopes.orEmpty()) {
            throw GoogleDriveAuthorizationException(
                "Google did not grant Drive access (scopes: ${grantedScopes.orEmpty().joinToString()}).",
            )
        }
        return DriveAuthorization.Granted(token)
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val BACKUP_FILE_NAME = "Smart Random Reminder Backup.txt"
        const val MIME_TYPE = "text/plain"
    }
}

/** Runs a Drive call and retries exactly once with a fresh token when Drive answers 401. */
object DriveTokenRetry {
    suspend fun <T> run(
        initialToken: String,
        refreshToken: suspend (staleToken: String) -> String?,
        block: (String) -> T,
    ): T {
        return try {
            block(initialToken)
        } catch (error: DriveApiException) {
            if (!error.isUnauthorized) throw error
            val freshToken = refreshToken(initialToken) ?: throw error
            block(freshToken)
        }
    }
}

data class RemoteSyncFile(
    val content: String,
    val modifiedTimeEpochMillis: Long?,
)

class GoogleDriveNoBackupException : Exception("No backup found in Google Drive.")

class GoogleDriveAuthorizationException(message: String) : Exception(message)

class GoogleDriveConsentRequiredException(val pendingIntent: PendingIntent) :
    Exception("Google Drive permission must be granted again.")

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val exception = task.exception
        when {
            exception != null -> continuation.resumeWithException(exception)
            task.isCanceled -> continuation.cancel()
            else -> continuation.resume(task.result)
        }
    }
}
