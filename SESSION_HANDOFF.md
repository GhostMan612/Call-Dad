# SESSION_HANDOFF.md — Call-Dad (live state)

> Update every session per RULES.md §4.2. Cold-start entry point after RULES.md.
> Keep Gemini/DeepSeek-readable: deltas + next actions + open decisions. No raw dumps.

## DOCUMENT MAP — cold-start hooks (read top-to-bottom)

| # | File | Holds | When to read |
|---|------|-------|--------------|
| 0 | `AGENTS.md` (root) | Compact ramp: structure, commands, env, architecture | automatic |
| 1 | `RULES.md` | **CANONICAL** operating law | EVERY session, before any edit |
| 2 | THIS FILE | Latest deltas, next actions, open decisions, toolchain notes | EVERY session |
| 3 | `blueprints/CURRENT_STATE.md` | Verified per-file map, known-issue registry, toolchain freeze | before writing code |
| 4 | `blueprints/ROADMAP.md` | Phase tracker (0–5) with gates | when planning/phases |
| 5 | `blueprints/CALL_DAD_MASTER_BLUEPRINT.md` | Frozen product spec (v0.1 target) | before novel features |
| 6 | `blueprints/ARCHITECTURE.md` | UI/comms/data split, storage flow | structural changes |
| 7 | `blueprints/blueprint-sections/BP-*.md` | Executable task slices per phase | task work |
| — | `SPEC_SHEET.md/.json` | v0.1 scope contract | scope questions |

Conflict law: RULES.md > other docs; executable files (`*.gradle.kts`, `AndroidManifest.xml`) > prose.

## Where we are (2026-09-19, first device run AUDITED — Firestore write never completed)

- **Operator run (BLU, single device):** Home → Call Dad; logcat showed Factory→PC→capture→HAVE_LOCAL_OFFER→Local OFFER→GATHERING, then 26s silence, then user hangup (CLOSED cascade + dispose). **`OFFER published` NEVER appeared → per prompt §J, fault is in SignalingClient/Firestore, not WebRTC.**
- **Executor probes (authorized, read-only adb):** BLU online (firestore.googleapis.com ping 0% loss); app installed, no crash, no Firebase exceptions in buffer (buffer rotated; `-s WebRTC:D` filter would have hidden non-WebRTC errors anyway).
- **Ranked hypotheses:** (1) write HUNG (offline at 19:55? wrong-project json?) vs (2) failed fast into Error state with ZERO logging (observability gap — now fixed: `OFFER/ANSWER publish started` markers + `Call failed: <KIND>` in reportError, guardrail-compliant). UI state during the 26s UNKNOWN — operator to confirm (Calling vs Retry card).
- **Single-device ceiling:** no callee exists (Moto G gaming) → ANSWER/CONNECTED impossible regardless; Firestore rules/DB provisioning still unverified.
- **Fix committed next (pending):** observability markers above. Re-test needs UNFILTERED logcat.

## Where we are (2026-09-19, host gates GREEN — operator run, executor recorded)

- **Evidence (operator pasted):** `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` → **BUILD SUCCESSFUL in 2m17s, 33 tasks (31 executed, 2 cached)**. 8/8 host tests pass (Routes 3 + SignalingModels 5); lint clean apart from K2 Kotlin-analysis-API warnings (toolchain noise, pre-existing). Wrapper generation itself also BUILD SUCCESSFUL.
- **Device seen from executor lane (read-only adb):** `7040016025040287 device` = **BLU View 5 (B160V, sdk 34)** — not the Moto G. Moto G remains truth device for sign-off.
- **Gates flipped:** G1, G2-host, G3-host GREEN. Still pending: `:app:assembleDebug` + install + `WebRTC:D` call sequence on device.
- **Answer to operator's question (standing orders):** read-only adb from this lane is YES and already proven above. Installs / `connected*` / instrumented runs stay behind an explicit per-order authorization per RULES §1.5 — and there is no `androidTest` source set in repo yet, so the only device work available is the manual `/smoke` walkthrough (operator taps, pastes observations).

## Where we are (2026-09-19, Phase 3 peer connection landed — executor lane, UNCOMMITTED)

- **Architect prompt executed (4 created, 5 modified, package `com.calldad`):** `webrtc/WebRtcConfig.kt` (Google STUN ×2, 640×480@24), `WebRtcLog.kt` guardrail (fixed-string/enum logging only — KDoc is a standing RULES-§2 exception per ADR-005), `WebRTCClient.kt` (trickle ICE, GATHER_CONTINUALLY, audio+front-camera tracks, Phase 4 renderer hooks), `ui/permissions/CallPermissions.kt`; catalog `webrtc 1.1.0`, module dep, Manifest mic/camera + `required=false` features.
- **Executor fixes (soundness):** (1) prompt's `CallViewModel(application)` + bare `viewModel()` would CRASH on navigation — added `callViewModel()` factory; (2) package rewritten from `com.calldad.app.*`; (3) H.2 Connecting branch verified pre-existing — no-op; no renderers added.
- **Architect's own flag confirmed fixed:** Phase 2 callee re-apply-OFFER bug gone (observation caller-scoped). STUN-only carried as K8 (Phase 4 TURN blocker).
- **Gates:** verify re-run next. Operator runs `:app:assembleDebug` + `testDebugUnitTest`/`lintDebug` + `adb logcat -s WebRTC:D` (expected state sequence in prompt §J; NEVER paste SDP/ICE payloads). `google-services.json` confirmed present on disk (gitignored).

## Where we are (2026-09-19, pipeline applied collaborator box — executor reconciled, UNCOMMITTED)

- **Operator applied collaborator catalog verbatim:** `libs.versions.toml` now camelCase single-source-of-truth (AGP 8.7.2 / Kotlin 2.0.21 / google-services 4.5.0 / BOM 34.19.0 / non-KTX firestore); root `build.gradle.kts` pure-alias; `app/build.gradle.kts` verbatim §3 with `com.calldad` correctly kept.
- **Executor reconciliations:** KTX fix applied (`getInstance()`, 4 dead imports removed incl. `FieldValue`/`QuerySnapshot`); junit restored (gates); versionCode held at 2 (avoids device downgrade-install failure); ADR-004 records the toolchain switch (operator-decided, DeepSeek retro-review invited); freeze + checklist updated.
- **Open risk:** `app/build/` was generated under AGP 8.13.2 — Studio must clean re-sync under 8.7.2; BOM 34.19.0 proven only by sync. Human pastes sync result.
- **Gates:** verify re-run next. Temp `Log.d` + `assembleDebug` from the box are OPERATOR-LOCAL ONLY (never committed by this lane).

## Where we are (2026-09-19, Phase 2 signaling landed — executor lane, UNCOMMITTED)

- **Operator Phase 2 WRITTEN under `com.calldad`:** `data/signaling/SignalingModels.kt` + `SignalingClient.kt` (Firestore `calls/dad_channel` OFFER/ANSWER + ICE trickle, `SignalingFailure` offline mapping), `ui/screens/CallState.kt` (Idle/Connecting/InCall/Error replaces `CallStatus` enum), `CallViewModel` rewire (startCall/answerCall/endCall + remoteDescription/remoteCandidates hand-off for Phase 3), `CallScreen` rewire (layout preserved + Error/Retry card), `SignalingModelsTest` (5 pure-JVM tests).
- **Build deltas:** Firebase BOM 33.5.1 + google-services 4.4.2 + coroutines-play-services; Manifest INTERNET + ACCESS_NETWORK_STATE (RECORD_AUDIO/CAMERA still commented); versionName 0.2.0. **Kept frozen AGP 8.13.2 / Kotlin 2.1.0 — the draft's 8.7.2/2.0.21 downgrade was rejected** (no ADR authorizes it; DeepSeek to confirm). Unused `FieldValue`/`QuerySnapshot` imports dropped for lint.
- **ADR-002 now DECIDED Firebase-for-signaling** (operator directive overrides P2P-first recommendation); sovereign P2P deferred to BP-04. `google-services.json` stays gitignored/verify-banned — operator must place it in `app/` before any device signaling test.
- **Gates:** verify re-run next. Human Studio run needed: `testDebugUnitTest` (Routes + SignalingModels) + `lintDebug` + `google-services.json` placement + device signaling proof. This lane ran no Gradle.

## Where we are (2026-09-19, Phase 1 scaffold landed — executor lane, UNCOMMITTED)

- **Operator Phase 1 scaffold WRITTEN to `app/`:** `com.calldad`, 15 `.kt` (MainActivity, Routes/AppNavHost, Color/Type/Theme, GiantComponents, Home+VM, Call+VM, Ptt+VM, Game, Helper+VM) with Genesis headers prepended per RULES §2, Manifest (portrait, no perms — Phase 2 uncomment block kept), `themes.xml`/`colors.xml` (Manifest `@style/Theme.CallDad` satisfied), `RoutesTest` (pure-JVM), module + root Gradle + `libs.versions.toml` + wrapper props (no `gradlew` binaries — Studio generates on sync).
- **Deltas vs frozen spec (executable truth wins, DeepSeek/Gemini to rule):** package `com.calldad` (was `com.calldad` — SPEC amended); routes Home/Call/**Ptt/Game/Helper** (was Chat/Photo/Log — deferred to BP-03/04); minSdk **26** (ADR-001 DECIDED B); BOM **2024.10.01** (drift from frozen 2024.12.01 — flagged); no Hilt/Room/Hilt yet (BP-02+); placeholders: 1.5s fake connect, `cannedReply()`, PTT mic hooks, WebView hook.
- **Gates:** G0 GREEN (re-run next). G1 PENDING human Studio run (`testDebugUnitTest` + `lintDebug` + Moto G install proof). This lane ran no Gradle (build boundary).
- **GitHub:** repo `https://github.com/GhostMan612/Call-Dad` recorded. Local git NOT yet init (next step this session: init + remote, commit explicit paths, NO push).

## Where we were (2026-09-19, scaffold session — executor lane)

- **Scaffold COMPLETE (uncommitted):** root workflow (`AGENTS/RULES/SESSION_HANDOFF/CLAUDE/README/SPEC_SHEET`), `blueprints/` (MASTER + ROADMAP + CURRENT_STATE + CHECKLIST + CHECKPOINTS + ARCHITECTURE + BP-01..05 + ADR-001..003), `docs/` (5 guides), `.opencode/` agents + commands, `tools/verify_project.py`, `fixtures/`, `assets/`, `app/` placeholder, `.gitignore`, `local.properties.template`, isolated lane `C:\venv-hub\call-dad\`.
- **Env surveyed (read-only, nothing modified outside):** `C:\android` SDK/platforms/build-tools/NDK/cmake/adb/licenses + Studio build + JBR 25 + Moto_G_2025/BLU_View_5 AVDs; `C:\venv-hub` python 3.14.6; six Sovereign-family trees surveyed for workflow + mantle comms donor map (`docs/sovereign-comms-reuse-map.md`).
- **Gates:** `tools/verify_project.py` GREEN (2026-09-19: VERIFY PASS 10 dirs + 28 files). No app code — no unit/lint/device gates yet.
- **Team alignment PENDING:** operator setting up Gemini (R&D) + DeepSeek (architect). Awaiting further instructions after scaffold.

## Next actions (for operator + Gemini + DeepSeek)

1. Operator: review scaffold, confirm app name ("Call Dad" working title) + package (`com.calldad` proposed).
2. DeepSeek (architect): rule on ADR-001 (minSdk 30 vs 26 for old kid tablet), ADR-002 (P2P-first vs Firebase-first v0.1 signaling), ADR-003 (Room+SQLCipher vs plain Room v0.1).
3. Gemini (R&D): validate reuse map (mantle `CallSignalingManager`/`LiveCallSession`/`AudioFrameCipher`/`SovereignImageEngine`/`RendezvousClient+main.go`) + propose video-call approach (CameraX + custom UDP vs WebRTC — WebRTC NOT in donor; needs research).
4. Joint: authorize BP-01 (native app skeleton in Android Studio — human creates `app/` via wizard, executor wires packages/manifest + first unit test).
5. Human device step (later): Studio creates skeleton → installs debug on Moto G → pastes `adb devices` + launch proof → executor records in CURRENT_STATE (no build claims from this lane).

## Open decisions

- D1: App name + launcher label + icon (kid-friendly, big-type). Owner: operator.
- D2: minSdk 30 (donor default) vs 26 (old kid tablet reuse). Owner: DeepSeek (ADR-001).
- D3: v0.1 signaling: sovereign P2P+LAN+rendezvous only (recommended) vs Firebase/FCM assist. Owner: DeepSeek + Gemini (ADR-002). Firebase paid account ready but unused.
- D4: Video: extend `LiveCallSession` UDP pattern with CameraX frames (recommended spike) vs adopt WebRTC (new dep, needs ADR). Owner: Gemini research.
- D5: GitHub repo init + remote: DONE local (`main`, origin `https://github.com/GhostMan612/Call-Dad`, commit `d6399d9`, NO push per RULES §1.4). Push only on operator order. Owner: operator.

## Toolchain notes (2026-09-19, verified read-only)

- SDK `C:\android\sdk`: platforms 24/31/33/34/35/36/36.1/37.0; build-tools 34–37; NDK 27.0.12077973 (donor pin) + 28/30; cmake 3.22.1 (donor pin); adb 37.0.1; licenses accepted.
- Studio `C:\android\Android Studio` AI-261.26222.65.2614.16379836; JBR 25; `JAVA_HOME=C:\android\Android Studio\jbr`.
- Pins (frozen until ADR): AGP 8.13.2 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29 / Gradle 8.13 / JVM 17 / Compose BOM 2024.12.01 / Room 2.6.1 / OkHttp 4.12.0 / CBOR 1.7.3 / Concentus 1.0.2 / CameraX 1.3.4 / ZXing 3.5.3.
- Keystores: only `tacplan-debug.keystore`; no `call-dad` keystore (debug only for now).
- Firebase: paid, zero `google-services.json` (intentional — Phase 4 fallback).
- `gh` / Firebase CLI: not on PATH.
