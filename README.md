# Smart Random Reminder

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

## GitHub Actions release

The repository has two workflows:

- `Android CI`: runs unit tests on pushes to `main` and pull requests.
- `Android Release Bundle`: manually builds a signed Play Store `.aab` and uploads it as a GitHub Actions artifact. Each run asks for `versionCode` and `versionName`; `versionCode` must be higher than every previous Play upload.

Configure these repository secrets before running the release workflow:

- `RELEASE_KEYSTORE_BASE64`: base64-encoded upload keystore file.
- `RELEASE_STORE_PASSWORD`: upload keystore password.
- `RELEASE_KEY_ALIAS`: upload key alias.
- `RELEASE_KEY_PASSWORD`: upload key password.

To generate the keystore secret locally:

```sh
base64 -i path/to/upload-keystore.jks | pbcopy
```

Then open GitHub Actions, run `Android Release Bundle`, enter the next `versionCode`, download the `smart-random-reminder-release-aab` artifact, and upload the `.aab` in Play Console.
