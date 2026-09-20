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
- [x] First unit test written (`RoutesTest`, host-side, pure-JVM) — GREEN (operator run, see below)
- [x] Human Studio run GREEN 2026-09-19: `:app:testDebugUnitTest` + `:app:lintDebug` → BUILD SUCCESSFUL, 33 tasks (K2 analysis-API warnings = toolchain noise only). Device install proof still pending.
- [ ] Handoff + CURRENT_STATE updated

## Phase 2 — Signaling (operator directive landed 2026-09-19, ADR-002 DECIDED Firebase)
- [x] `data/signaling/` (`SignalingModels` + `SignalingClient` Firestore `calls/dad_channel`) + `CallState` sealed interface + VM rewire + CallScreen Error/Retry
- [x] Gradle: catalog is single source of truth (ADR-004: AGP 8.7.2 / Kotlin 2.0.21 adopted by operator); Firebase BOM 34.19.0 + google-services 4.5.0 + coroutines-play-services; KTX merged (`getInstance()`); Manifest INTERNET + ACCESS_NETWORK_STATE; versionName 0.2.0 / versionCode 2; package `com.calldad` held; junit restored
- [x] `SignalingModelsTest` (pure-JVM: fromWire leniency, ICE defaults, error-kind contract) — GREEN (same operator run: 8/8 host tests pass)
- [x] Human Studio run GREEN (same build); `google-services.json` confirmed on disk in `app/` (gitignored). Device signaling test pending.
- [ ] Sovereign P2P ports (BP-02 original scope) deferred to BP-04 revisit

## Phase 3 — Peer connection (LANDED, caller leg GREEN on device)
- [x] `webrtc/` (Config/Log/Client, stream-webrtc-android 1.3.10, trickle ICE, STUN-only) + `CallPermissions` + catalog dep + Manifest mic/camera (`required=false`)
- [x] VM AndroidViewModel rewrite (callee fix) + `callViewModel()` factory fix + permission-gated auto-start
- [x] OPERATOR CONSOLE done (Firestore enabled): rebuild → `OFFER published` ~3s BOTH sessions, no UNKNOWN, no crash (PID 17206, lane-verified). Caller leg GREEN.

## Phase 4 — Rendering + incoming overlay (DEVICE-PROVEN 2026-09-19)
- [x] `CallState.Incoming` + `VideoRenderer` + TURN-sentinel config + client deltas + VM + overlay + nav-arg QA hook + `CallStateTest`
- [x] E2E on BLU + Moto: CONNECTED both, video both ways (screenshots lane-witnessed), clean hangup, zero crashes
- [x] Audio confirm (operator ears, both ways, near-zero lag)
- [ ] Carried to Phase 4: TURN provider, renderer wiring, incoming-call overlay

## Phase 5 — Per-call rooms + FCM + lockdown (architect prompt landed 2026-09-19, ADR-007)
- [x] Anonymous auth + channel + FCM receiver + FGS phoneCall + manifest + MainActivity routing + POST_NOTIFICATIONS ask + ic_call
- [x] Signaling rewrite (CallDocument, status machine, ring bridge, own-call registry) + VM rewrite + nav callId routing + TURN BuildConfig + rules + functions
- [ ] Human: Studio sync (new deps) → deploy functions+rules → enable Anonymous sign-in → CALLEE_UID swap-builds → §L matrix (killed-app wakeup, rules proofs, TURN presence-check)

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
