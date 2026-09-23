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

## Phase 6 — PTT subsystem (architect prompt landed 2026-09-19, ADR-008)
- [x] ptt/ (engine contract, simulated default, reflective adapter, focus+haptics, VM+factory+shared accessor) + screen replacement + interlock + perms
- [x] Host-test infra + 4 engine tests (no device needed)
- [ ] Human: Studio sync (code-only, no new deps) → §K matrix (press/release, drag-off, in-call interlock, boundary grep for com.sovereign imports)

## Phase 7 — Game sync over data channel (architect prompt landed 2026-09-20, ADR-009)
- [x] game_sync channel (create/accept, copy-before-emit, 1KB cap, cleanup) + bridge + hardened WebView + game.html + shared VM accessor
- [ ] Human: Studio sync (androidx.webkit) → build → TWO-device TAP sync + escaping test + guardrail log audit

## Phase 8 — Tic-tac-toe + resilience (architect prompt landed 2026-09-20, ADR-010)
- [x] Debounce machine + restartIce + updateOffer + callee offer-watcher + auto-reconnect + banner + full game + PiP card + media-overlay
- [ ] Human: Studio sync (no new deps) → build → §H matrix (game both ways, simultaneous-tap race, PiP visible, Wi-Fi toggle recovery, clean logcat)

## Phase 11 — Static rooms + topic wakeup (architect prompt landed 2026-09-20, ADR-013)
- [x] Static family_channel + seq + status + structured rules + topic FCM + foreground-first FGS + callee observation (role gates + child-only sub REJECTED)
- [ ] Human: Studio sync (no new deps) → deploy rules+functions (REPLACES old rules) → §G matrix (rules proofs, 3× calls, restart recovery, clean logcat)

## Phase 10 — Flavors + games hub + hardening (architect prompt landed 2026-09-20, ADR-012)
- [x] parent/child flavors (blue/pink, APP_THEME, dynamic off) + full game hub (c4 bounds guard) + camera toggle verified
- [ ] BLOCKER FIRST: console — register com.calldad.parent + com.calldad.child, merged google-services.json (builds fail until then)
- [ ] Human: assembleParentDebug + assembleChildDebug → side-by-side install → blue/pink check → feature colors → c4 bounds torture (99/-1/banana) → clean logcat

## Phase 9 — Ringtone + multi-TURN + games hub + voice bot (architect prompt landed 2026-09-20, ADR-011)
- [x] TURN_URLS + audio-mode set/reset + single-ringer + voice helper (STT→bot→TTS) + game hub + host-tested bot
- [ ] Human: Studio sync (no new deps) → TURN lines or empty → §K matrix (looping ringtone, vibration, jokes, airplane STT, multi-game sync, clean logcat)

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

## Contracts 2–6 — Signaling rebuild → pairing → reliability (landed 2026-09-21/22, pushed)
- [x] C2&3: 7-state CallState + per-call rooms + topic wakeup + wakelock + strings (Mama/Dad)
- [x] Legacy cleanup: duplicate CallDocument deleted, seq-tracked SDP, Home listener restored, try/catch call launches, ICE trickle, setup timeouts, instruments, caller-label fix, stale-track crash guard, SecurePeerStore
- [x] C4: CameraX 1.4.2 + unbundled ML Kit 18.3.1 + ZXing pairing (QR both ways, DataStore persistence)
- [x] C5: PAIRING route + gear + backup rules both API ranges + allowBackup false + camera unbind
- [x] Timer fix: elapsed timer on Connected (was frozen at 00:00)
- [x] Operator-run clean assembleParentDebug+assembleChildDebug BUILD SUCCESSFUL; first E2E call GREEN (offer/answer live)
- [ ] Human: fresh-install both flavors → pair both ways → strict one-caller test → hangup test (observer-guard confirmation)

## Contract 7 — Stale takeover + mutual pairing + debt (code landed, uncommitted)
- [x] Heartbeat (120s batch: room updatedAt + pairings presence) + stale-CONNECTED takeover (20-min threshold + peer-unreachability guard) + hasPendingWrites guard
- [x] Mutual handshake (QR-derived nonce, presence docs, 30s peer wait, 1.5s hold) + pairings rules stanza
- [x] CallStateTest rewritten (7-state fromDocument); CallDocumentTest deleted (model retired); deprecation suppressions + FID TODO
- [x] ADR-014 (pairing) + LESSONS_LEARNED.md + CURRENT_STATE/CHECKLIST updated
- [ ] Human: deploy pairings rules (console — new stanza, lane cannot deploy) → assemble both → takeover matrix (§2) + handshake matrix (§3) + heartbeat check (§4)
