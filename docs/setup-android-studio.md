# Setup: Android Studio native Kotlin (Call-Dad)

> Human runs Studio steps. This lane never builds/installs.

1. Prerequisites: `C:\android\sdk` present (platforms 35/36, build-tools 35–37, NDK 27.0.12077973, cmake 3.22.1, licenses accepted). `JAVA_HOME=C:\android\Android Studio\jbr` (JBR 25). Gradle needs Java 17/21 toolchain — in Studio set Gradle JDK to 17+ (or add `org.gradle.java.home` in `~/.gradle/gradle.properties`, never in repo).
2. BP-01 create: New Project → Empty Activity (Compose). Package `com.calldad`, minSdk 30 (pending ADR-001), compile/target 35. Kotlin 2.1.0, AGP 8.13.2, Gradle wrapper 8.13, KSP 2.1.0-1.0.29. Location: `C:\Call-Dad\app` (create INTO this scaffold; keep root docs).
3. Deps (pins frozen until ADR): Compose BOM 2024.12.01, activity-compose, lifecycle-viewmodel + runtime-compose, Hilt, Room 2.6.1 (KSP), sqlite 2.4.0, SQLCipher 4.5.4 (if ADR-003 = now), coroutines 1.9.0, OkHttp 4.12.0, CBOR 1.7.3, Concentus 1.0.2, CameraX 1.3.4, ZXing 3.5.3, biometric 1.1.0, splashscreen, junit 4.13.2 (unit) + room-testing/coroutines-test (androidTest, human-run).
4. `local.properties`: copy from `..\local.properties.template` (gitignored). `sdk.dir=C\:\\android\\sdk`. Never commit.
5. Gates in Studio terminal (human): `.\gradlew testDebugUnitTest`, `.\gradlew lintDebug`. NEVER `assemble*/install*/connected*` in this lane — human runs installs from Studio UI onto Moto G.
6. Firebase: DO NOT add `google-services.json` in v0.1 (ADR-002 pending). If later authorized, place under `app/` (gitignored) per firebase plan.
