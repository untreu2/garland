# Garland v0.0.1-alpha

First alpha release of the Garland Android MVP.

## Included in this release

- native Android MVP for identity import, upload prep, retry, restore, and local document browsing
- dedicated diagnostics screen with recent per-document history and copyable tester reports
- WorkManager-backed pending sync and restore with duplicate-job protection and failure classification
- fake Blossom and relay harness coverage for no-device alpha verification
- stronger manifest validation and wildcard MIME fallback naming for provider flows
- repo-side alpha quality gates covering tests, coverage summary, instrumentation-source compile, lint, and debug build

## Repo-side verification

- `automation/verify_alpha_no_device.sh`
- `cargo test`
- `./gradlew testDebugUnitTest`
- `./gradlew jacocoDebugUnitTestReport`
- `python3 automation/report_android_unit_coverage.py`
- `./gradlew compileDebugAndroidTestKotlin`
- `./gradlew assembleDebug`
- `./gradlew lintDebug`

## Known open gates

- `./gradlew connectedDebugAndroidTest` still needs an emulator or device
- manual device checks in `docs/ALPHA_RELEASE_CHECKLIST.md` are still open

## Tag

- `v0.0.1-alpha`
