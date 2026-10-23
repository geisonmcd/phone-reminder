# Google Drive backup talks to the Drive REST API directly (DriveBackupClient) with
# kotlinx.serialization JSON trees and an AuthorizationClient access token, so no
# reflection-based Google API client keep rules are needed anymore.
-dontwarn com.google.android.apps.common.proguard.**
