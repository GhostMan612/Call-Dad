# CHECKLIST.md — Call-Dad tick list

> Tick per RULES.md §4.2. Ticks follow CHECKPOINTS.md evidence.

## Phase 0 — Scaffold
- [x] Directory tree created
- [x] Root workflow docs (AGENTS/RULES/HANDOFF/CLAUDE/README/SPEC)
- [x] Blueprints (MASTER/ROADMAP/CURRENT/ARCH + BP-01..05 + ADR-001..003)
- [x] Docs (5 guides) + agents/commands + verify script + fixtures/assets placeholders
- [x] `tools/verify_project.py` GREEN (2026-09-19: VERIFY PASS 10 dirs + 28 files)
- [x] Commit by explicit path (2026-09-19: local `d6399d9`, no push — RULES §1.4)

## Phase 1 — Skeleton (BP-01, operator scaffold landed 2026-09-19)
- [x] `app/` scaffold written (`com.calldad`, minSdk 26 per ADR-001-B, compile/target 35)
- [x] Compose Nav (Home/Call/Ptt/Game/Helper) + theme + giant components + 4 ViewModels + Manifest + themes.xml
- [x] First unit test written (`RoutesTest`, host-side, pure-JVM) — GREEN pending human Studio run
- [ ] Human Studio run: `testDebugUnitTest` + `lintDebug` + Moto G install proof (this lane never builds)
- [ ] Handoff + CURRENT_STATE updated

## Phase 2 — Signaling (operator directive landed 2026-09-19, ADR-002 DECIDED Firebase)
- [x] `data/signaling/` (`SignalingModels` + `SignalingClient` Firestore `calls/dad_channel`) + `CallState` sealed interface + VM rewire + CallScreen Error/Retry
- [x] Gradle: Firebase BOM 33.5.1 + google-services 4.4.2 + coroutines-play-services; Manifest INTERNET + ACCESS_NETWORK_STATE; versionName 0.2.0 (toolchain kept at frozen AGP 8.13.2 / Kotlin 2.1.0 — draft downgrade rejected)
- [x] `SignalingModelsTest` (pure-JVM: fromWire leniency, ICE defaults, error-kind contract) — GREEN pending human Studio run
- [ ] Human Studio run: `testDebugUnitTest` (Routes + SignalingModels) + `lintDebug`; place `google-services.json` in `app/` (gitignored) for device signaling test
- [ ] Sovereign P2P ports (BP-02 original scope) deferred to BP-04 revisit

## Phase 3 — Chat + remote (BP-03)
- [ ] `SovereignCommsEngine` + receipts + voice memo
- [ ] `RendezvousClient` + relay deploy; remote matrix proof (human)

## Phase 4 — Photo + video (BP-04)
- [ ] `SovereignImageEngine` photo E2E + receipt
- [ ] Video spike decision (ADR) + LAN video proof (human)
- [ ] Offline/direct-mode matrix (human)

## Phase 5 — Hardening (BP-05)
- [ ] Parent gate + consent cert + kill switch
- [ ] SQLCipher (per ADR-003), kid-UX + no-escape audits
- [ ] v0.1 device proof on Moto G + BLU View 5 (human)
