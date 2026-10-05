# Validation

- Public source APK compiled successfully with `app:assembleDebug`.
- Version 0.0.2 passed all 37 core tests, zero failures/errors, including template population, preservation of recorded sets, duplicate prevention, and the public starter seed privacy check.
- Android lint passed and the APK signature was verified against version 0.0.1's signing certificate.
- Public seed contains 24 built-in exercises, no sessions, and no original personal notes.
- The supplied private training history and original test APK are preserved outside the public repository.
- Native instrumentation tests are included but have not been run in this cloud environment.

Build settings: Android SDK 35, min API 26, Kotlin 2.1.10, AGP 8.9.1, JDK 17.
