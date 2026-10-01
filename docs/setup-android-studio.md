# Setup: Android Studio native Kotlin (Call-Dad)

> Human runs Studio steps. This lane never builds/installs.

1. Prerequisites: `C:\android\sdk` present (platforms 35/36, build-tools 35–37, NDK 27.0.12077973, cmake 3.22.1, licenses accepted). `JAVA_HOME=C:\android\Android Studio\jbr` (JBR 25). Gradle needs Java 17/21 toolchain — in Studio set Gradle JDK to 17+ (or add `org.gradle.java.home` in `~/.gradle/gradle.properties`, never in repo).
2. BP-01 create: New Project → Empty Activity (Compose). Package `com.calldad`, minSdk 26 (ADR-001-B), compile/target 35. Kotlin 2.0.21, AGP 8.7.2, Gradle wrapper 8.13. Location: `C:\Call-Dad\app` (create INTO this scaffold; keep root docs).
   **This step is history — the app exists.** Read it for the Studio workflow, not as instructions. `app/build/` was first generated under AGP 8.13.2; expect a full clean re-sync under 8.7.2 and distrust stale outputs (ADR-004).
3. Deps: **the catalog is the only source of truth — `gradle/libs.versions.toml`.** Compose BOM 2024.10.01, activity-compose 1.9.3, lifecycle 2.8.7, navigation-compose 2.8.4, coroutines 1.10.2, Firebase BOM 34.19.0, `io.getstream:stream-webrtc-android:1.3.10`, CameraX 1.4.2 (uniform), ML Kit barcode 18.3.1 (unbundled), ZXing 3.5.3, DataStore 1.1.1, junit 4.13.2.
   **There is no Hilt, no Room, no SQLCipher, no OkHttp, no Concentus, no KSP** in this build. If a doc or a Studio template offers them, that doc is stale. Deps are hand-written: no annotation processor, so no KSP/annotation-processing setup step exists.
4. `local.properties`: copy from `..\local.properties.template` (gitignored). `sdk.dir=C\:\\android\\sdk`. Never commit.
5. Gates in Studio terminal (repo root): `.\gradlew :app:testParentDebugUnitTest :app:testChildDebugUnitTest :app:lintParentDebug :app:lintChildDebug`. NEVER `assemble*/install*/connected*` in this lane — human runs installs from Studio UI onto Moto G.
6. Firebase: DO NOT add `google-services.json` in v0.1 (ADR-002 pending). If later authorized, place under `app/` (gitignored) per firebase plan.
