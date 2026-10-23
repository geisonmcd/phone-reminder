package com.geison.phonereminder.backup

import java.io.IOException

/** User-facing categories for Drive backup/restore failures. */
enum class GoogleDriveFailure {
    CANCELLED,

    /** OAuth client for this package + signing certificate SHA-1 is missing (status 10). */
    APP_NOT_REGISTERED,

    /** Drive API is disabled in the Google Cloud project that owns the OAuth client. */
    DRIVE_API_DISABLED,
    PERMISSION_MISSING,
    NO_BACKUP,
    STORAGE_FULL,
    NETWORK,
    UNKNOWN,
    ;

    companion object {
        // com.google.android.gms.common.api.CommonStatusCodes / GoogleSignInStatusCodes values.
        const val STATUS_NETWORK_ERROR = 7
        const val STATUS_DEVELOPER_ERROR = 10
        const val STATUS_CANCELED = 16
        const val STATUS_SIGN_IN_CANCELLED = 12501

        /**
         * @param statusCode the Google Play services ApiException status code, when the failure was one.
         */
        fun classify(error: Throwable, statusCode: Int? = null): GoogleDriveFailure {
            if (statusCode != null) {
                return when (statusCode) {
                    STATUS_CANCELED, STATUS_SIGN_IN_CANCELLED -> CANCELLED
                    STATUS_DEVELOPER_ERROR -> APP_NOT_REGISTERED
                    STATUS_NETWORK_ERROR -> NETWORK
                    else -> UNKNOWN
                }
            }
            val chain = generateSequence(error) { it.cause }.toList()
            chain.firstNotNullOfOrNull { it as? DriveApiException }?.let { driveError ->
                return when {
                    driveError.isApiDisabled -> DRIVE_API_DISABLED
                    driveError.isInsufficientScope || driveError.isUnauthorized -> PERMISSION_MISSING
                    driveError.isStorageQuotaExceeded -> STORAGE_FULL
                    else -> UNKNOWN
                }
            }
            return when {
                chain.any { it is GoogleDriveNoBackupException } -> NO_BACKUP
                chain.any { it is GoogleDriveAuthorizationException || it is GoogleDriveConsentRequiredException } ->
                    PERMISSION_MISSING
                chain.any { it is IOException } -> NETWORK
                else -> UNKNOWN
            }
        }
    }
}
