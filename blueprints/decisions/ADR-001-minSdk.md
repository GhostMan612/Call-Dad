# ADR-001 — minSdk 30 vs 26

- Status: DECIDED B — minSdk 26 (2026-09-19, operator Phase 1 scaffold; old-tablet reuse wins).
- Decision: minSdk 26 ships in `app/build.gradle.kts` (Phase 1). Revisit only if scoped-storage/biometric issues force 30.
- Context: donor `android_node` pins minSdk 30 (Android 11+). Kid may reuse an old tablet (26 = Android 8.0, vision/tagger pins). Scoped-storage, biometric, CameraX behaviors differ below 30.
- Options: A) minSdk 30 (donor-faithful, smaller matrix, excludes old tablet). B) minSdk 26 (wider reuse, larger test matrix, permission/FFmpeg-style quirks).
- Recommendation (executor): A for v0.1 speed; revisit B if operator confirms old tablet in fleet.
- Decision: PENDING.
