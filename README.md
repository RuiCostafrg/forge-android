# Forge for Android

A native Kotlin / Jetpack Compose gym tracker that stores everything on the device.
English interface, kilograms, Sunday-first calendars. Android 8.0+ (API 26).

## Features

- Overview with workout suggestions, overrides, draft resume, weekly summaries, and interactive exercise charts.
- Session journal with a Sunday-first calendar, search, and history/review/draft filters.
- Logging of kg/reps/sets, warm-up flags, session/exercise/set notes, training maxima, previous weights, and a persistent rest timer.
- Session dates and types, custom exercise library, reordering, finish/reopen, and deletion.
- Editable seven-day Push/Pull/Legs/Upper/Lower program, including rest days.
- JSON export and confirmed restore, previous valid revision recovery, and conservative training-note parsing.

No accounts, Internet permission, analytics, cloud sync, or API keys.
Data is saved by a background writer to a transactional SQLite snapshot with the previous valid revision retained.
Corrupt startup data blocks edits until recovery or restore, rather than silently resetting.
Automatic cloud/device-transfer backup is excluded. Export JSON before uninstalling.

## Build

Open the project in Android Studio with JDK 17 and Android SDK 35.
Android Studio can create `local.properties` with your SDK path.

```sh
./gradlew app:testDebugUnitTest app:assembleDebug app:lintDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
The build needs Internet access to download dependencies; the installed app works offline.
For native smoke tests on an emulator or connected device:

```sh
./gradlew app:connectedDebugAndroidTest
```

## Starter data and personal history

This public source starts with an empty history and a default weekly program. It contains no personal training records.
`TrainingHistory.md` contains only synthetic examples used by importer tests.
To deliberately bundle your own notes locally, replace that file and regenerate the seed:

```sh
./gradlew app:generateSeed
./gradlew app:testDebugUnitTest app:assembleDebug
```

Personal notes and generated history should stay outside a public repository.
The seed loads only on first launch; normal app updates preserve existing local data.

## Review and metrics

All imported note blocks require review. Partial dates never receive a guessed year.
Check original notes, correct the date and sets, then confirm the entry for calendar/totals.
Explicit notation such as `8x44` and `3x5x120` is parsed; ambiguous values remain notes.
Scheme weights from `5/3/1`, `3/3/3`, and `5/5/5` are training maxima, separate from lifted weights.
Imported charts explicitly follow note order; dated charts and calendar totals use confirmed finished sessions.
Working volume excludes warm-ups and incomplete sets. Bodyweight uses zero external load.
Estimated 1RM uses Epley for 1–12 reps, with a single rep using its actual weight.

Backups use `forge-android-backup` schema 1. iOS Swift archives use a different format.
The rest timer persists its deadline but does not issue background notifications.

## Validation

The original Android build passed 28 core tests, lint, and APK signature verification.
The public-source build passed 29 core tests, including a starter dataset privacy check.
Native instrumentation tests are included; device UI execution has not been completed in the cloud.
