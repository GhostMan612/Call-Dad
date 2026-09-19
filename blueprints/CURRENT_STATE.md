# CURRENT_STATE.md — Call-Dad verified map

> Updated every session per RULES.md §4.2. Executable truth > prose.

## Toolchain freeze (2026-09-19, verified read-only)

AGP 8.13.2 / Kotlin 2.1.0 / Gradle 8.13 / JVM 17 / Room 2.6.1 (deferred to BP-02+) / OkHttp 4.12.0 / CBOR 1.7.3 / Concentus 1.0.2 / CameraX 1.3.4 / ZXing 3.5.3 / NDK 27.0.12077973 / cmake 3.22.1. App target: compileSdk 35 / target 35 / minSdk 26 (ADR-001 DECIDED B). Phase 1 scaffold pins Compose BOM 2024.10.01 + nav 2.8.4 + lifecycle 2.8.7 (drift from frozen BOM 2024.12.01 — DeepSeek to rule).

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
| `app/` | PHASE 1 LANDED 2026-09-19 (uncommitted) | `com.calldad.app`: MainActivity + Routes/AppNavHost + theme(3) + GiantComponents + 4 screens w/ ViewModels + Manifest + themes/colors + `RoutesTest` + module/root Gradle + catalog + wrapper props (no `gradlew` binaries — Studio generates) |
| `SPEC_SHEET.json` | AMENDED | package `com.calldad.app` (Phase 1 truth); routes Home/Call/Ptt/Game/Helper supersede Chat/Photo/Log for Phase 1 |
| `C:\venv-hub\call-dad\` | CREATED (empty) | isolated lane |

## Known-issue registry

| ID | Issue | Status |
|----|-------|--------|
| K1 | No `app/` Gradle skeleton yet — human Studio step required | CLOSED (scaffold written) → OPEN human Studio sync + `testDebugUnitTest`/`lintDebug` + Moto G install proof |
| K2 | minSdk 30 vs 26 (old kid tablet?) | DECIDED B (26) per operator scaffold → ADR-001 |
| K3 | Video approach: extend-UDP vs WebRTC | OPEN → Gemini research + ADR |
| K4 | P2P-first vs Firebase-first signaling | OPEN → ADR-002 |
| K5 | SQLCipher now vs later | OPEN → ADR-003 |
| K6 | No `call-dad` keystore (debug only) | OPEN → operator provisions later |
| K7 | `gh`/Firebase CLI not on PATH | ACCEPTED (not needed v0.1) |

## Last gates

- G0 scaffold gate: GREEN (2026-09-19: VERIFY PASS 10 dirs + 28 files).
- G1 skeleton: CODE LANDED, gates PENDING human Studio run (`RoutesTest` + `lintDebug`; this lane never builds). No Hilt/Room yet (BP-02+).
