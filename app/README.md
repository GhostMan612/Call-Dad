# app/ — native Kotlin Phase 1 scaffold (BP-01 landed, operator-provided)

Package `com.calldad`, minSdk 26 / compile-target 35, Compose BOM 2024.10.01.
Screens: Home (2x2 giant cards) / Call (placeholder handshake) / Ptt (Walkie Talkie) / Game (WebView placeholder + escape hatch) / Helper (Quick-Ask chat placeholder).
Human opens `C:\Call-Dad` in Android Studio (generates `gradlew` wrapper + `local.properties`), runs `testDebugUnitTest` + `lintDebug`, installs debug on Moto G.
This lane never runs `assemble*|install*|connected*`.
