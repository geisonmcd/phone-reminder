# Data Safety Notes

Expected Play Console answers based on the current app code and Firebase Crashlytics:

- Personal info: No
- Financial info: No
- Health and fitness: No
- Messages: No
- Photos and videos: No
- Audio files: No
- Files and docs: User-created export/import files only (not collected; stay where the user saves them)
- App activity: No behavior analytics collected. "Other user-generated content" (reminder text and settings) is collected only when the user turns on the optional Google Drive backup (see below)
- App info and performance: Crash logs and diagnostics collected by Firebase Crashlytics
- Web browsing: No
- Device or other IDs: Crashlytics installation UUID collected by Firebase Crashlytics

Collection:
- Reminder content and schedule data are not sent off device unless the user turns on the optional Google Drive backup.
- Google Drive backup (optional, user-initiated): the app uploads a backup file (`Smart Random Reminder Backup.txt`) with reminder text and settings directly from the device to the user's own Google Drive via HTTPS, using the `drive.file` scope (only files the app creates). The developer has no server and never receives this data, but Play treats any transmission off device as collection, so declare it: Other user-generated content, collected, optional, purpose App functionality, encrypted in transit, user can delete it (delete the file in Google Drive or revoke the app's access in their Google Account). The app does not read or store the Google account email or profile.
- Firebase Crashlytics collects crash reports, stack traces, relevant app state, device metadata, app version, and a Crashlytics installation UUID for stability monitoring.
- Developer-defined crash report keys/logs include technical metadata only, such as reminder count, scheduler state, notification permission state, import/export failure state, and app lifecycle breadcrumbs. Do not log reminder text, imported file contents, exported file contents, account identifiers, advertising identifiers, or precise location.

Sharing:
- Crash diagnostics are processed by Firebase Crashlytics for app stability monitoring.
- Reminder content is not shared with third parties. Uploading the backup to the user's own Google Drive is a user-initiated transfer the user expects, which Play exempts from "sharing".

Processing:
- Reminder text and schedule settings are stored locally on device.
- Export files are created only when the user chooses to export reminders.
- Import reads only the file selected by the user.
- Google Drive backup and restore run only when the user requests them; restore downloads only the app's own backup file.

Permissions used:
- `POST_NOTIFICATIONS`
- `RECEIVE_BOOT_COMPLETED`
- `INTERNET`
