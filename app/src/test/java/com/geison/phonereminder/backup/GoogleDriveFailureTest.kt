package com.geison.phonereminder.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class GoogleDriveFailureTest {
    @Test
    fun developerErrorMeansOAuthClientNotRegistered() {
        assertEquals(
            GoogleDriveFailure.APP_NOT_REGISTERED,
            GoogleDriveFailure.classify(RuntimeException("10: "), statusCode = 10),
        )
    }

    @Test
    fun cancelStatusCodesAreCancelled() {
        assertEquals(GoogleDriveFailure.CANCELLED, GoogleDriveFailure.classify(RuntimeException(), 16))
        assertEquals(GoogleDriveFailure.CANCELLED, GoogleDriveFailure.classify(RuntimeException(), 12501))
    }

    @Test
    fun driveErrorsAreClassified() {
        assertEquals(
            GoogleDriveFailure.PERMISSION_MISSING,
            GoogleDriveFailure.classify(DriveApiException(403, "insufficientPermissions", "scope")),
        )
        assertEquals(
            GoogleDriveFailure.STORAGE_FULL,
            GoogleDriveFailure.classify(DriveApiException(403, "storageQuotaExceeded", "full")),
        )
        assertEquals(
            GoogleDriveFailure.DRIVE_API_DISABLED,
            GoogleDriveFailure.classify(IllegalStateException("wrapped", DriveApiException(403, "SERVICE_DISABLED", "off"))),
        )
    }

    @Test
    fun missingBackupAndNetworkAreClassified() {
        assertEquals(GoogleDriveFailure.NO_BACKUP, GoogleDriveFailure.classify(GoogleDriveNoBackupException()))
        assertEquals(GoogleDriveFailure.NETWORK, GoogleDriveFailure.classify(UnknownHostException("www.googleapis.com")))
        assertEquals(GoogleDriveFailure.NETWORK, GoogleDriveFailure.classify(IOException("timeout")))
        assertEquals(GoogleDriveFailure.UNKNOWN, GoogleDriveFailure.classify(IllegalStateException("boom")))
    }
}
