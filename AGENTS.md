# AGENTS.md

## Project Overview
Phone Reminder - an Android app for scheduling and managing reminders.

## Commands
- Run tests: `./gradlew test`
- Run lint: `./gradlew lintDebug`
- Build: `./gradlew assembleDebug`

## After every commit
- Update `CHANGELOG.md` with a dated entry summarizing the change. Keep entries concise (one line per change).

## Code Conventions
- Kotlin for Android development
- Jetpack Compose for UI
- MVVM architecture with ViewModels
- Follow existing patterns in the codebase for new code

## TODO

### 1. Fix lint failures
- `app/src/main/java/com/geison/phonereminder/notifications/ReminderNotifier.kt:42` calls `notify()` without checking notification permission. Android 13+ can crash if permission is denied.
- Address all 5 lint errors reported by `lintDebug`.

### 2. Safer import with confirmation dialog
- `app/src/main/java/com/geison/phonereminder/MainViewModel.kt:195` replaces the entire state immediately after import.
- Add a confirmation dialog: "Replace 12 reminders with 8 from this file?"
- Ideally, also provide a merge option.

### 3. Explicit empty-save validation **✓ DONE (2025-05-29)**
- ~~The save FAB in `app/src/main/java/com/geison/phonereminder/ui/ReminderApp.kt:648` only changes color when text is blank, but still allows clicking and silently does nothing.~~
- ~~Disable the FAB or show an inline "Reminder text required" message when text is empty.~~
- FAB click is now a no-op when text is blank; inline "Reminder text is required." message appears below the text field.

### 4. Improve notification permission UX
- `app/src/main/java/com/geison/phonereminder/MainActivity.kt:49` asks for notification permission immediately.
- Show a short in-app rationale first, then request.
- Add a "notifications disabled" indicator in settings with a button to open system settings.

### 5. Better scheduling preview
- The week/day steppers work but the result is not obvious to users.
- Add a preview summary like: "3 reminders/week, max 1/day, between 9:00 and 20:00 on selected days."
