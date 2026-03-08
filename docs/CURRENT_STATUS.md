# Garland Current Status

## Verified in this repo

- Android unit tests pass with `./gradlew test`
- No-device alpha verification now has a repeatable entry point at `automation/verify_alpha_no_device.sh`
- Debug APK builds with `./gradlew assembleDebug`
- Rust core tests pass with `cargo test`

## Current product shape

- Native Android MVP is in place for identity load, upload prep, retry, restore, and local document browsing
- `DocumentsProvider` support exists for recent items, search, path lookup, write, delete, restore-on-read, image thumbnails, and wildcard MIME fallback naming
- Background sync and restore run through WorkManager with duplicate-job protection and retry classification
- Diagnostics are preserved in `MainActivity` summaries and a dedicated diagnostics screen with recent history and copyable reports
- Manifest validation now rejects duplicate or invalid server entries, and restore-side plan failures now store structured diagnostics

## Open release gates

1. Connected Android instrumentation has not been run on an emulator or device in this environment
2. Provider and diagnostics flows still need real-device validation through the Android document picker and connected tests
3. Manual alpha checks in `docs/ALPHA_RELEASE_CHECKLIST.md` are still open

## Recommended execution order

1. Stand up an Android target with `adb`
2. Run `./gradlew connectedDebugAndroidTest`
3. Run the manual picker and diagnostics checks from `docs/ALPHA_RELEASE_CHECKLIST.md`
4. Work through `docs/ALPHA_RELEASE_CHECKLIST.md`

## Evidence snapshot

- `./gradlew test` -> pass
- `./gradlew compileDebugAndroidTestKotlin` -> pass
- `./gradlew assembleDebug` -> pass
- `cargo test` -> pass
- `adb devices` -> unavailable in this CLI environment (`adb: command not found`)
