# Data Safety Notes

Expected Play Console answers based on the current app code and Firebase Crashlytics:

- Personal info: No
- Financial info: No
- Health and fitness: No
- Messages: No
- Photos and videos: No
- Audio files: No
- Files and docs: User-created export/import files only
- App activity: No behavior analytics collected
- App info and performance: Crash logs and diagnostics collected by Firebase Crashlytics
- Web browsing: No
- Device or other IDs: Crashlytics installation UUID collected by Firebase Crashlytics

Collection:
- Reminder content and schedule data are not collected or sent off device by the app.
- Firebase Crashlytics collects crash reports, stack traces, relevant app state, device metadata, app version, and a Crashlytics installation UUID for stability monitoring.
- Developer-defined crash report keys/logs include technical metadata only, such as reminder count, scheduler state, notification permission state, import/export failure state, and app lifecycle breadcrumbs. Do not log reminder text, imported file contents, exported file contents, account identifiers, advertising identifiers, or precise location.

Sharing:
- Crash diagnostics are processed by Firebase Crashlytics for app stability monitoring.
- Reminder content is not shared with third parties.

Processing:
- Reminder text and schedule settings are stored locally on device.
- Export files are created only when the user chooses to export reminders.
- Import reads only the file selected by the user.

Permissions used:
- `POST_NOTIFICATIONS`
- `RECEIVE_BOOT_COMPLETED`
- `INTERNET`
