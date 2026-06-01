# AGENTS.md

## Project Overview
Phone Reminder - an Android app for scheduling and managing reminders.

## Commands
- Run tests: `./gradlew test`
- Run lint: `./gradlew lintDebug`
- Build: `./gradlew assembleDebug`

## Play Store Deployment
- To deploy a new build to Internal testing, prefer GitHub Actions over the Play Console browser flow.
- Use the `Android Internal Testing Release` workflow, which builds a signed release AAB and uploads it to the Play `internal` track.
- The Play version code must be higher than every previously uploaded version code; do not reuse an existing code.
- Example:
  ```bash
  gh workflow run "Android Internal Testing Release" \
    --repo geisonmcd/phone-reminder \
    -f versionCode=15 \
    -f versionName=1.0.14
  ```
- Before reporting completion, verify the workflow succeeded and the logs show the upload to Google Play completed.

## After every commit
- Update `CHANGELOG.md` with a dated one-liner (e.g. `- 2025-05-29 — Fixed X by doing Y.`).

## Code Conventions
- Kotlin for Android development
- Jetpack Compose for UI
- MVVM architecture with ViewModels
- Follow existing patterns in the codebase for new code
