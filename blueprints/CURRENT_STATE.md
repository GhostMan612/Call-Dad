# CURRENT_STATE.md — Call-Dad verified map

> Updated every session per RULES.md §4.2. Executable truth > prose.

## Toolchain freeze (2026-09-19, verified read-only)

AGP 8.7.2 / Kotlin 2.0.21 (ADR-004, catalog is source of truth) / Gradle 8.13 / JVM 17 / Room 2.6.1 (deferred to BP-02+) / OkHttp 4.12.0 / CBOR 1.7.3 / Concentus 1.0.2 / CameraX 1.3.4 / ZXing 3.5.3 / NDK 27.0.12077973 / cmake 3.22.1. App target: compileSdk 35 / target 35 / minSdk 26 (ADR-001 DECIDED B). Phase 1 scaffold pins Compose BOM 2024.10.01 + nav 2.8.4 + lifecycle 2.8.7 (donor-frozen BOM retired — ADR-004). Phase 2: google-services 4.5.0 + Firebase BOM 34.19.0 (KTX merged, `getInstance()`).

## File map (scaffold session)

| Path | State | Notes |
|------|-------|-------|
| `AGENTS.md` / `RULES.md` / `SESSION_HANDOFF.md` / `CLAUDE.md` / `README.md` | WRITTEN, uncommitted | Vision-pattern workflow, Call-Dad tailored |
| `SPEC_SHEET.md` / `.json` | WRITTEN | v0.1 contract |
| `blueprints/` MASTER/ROADMAP/CURRENT_STATE/CHECKLIST/CHECKPOINTS/ARCHITECTURE | WRITTEN | BP-01..05 + ADR-001..003 |
| `docs/` 5 guides | WRITTEN | setup, devices, firebase-plan, kid-ux, reuse-map |
| `.opencode/` 3 agents + 3 commands | WRITTEN | native-dev, comms-porter, kid-ux-guardian; verify/probe/smoke |
| `tools/verify_project.py` | WRITTEN, PENDING run | stdlib scaffold gate |
| `fixtures/` + `assets/` | PLACEHOLDERS | synthetic only |
| `app/` | PHASE 1 LANDED 2026-09-19 (uncommitted) | `com.calldad`: MainActivity + Routes/AppNavHost + theme(3) + GiantComponents + 4 screens w/ ViewModels + Manifest + themes/colors + `RoutesTest` + module/root Gradle + catalog + wrapper props (no `gradlew` binaries — Studio generates) |
| `app/.../data/signaling/` | PHASE 2 LANDED 2026-09-19 (uncommitted) | `SignalingModels` + `SignalingClient` (Firestore `calls/dad_channel`) + `CallState` + VM rewire + Screen Error/Retry + `SignalingModelsTest` + Firebase Gradle/Manifest |
| `app/.../webrtc/` + `ui/permissions/` | PHASE 3 LANDED (caller leg GREEN on device) | `WebRtcConfig` + `WebRtcLog` guardrail + `WebRTCClient` (Stream 1.3.10, trickle ICE, STUN-only) + `CallPermissions` + VM rewrite + factory fix + mic/camera perms |
| `app/.../ui/components/VideoRenderer.kt` + overlay | PHASE 4 LANDED 2026-09-19 (uncommitted) | `CallState.Incoming`, composable-owned renderers, TURN-sentinel config, nav-arg `call?mode=` + DEBUG QA hook (real-path answer), `CallStateTest` |
| `SPEC_SHEET.json` | AMENDED | package `com.calldad` (Phase 1 truth); routes Home/Call/Ptt/Game/Helper supersede Chat/Photo/Log for Phase 1; v0.2.0 Firebase-signaling-active |
| `C:\venv-hub\call-dad\` | CREATED (empty) | isolated lane |

## Known-issue registry

| ID | Issue | Status |
|----|-------|--------|
| K1 | No `app/` Gradle skeleton yet — human Studio step required | CLOSED (scaffold written) → OPEN human Studio sync + `testDebugUnitTest`/`lintDebug` + Moto G install proof |
| K2 | minSdk 30 vs 26 (old kid tablet?) | DECIDED B (26) per operator scaffold → ADR-001 |
| K3 | Video approach: extend-UDP vs WebRTC | DECIDED WebRTC via Stream fork 1.1.0 → ADR-005; sovereign-UDP path retired |
| K4 | P2P-first vs Firebase-first signaling | DECIDED Firebase-for-signaling (operator Phase 2 directive) → ADR-002; P2P deferred to BP-04 |
| K5 | SQLCipher now vs later | OPEN → ADR-003 |
| K8 | No TURN — symmetric-NAT calls will fail | OPEN Phase 4/5 blocker (provider decision before mobile-data field testing) → ADR-005 |
| K6 | No `call-dad` keystore (debug only) | OPEN → operator provisions later |
| K7 | `gh`/Firebase CLI not on PATH | ACCEPTED (not needed v0.1) |

## Last gates

- G0 scaffold gate: GREEN (2026-09-19: VERIFY PASS 10 dirs + 28 files).
- G1 skeleton: GREEN 2026-09-19 (operator run: BUILD SUCCESSFUL, 33 tasks; `RoutesTest` 3/3).
- G2 signaling host: GREEN same build (`SignalingModelsTest` 5/5; lint 0 errors, K2 warnings only).
- G3-device FULL: GREEN 2026-09-19 (BLU + Moto: CONNECTED both, video + audio both ways lane-witnessed, clean hangup, zero crashes).
- G4 rendering/device: GREEN same evidence (VideoRenderer both ways, overlay, auto-popup, ringtone).
- G5 (Phase 5 code): LANDED 2026-09-19, UNPROVEN — needs Studio sync (auth/messaging/play-services-auth) + functions+rules deploy + Anonymous enable + CALLEE_UID swap-builds + killed-app test.
- G6 (Phase 6 code): LANDED 2026-09-19, UNPROVEN — ptt/ + interlock + 4 host tests; needs Studio sync (code-only) + §K press/release/interlock/boundary matrix.
- G3 peer connection (Phase 3, architect prompt): CODE LANDED 2026-09-19 — `webrtc/` (Config/Log/Client) + `CallPermissions` + VM AndroidViewModel rewrite (callee fix) + factory fix + mic/camera perms; gates PENDING human `:app:assembleDebug` + `testDebugUnitTest`/`lintDebug` + `adb logcat -s WebRTC:D` (this lane ran no Gradle).
