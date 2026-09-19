# BP-01 — Native app skeleton (Phase 1)

Goal: Studio-created `app/` that compiles its gates in this lane and installs from Studio by human.

## Human steps (operator in Android Studio)
1. New Project → Native C++? NO — Empty Activity + Compose. Package `com.calldad`, minSdk per ADR-001 (default 30), compile/target 35, Kotlin 2.1.0, AGP 8.13.2, Gradle 8.13, JVM 17, NDK 27.0.12077973, cmake 3.22.1 (see docs/setup-android-studio.md).
2. Add deps: Compose BOM 2024.12.01, Hilt, Room 2.6.1 (KSP), lifecycle-viewmodel, core-splashscreen, datastore/preferences. No Firebase yet.
3. Install debug on Moto G; paste `adb devices` + launch proof to executor.

## Executor steps (this lane, after human creates app/)
1. Wire Nav (`Home/Call/Chat/Photo/Log`), Hilt modules, Room `CallDadDatabase` (contacts/calls/messages entities + DAOs), one ViewModel/screen with `StateFlow`/`SharedFlow`.
2. First unit test: `CallUiStateTest` (giant-button state + allowlist=DAD only) + DAO in-memory test.
3. Gates G1: `testDebugUnitTest` PASS + `lintDebug` 0 errors + verify script.

## Out
`app/` skeleton + gates evidence in CURRENT_STATE. No comms yet (BP-02).
