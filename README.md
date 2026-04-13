# Phone Reminder

Simple Kotlin Android app for saving short life lessons and surfacing them as random local notifications during the day.

## What it does

- Stores reminders locally on the device
- Lets you choose how many times each reminder can appear per week
- Lets you choose a start and end hour
- Picks random times for the current day
- Reschedules after reboot and each new day

## Project setup

1. Install Android Studio with a recent Android SDK and JDK 17.
2. Open this folder as a project.
3. Let Gradle sync and download dependencies.
4. Run the `app` configuration on an Android device or emulator.

## Notes

- Notifications are local to the device. There is no backend or push service.
- The app uses `AlarmManager.setWindow(...)`, so delivery is intentionally approximate within a small time window instead of exact to the minute.
- On Android 13 and newer, the app asks for notification permission on first launch.

## Release build

- Copy `keystore.properties.example` to `keystore.properties`.
- Point `RELEASE_STORE_FILE` to your upload keystore.
- Fill in the store password, key alias, and key password.
- Build the Play bundle with `./gradlew bundleRelease`.
