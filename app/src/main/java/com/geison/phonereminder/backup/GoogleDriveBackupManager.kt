package com.geison.phonereminder.backup

import android.content.Context
import com.geison.phonereminder.R
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDriveBackupManager(private val context: Context) {

    private val driveScope = Scope(DriveScopes.DRIVE_FILE)

    private val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(driveScope)
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent() = signInClient.signInIntent

    fun getAuthorizedAccount(): GoogleSignInAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        return account.takeIf { GoogleSignIn.hasPermissions(it, driveScope) }
    }

    fun signOut() {
        signInClient.signOut()
    }

    suspend fun backup(account: GoogleSignInAccount, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val drive = buildDriveService(account)
                val existing = findBackupFile(drive)
                val media = ByteArrayContent(MIME_TYPE, content.toByteArray(Charsets.UTF_8))
                if (existing != null) {
                    drive.files().update(existing.id, null, media).execute()
                } else {
                    val metadata = com.google.api.services.drive.model.File().apply {
                        name = BACKUP_FILE_NAME
                        mimeType = MIME_TYPE
                    }
                    drive.files().create(metadata, media).setFields("id").execute()
                }
                Unit
            }.recoverCatching { error ->
                throw mapAuthFailure(error)
            }
        }

    suspend fun restore(account: GoogleSignInAccount): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val drive = buildDriveService(account)
                val existing = findBackupFile(drive)
                    ?: throw IllegalStateException(
                        context.getString(R.string.message_google_drive_no_backup_found),
                    )
                val outputStream = java.io.ByteArrayOutputStream()
                drive.files().get(existing.id).executeMediaAndDownloadTo(outputStream)
                outputStream.toString(Charsets.UTF_8.name())
            }.recoverCatching { error ->
                throw mapAuthFailure(error)
            }
        }

    private fun findBackupFile(drive: Drive): com.google.api.services.drive.model.File? {
        val query = "name = '$BACKUP_FILE_NAME' and 'me' in owners and trashed = false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .setPageSize(10)
            .execute()
        return result.files.firstOrNull()
    }

    private fun buildDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE),
        )
        val accountName = account.email
            ?: account.account?.name
            ?: throw IllegalStateException("Signed-in account has no email.")
        credential.selectedAccountName = accountName
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential,
        ).setApplicationName(context.getString(R.string.app_name)).build()
    }

    private fun mapAuthFailure(error: Throwable): Throwable {
        val recoverable = generateSequence(error) { it.cause }
            .firstOrNull {
                it is UserRecoverableAuthIOException || it is UserRecoverableAuthException
            }
        return when (recoverable) {
            is UserRecoverableAuthIOException ->
                GoogleDriveAuthRequiredException(recoverable.intent, recoverable)
            is UserRecoverableAuthException ->
                GoogleDriveAuthRequiredException(recoverable.intent, recoverable)
            else -> error
        }
    }

    companion object {
        const val BACKUP_FILE_NAME = "Smart Random Reminder Backup.txt"
        const val MIME_TYPE = "text/plain"
    }
}

class GoogleDriveAuthRequiredException(
    val recoveryIntent: android.content.Intent?,
    cause: Throwable,
) : Exception(cause.message ?: "Google Drive authorization required.", cause)
