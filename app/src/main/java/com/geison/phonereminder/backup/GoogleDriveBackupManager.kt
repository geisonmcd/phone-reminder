package com.geison.phonereminder.backup

import android.content.Context
import com.geison.phonereminder.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDriveBackupManager(private val context: Context) {

    private val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent() = signInClient.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

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
            }
        }

    private fun findBackupFile(drive: Drive): com.google.api.services.drive.model.File? {
        val query = "name = '$BACKUP_FILE_NAME' and 'me' in owners and trashed = false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .setPageSize(1)
            .execute()
        return result.files.firstOrNull()
    }

    private fun buildDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE),
        )
        val accountName = account.account
            ?: throw IllegalStateException("Signed-in account has no Android account.")
        credential.selectedAccount = accountName
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential,
        ).setApplicationName(context.getString(R.string.app_name)).build()
    }

    companion object {
        const val BACKUP_FILE_NAME = "Smart Random Reminder Backup.txt"
        const val MIME_TYPE = "text/plain"
    }
}
