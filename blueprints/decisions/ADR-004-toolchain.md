# ADR-004 — toolchain: collaborator pins adopted over donor-frozen pins

- Status: DECIDED by operator (2026-09-19, applied via MeatBag Pipeline from collaborator box).
- Context: repo froze donor-proven AGP 8.13.2 / Kotlin 2.1.0 / Gradle 8.13. Collaborator "corrected" catalog pins AGP 8.7.2 / Kotlin 2.0.21 + google-services 4.5.0 + Firebase BOM 34.19.0 (KTX merged). Executor initially held frozen pins for lack of ADR; operator then applied the collaborator catalog verbatim on disk.
- Decision: catalog is now SINGLE SOURCE OF TRUTH (camelCase keys; root `build.gradle.kts` pure-alias, legacy string pins removed). Frozen donor pins RETIRED for this project.
- Consequences / risks (DeepSeek retro-review invited):
  1. Studio must download AGP 8.7.2 + Kotlin 2.0.21; `app/build/` was generated under 8.13.2 — expect a full clean re-sync; stale outputs must not be trusted.
  2. BOM 34.19.0 / google-services 4.5.0 availability proven only by successful Studio sync (human pastes result).
  3. `firebase-firestore` (non-KTX) + `FirebaseFirestore.getInstance()` — no KTX dependency anywhere.
- Executor restorations on top of the pasted draft (hygiene, non-negotiable): package stays `com.calldad` (operator order), `testImplementation junit` restored (G1/G2 gates), `versionCode` kept at 2 (avoids device downgrade-install failure), trailing newlines restored.
