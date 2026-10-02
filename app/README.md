# app/ — native Kotlin (two flavors)

Package `com.calldad` (`com.calldad.parent` / `com.calldad.child`), minSdk 26 /
compile-target 35, Compose BOM 2024.10.01. Toolchain truth is
`../gradle/libs.versions.toml` (ADR-004).

Screens: Home / Call / Ptt (Walkie Talkie) / Game / Helper / **Chat** / **Photo** /
**Consent** / Pairing, plus the missed-call callback card and the DataStore call log.

**No Hilt, no Room, no SQLCipher, no OkHttp, no Concentus, no KSP in this build.**
ViewModel factories are hand-written; the call log is DataStore and the thread is
pair-scoped Firestore (ADR-018).

Human opens `C:\Call-Dad` in Android Studio (generates `gradlew` wrapper +
`local.properties`), runs `:app:testParentDebugUnitTest :app:testChildDebugUnitTest`
and `:app:lintParentDebug :app:lintChildDebug` — **the unflavored
`testDebugUnitTest`/`lintDebug` task names do not exist**, flavors rename every
variant task — then installs debug on Moto G.

**None of those tasks compiles the app.** `8f47512` was pushed with a duplicated brace
and the whole gate printed GREEN; `assembleParentDebug` is the only check that has ever
caught a non-compiling tree, and it is operator/ask-gated.

This lane never runs `assemble*|install*|connected*`.