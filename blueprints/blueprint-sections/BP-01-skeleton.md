# BP-01 — Native app skeleton (Phase 1)

> **HISTORICAL (2026-09-24):** this describes the original LAN/UDP donor-port plan. What shipped is Firebase signaling + WebRTC + pair-scoped rooms. Current shape: `AGENTS.md` "Architecture notes", `blueprints/CURRENT_STATE.md` and `blueprints/decisions/ADR-015-pair-rooms.md`.

Goal: Studio-created `app/` that compiles its gates in this lane and installs from Studio by human.

## Human steps (operator in Android Studio)
1. New Project → Native C++? NO — Empty Activity + Compose. Package `com.calldad`, minSdk 26 (ADR-001-B), compile/target 35, Kotlin 2.0.21, AGP 8.7.2, Gradle 8.13, JVM 17 (see docs/setup-android-studio.md; NDK/cmake were not needed and are not part of the frozen toolchain).
2. Deps: **the catalog is the only source of truth — `gradle/libs.versions.toml`** (ADR-004). Compose BOM 2024.10.01, lifecycle 2.8.7, navigation-compose 2.8.4, datastore-preferences 1.1.1. **No Hilt, no Room, no SQLCipher, no KSP, no OkHttp, no Concentus, no core-splashscreen, no Retrofit** in this build. Firebase is not "not yet" — it ships (Firebase BOM 34.19.0, google-services 4.5.0) and needs `app/google-services.json` (gitignored, operator-placed).
3. Install debug on Moto G; paste `adb devices` + launch proof to executor.

## Executor steps (this lane, after human creates app/)
1. Wire Nav (`Home/Call/Chat/Photo/Log`), one ViewModel/screen with `StateFlow`/`SharedFlow`, **hand-written ViewModel factories (no DI framework)**. Persistence is DataStore + pair-scoped Firestore — **there is no database, no entities and no DAOs** (ADR-018).
2. First unit test: `RoutesTest` (route contract, pure-JVM) — LANDED.
3. Gates G1: `:app:testParentDebugUnitTest` / `:app:testChildDebugUnitTest` PASS + `:app:lintParentDebug` / `:app:lintChildDebug` 0 errors + `tools/verify_project.py`. **Note: none of those compile the app** — `assembleParentDebug` is the only check that ever has.

## Out
`app/` skeleton + gates evidence in CURRENT_STATE. No comms yet (BP-02).
