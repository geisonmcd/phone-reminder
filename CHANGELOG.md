# Changelog

- 2026-06-06 — Added a manual production promotion workflow for promoting an existing internal testing version.
- 2026-06-05 — Enabled Google Drive backup for internal and closed testing release builds while keeping production release builds disabled by default.
- 2026-06-05 — Focused the reminder text field automatically when creating a new reminder.
- 2026-06-05 — Added a build-type feature flag so Google Drive backup stays enabled locally and hidden in Play releases.
- 2026-06-05 — Simplified daily reminder scheduling, fixed alarm-limit crashes, and refined config day styling.
- 2025-05-29 — Disabled save FAB when text is blank; added inline "Reminder text is required." validation message.
- 2025-05-29 — Added notification-permission rationale dialog before requesting; added notification-status indicator in config screen with button to open system settings.
- 2025-05-29 — Added schedule preview summary below week/day steppers showing count, max per day, hours, and selected days.
- 2026-05-30 — Updated Play closed-testing release notes for notification UX, schedule preview, and validation changes.
- 2026-05-30 — Made notification posting permission guard explicit; added merge/replace import confirmation and removed completed TODOs.
- 2026-05-31 — Updated closed-testing release notes for safer import merging and notification permission handling.
- 2026-05-31 — Added a GitHub Actions workflow to publish signed builds directly to Play internal testing.
- 2026-05-31 — Documented the internal-testing deployment workflow for future agents.
- 2026-06-01 — Restored richer notification frequency adjustment labels showing the resulting weekly and daily schedule.
- 2026-06-01 — Replaced the launcher icon with a simpler phone-and-bell logo.
- 2026-06-03 — Added Google Drive backup and restore option in the config screen.
- 2026-06-03 — Fixed Play production blocking by updating Android packaging and DataStore for 16 KB page-size compatibility.
- 2026-06-03 — Grouped Google Drive backup/import controls with sync status and restored notification frequency actions.
