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

## Where we are (2026-09-19, hangup SIGSEGV root-caused — fix committed, needs rebuild)

- **Tombstone proof (BLU, every hangup):** `VideoTrack.removeSink` → libjingle SIGSEGV from `VideoRenderer onDispose`. Race: endCall() disposed native tracks while composables still held sinks; navigation-pop disposal then touched freed memory. Fix: endCall no longer disposes — disposal only in onCleared (composition gone first). No FATALs since fix exists yet — rebuild + hangup test PENDING.
- **Lock-screen behavior (accepted, Phase 5 polish):** connection persists, camera pauses on lock, resumes after unlock. No action now.
- **Audio:** operator-confirmed both ways. Quality: near-zero lag, not choppy. Phase 4 NOT closed until hangup-clean rebuild passes.

## Where we are (2026-09-19, FULL E2E VIDEO CALL GREEN — both phones, lane-witnessed)

- **The call (21:40):** BLU caller → Moto QA-Answer → OFFER/ANSWER exchanged clean → **PeerConnectionState + ICE CONNECTED on BOTH**. No clobbering, no UNKNOWN, no crash.
- **Lane-witnessed screenshots (in `C:\venv-hub\call-dad\`, NEVER repo):** BLU shows Moto's feed full-screen + own PiP + 00:25 timer; Moto shows own PiP + BLU feed (aimed at ceiling) + 00:55 timer. Video flows both ways; controls correct on both.
- **Earlier FAILED session explained:** simultaneous callers clobbered the single room (stale SDP pair) — choreography + wipe/poll fixes resolved it. Single-room clobbering still must go before real use (Phase 5 per-call rooms).
- **Still unverified:** AUDIO both ways (operator ear-check needed); TURN/symmetric-NAT (K8); Firestore rules still dev-open; no call-history/ringtone.

## Where we are (2026-09-19, stale-offer immunity — executor lane, UNCOMMITTED)

- **Lane read the live room (REST):** it holds an OFFER right now — Moto's "still ringing" is a genuine live offer (or an abandoned one; indistinguishable without timestamps — hence this fix).
- **Fix:** offers carry `createdAt`; listener + fetch ignore anything >60s old or unstamped. Abandoned rings now die on their own instead of haunting the phones. Per-call rooms still the real answer (Phase 5).
- **Retest choreography (strict one-caller-at-a-time):** hang up BOTH phones first (clears room) → BLU calls and waits → Moto answers within a minute. Simultaneous calling still clobbers — don't.

## Where we are (2026-09-20, Phase 9 landed — executor lane, UNCOMMITTED)

- **Architect prompt executed with 6 recorded deviations (ADR-011):** TURN_URLS multi-URL + alias, audio-mode set/reset, single-ringer doctrine (overlay player removed), answer-stops-ring, shared Helper VM (same crash class as 3/6), keyword order fix. KeywordBot host-tested (6 tests).
- **Needs operator:** Studio sync (no new deps) → provision TURN_URLS or leave empty → §K matrix: ringtone loops post-call, vibration repeats, keyword jokes, airplane-mode STT (API 31+), multi-game sync, guardrail audit.

## Where we are (2026-09-20, Phase 10 landed — BLOCKED on console step, UNCOMMITTED)

- **Architect prompt executed (ADR-012):** parent/child flavors (blue/pink, APP_THEME-gated, dynamicColor never on), feature colors preserved, child keeps "Call of Daddy", full 3-game hub with c4 bounds guard, camera toggle verified present.
- **BLOCKER before ANY Phase 10 verification:** suffixed IDs match no Firebase client → register `com.calldad.parent` + `com.calldad.child` in console, replace gitignored `google-services.json` with merged download. Builds fail until then (not a code bug).
- **Also recorded:** Phase 9's game-hub replacement never landed (executor miss, superseded — no recovery needed).

## Where we are (2026-09-20, Phase 11 landed — executor lane, UNCOMMITTED)

- **Architect prompt executed with 6 recorded deviations (ADR-013):** static room + seq + status machine + structured rules + topic FCM (no callId) + FGS foreground-first. REJECTED twice, loudly: flavor role gates (would brick both directions — no differentiated UI exists) and child-only subscription (product direction is child→parent). Callee observes docs (prompt left it blind). Stale-snapshot guard restored.
- **Needs operator:** Studio sync (no new deps) → `firebase deploy --only firestore:rules,functions` (rules REPLACED — old per-call paths deny by default) → §G matrix: rules proofs, 3× calls with seq check, restart recovery, clean logcat. NOTE: old `ring/dad` doc and per-call rooms orphaned in Firestore (dead data, nobody reads them).

## Where we are (2026-09-20, Phase 8 landed — executor lane, UNCOMMITTED)

- **Architect prompt executed with 6 recorded deviations (ADR-010):** debounce machine + `restartIce` (IceRestart constraint), `updateOffer` + callee offer-watcher (prompt's Phase 2 API is gone), role derived from state, auto-reconnect trigger + banner (prompt expects the logs, never wires the cause), callee `listenCall` (was blind post-answer), game.html + PiP card + media-overlay fix.
- **Needs operator:** Studio sync (no new deps) → build → §H matrix: two-device game both directions, simultaneous-tap race, PiP-over-WebView, Wi-Fi toggle recovery, logcat guardrail audit.

## Where we are (2026-09-20, Phase 7 landed — executor lane, UNCOMMITTED)

- **Architect prompt executed with 5 recorded deviations (ADR-009):** no-override fix (wouldn't compile), shared activity-scoped CallViewModel for the bridge, WebViewAssetLoader hardening + nav-lock, 1KB cap + buffer-copy, game.html TAP shell, `webrtcClientOrNull()` accessor. No new host tests possible (native + JS engines) — device matrix per prompt §G is the gate.
- **Needs operator:** Studio sync (androidx.webkit) → build → TWO-device game sync (TAP → "Game state TX" + remote title "Remote taps: N") → quote/backslash escaping test → logcat guardrail audit (no JSON/SDP/ICE).

## Where we are (2026-09-19, Phase 6 landed — executor lane, UNCOMMITTED)

- **Architect prompt executed with 5 recorded deviations (ADR-008):** ptt/ package (interface, simulated default, reflective adapter, focus+haptics, VM+factory+shared accessor), PttScreen replacement (tryAwaitRelease, 3 color states), CallScreen interlock, manifest mic-audio perms. Proprietary boundary holds: zero com.sovereign imports (reflection only), no Gradle dep, simulated default.
- **Prompt bugs fixed:** shared activity-scoped PTT VM (prompt's sharing claim was wrong twice — crash + silent non-sharing); no fake receiving pulse (template confirms Idle-forever accepted); host-test infra (returnDefaultValues + coroutines-test) + 4 engine tests.
- **Needs operator:** Studio sync (no new deps — pure code) → §K matrix (press/release logcat, drag-off release, in-call interlock, boundary grep).

## Where we are (2026-09-20, Phase 5 console triage — rules live, auth failing on BLU)

- **Lane-proven:** new strict rules ARE deployed (`ring/dad` listen → PERMISSION_DENIED for unauthenticated — correct). But BLU logs `Anonymous auth: FAILED`, so every Firestore call is denied and nothing works. Moto side unknown (wireless adb timed out from lane).
- **Two suspects, operator checks in order:** (1) Anonymous provider not enabled in console (Auth → Sign-in method) — most likely; (2) BLU Play Services broken (GMS broker SecurityException + Phenotype errors in same window) → update Play Services, reboot.
- **Needed back:** console Anonymous status; BLU retest (`Anonymous auth: signed in`?); Moto auth line (run locally — lane wireless timed out).

## Where we are (2026-09-20, both phones authed — Phase 5 call unblocked)

- **Moto auth:** `already signed in` (fresh-buffer proof). Both phones authenticated; strict rules passable from both sides.
- **Next:** CALLEE_UID swap-build confirm → first per-call-room call (green card → auto-popup → CONNECTED) → §L remainder (killed-app, rules proofs, TURN check).

## Where we are (2026-09-20, PTT durable on Moto — 8+ clean cycles, auth TBD)

- **Moto (PID 28103, one long session):** SIX more press/release cycles, all textbook (granted→fallback→started→stopped→abandoned), zero errors, zero FATALs. Fallback line absent after the first swap (persistent engine instance — correct). Interlock/drag-off still untested.
- **Moto auth:** still no `Anonymous auth` line in any pasted window (buffer reaches 09-19, so absence is notable but not conclusive — line may predate rotation). One direct grep needed: `Select-String "Anonymous auth"` alone.
- **Still open:** Moto auth confirm; CALLEE_UID swap-build status; first Phase 5 addressed call; full §L matrix.

## Where we are (2026-09-20, auth healed + PTT press/release GREEN on device)

- **Auth:** `Anonymous auth: signed in` + `already signed in` on device 1 (console toggle or Play Services healed — operator-side fix worked). Moto auth line not yet seen.
- **PTT §K.2 (Moto, 2 cycles):** focus granted → fallback to simulated → TX started → TX stopped → focus abandoned, twice, zero errors. Proprietary boundary holds at runtime too (no Mantle). Remaining: drag-off release, in-call interlock, camera toggle already seen working.
- **No crashes** anywhere in either dump (only historical 09-19 tombstones).
- **Still open:** Moto auth line; CALLEE_UID swap-build status; any actual Phase 5 call (no OFFER/ANSWER in these windows); full §L matrix (killed-app, rules proofs, TURN check).

## Where we are (2026-09-19, Phase 5 landed — executor lane, UNCOMMITTED)

- **Architect prompt executed with 7 recorded deviations (ADR-007):** no-KTX, BOM-managed versions, ring-pointer bridge (`ring/dad`, presence-only, strict-ish rules) so app-to-app stays testable pre-Phase-6, CALLEE_UID per-phone provisioning, POST_NOTIFICATIONS runtime ask, status-machine VM (45s ring timeout, 15s media watchdog, cancel-safe), FCM armed-but-untargeted (no tokens until Phase 6).
- **New files:** CallDadApplication, CallDocument, OwnCallRegistry (replaces OwnOfferRegistry), fcm/×2, ic_call, functions/×3, firebase.json, firestore.rules. Deleted: OwnOfferRegistry.kt.
- **NOT yet proven:** everything needs a Studio sync (new deps: auth/messaging/play-services-auth) + `firebase deploy --only functions,firestore:rules` + CALLEE_UID provisioning + killed-app test. Handoff §M boxes filled from operator pastes only — nothing fabricated.
- **Operator console steps (in order):** enable Anonymous sign-in (Auth → Sign-in method) → `firebase deploy --only functions,firestore:rules` → read both phones' uids (Auth → Users) → CALLEE_UID swap-builds → test matrix in prompt §L.

## Where we are (2026-09-20, rules mismatch proven — server runs stale rules)

- **Operator pasted deployed rules:** old dev-open `calls/dad_channel` only. My `firestore.rules` (per-call rooms + ring bridge) was NEVER deployed → every Phase 5 write default-denies (`OFFER publish started` → 0.4s → PERMISSION_DENIED, both phones). All prior theories (crossed/stale UIDs) were wrong; apologize for the runaround.
- **Fix (no rebuild):** console → Firestore → Rules → replace ALL text with `C:\Call-Dad\firestore.rules` content → Publish. Retest call immediately.
- **Process correction (operator's call, accepted):** stop whack-a-mole from the lane; route open research through Gemini/DeepSeek, lane verifies against device evidence. Verification-first questions before theories.

## Where we are (2026-09-20, rules live — writes pass, popup path unproven)

- **Operator runs (both phones, caller-only):** `Call room created` + `OFFER published` + clean `ENDED` teardown on both — Phase 5 write path FULLY GREEN under strict rules. No ANSWER/popup lines anywhere: either choreography (callee never parked on Home) or the ring pointer never lands/listens.
- **Executor closed the evidence gap:** ring-write failures now log (`Ring pointer write failed`); ring receipt logs (`Ring observed`). Both literals, guardrail-clean. If the next test shows publish WITHOUT observed on the parked phone, the fault is isolated to ring-write/rules; if observed WITHOUT popup, it's navigation.
- **Retest (strict):** rebuild both → park Moto on the 4-card screen untouched → BLU calls once, waits → expect `Ring observed` on Moto + auto-popup.

## Where we are (2026-09-20, back-button ghost path closed — executor lane, UNCOMMITTED)

- **Lane read of both tails:** rings observed BOTH ways (`Ring observed` BLU ×2, Moto ×1), publishes clean, teardowns clean — popup path fully proven. But zero Answer taps anywhere: every overlay exit was silent (no DECLINED line) → system back button escaping without room cleanup → ghost rooms + the lingering both-caller chaos. Fix: BackHandler = Hang Up (Decline on overlay), same awaited path.
- **Retest that matters:** rebuild both → BLU calls → Moto overlay → tap ANSWER (green, the one untested button) → expect CONNECTED both sides.

## Where we are (2026-09-20, PHASE 5 CALL GREEN — video both ends, pipeline takes over)

- **Operator report:** connects, video both directions. The candidate-flush fix closed it. No more midnight hunting — open research goes to Gemini per operator order.
- **CRITICAL device intel (new):** BLU is a TEST MULE only. The daughter's real device is a tablet at home, currently inaccessible. Implications: minSdk 26 (ADR-001-B) must hold; tablet model + Android version + camera/mic behavior NEEDED before sign-off (operator to supply); emulator + BLU coverage does not equal tablet coverage.
- **Still unproven (Gemini/Phase 6 territory):** killed-app FCM wakeup, TURN/mobile-data, token plumbing, rules proofs (§L), call history, tablet run.

## Where we are (2026-09-20, no-remote-video root-caused — executor lane, UNCOMMITTED)

- **Operator report:** call connects UI-wise, local PiP only, drops ~14s (watchdog firing = peer never CONNECTED). Root cause: ICE gathering starts at createPeerConnection but the room only exists after the Firestore round-trip — early (host, LAN-critical) candidates were silently dropped while `currentCallId == null`. Fix: stash + flush on room creation; reset clears stash.
- **Retest:** rebuild both → call → expect CONNECTED + remote video (not just PiP), no 15s drop.

## Where we are (2026-09-19, stuck-overlay validated out — executor lane, UNCOMMITTED)

- **Operator report:** Incoming overlay hangs until another call+hangup cycle; both phones on v3 (lane-verified dumpsys) so NOT a stale build. Root cause: overlay opens on a possibly-stale snapshot and only watches for FUTURE deletions — an already-gone room strands it (nothing will ever fire). Fix: `watchIncomingRoom` validates entry (fetchOffer answerable? else bounce home at once) then watches. Covers stale-snapshot, own-ringback, and pre-hangup races.
- **Retest:** rebuild both → BLU calls, hangs up BEFORE Moto answers → Moto's overlay should vanish by itself (or never wrongly appear).

## Where we are (2026-09-19, self-ring + ghost-InCall — executor lane, UNCOMMITTED)

- **Operator bugs (both real, shared root):** (1) double-call + hangup → phone rings ITSELF (own OFFER heard by own Home listener); (2) answering own stale offer → InCall showing local video with no peer ("video without connecting" = local PiP renders immediately, remote black — by design). Fix: `OwnOfferRegistry` suppresses self-offers in listener + fetch; versionCode 3 fingerprints builds (dumpsys-checkable from lane, ends "which build is installed" confusion).
- **Open question for retest:** whether the ghost-InCall persisted past 15s (watchdog build installed?) — new build settles it either way.

## Where we are (2026-09-19, zombie-call guards — executor lane, UNCOMMITTED)

- **Operator bug (confirmed design gap):** caller quick-hangups → callee answers a deleted room → sits InCall with a ghost forever. Guards: (1) 15s media watchdog on every InCall entry (no CONNECTED → silent Idle → auto-home, no scary card); (2) incoming overlay watches the room — vanishes pre-Answer → home. Peer-connected flag resets per call.
- **Test:** BLU calls → hang up within 2s → Moto answers (or sits on overlay) → Moto should be home within ~15s, noRetry card, no crash. Then normal call to confirm the watchdog doesn't bite healthy calls.

## Where we are (2026-09-19, one-sided hangup root-caused — fix committed, needs rebuild)

- **Operator report:** hanging up one side strands the other (must hang up both). Root cause: `endCall` fired teardown into `viewModelScope` then navigated instantly — the pop clears the VM, cancels the scope, and the room delete usually dies with it, so the peer's room-deleted listener never fires. Fix: `endCallAndAwait()` (3s cap, offline-safe) awaited BEFORE navigation on local hangup/decline; fire-and-forget `endCall()` kept for the remote-triggered path.
- **Test:** rebuild both → call → hang up ONE side → other should glide home ~1s later.

## Where we are (2026-09-19, remote hangup + QA retired — executor lane, UNCOMMITTED)

- **Operator asked, executor built:** peer hangup/decline now mirrors home on both sides (`observeRoomDeleted` → `endCall`, auto-home on Idle-after-activity). Grey QA button REMOVED (auto-popup proven; route + `simulateIncomingCall` kept as Phase 5 FCM entry).
- **Real-phone question answered:** yes — background/killed-app incoming is exactly Phase 5 (FCM wakeup). App-open popup is done; nothing more can ring a dead app without push.

## Where we are (2026-09-19, auto-popup incoming call — executor lane, UNCOMMITTED)

- **Operator asked, executor built:** Home listens for new OFFERs and jumps to the overlay itself (ringtone + buzz, silenced on leave). Grey QA button stays as fallback. Killed-app wakeup = Phase 5 FCM. Rebuild + test: BLU calls while Moto sits on the 4-card screen → overlay should pop WITH sound, no taps on Moto.
- **Answer to the question:** yes, it should pop up — the grey button was scaffolding that overstayed. This fixes the app-open case now; FCM fixes the killed-app case later.

## Where we are (2026-09-19, OPERATOR VINDICATED — grid squeezed to zero by my QA card)

- **Device screenshot proved it:** Home shows greeting + one full-screen grey QA card, zero grid. Cause: `GiantActionCard`'s inner `fillMaxSize` Column is safe only inside weighted rows; my unweighted QA card claimed the whole Column and squeezed both grid rows to zero height. Fix: fixed `.height(140.dp)` on the QA card (+ missing `height` import).
- **Lesson recorded:** never trust "works" without a screenshot; the operator's report was precise and I argued instead of looking. Look first from now on.

## Where we are (2026-09-19, operator UX confusion — "no home", grid unseen)

- **Operator report:** only ever sees grey QA card → overlay → Answer → 15s → NOT_FOUND error → Retry ("Ready") / Hang-up (back). Never mentions the 4 colored Home cards — UNCONFIRMED whether the 2x2 grid renders on their build. "Home" jargon retired; describing screens by visible text from now on.
- **Lane observation (screenshots):** BLU showed only the pulled-down Quick Settings shade (airplane mode ON, WiFi on Dayton House — network OK); then BLU left USB. Moto G present on wireless adb (`adb-ZT4222BMWN-…`), screen ASLEEP (black capture). App Home screen never visually confirmed.
- **Standing question for operator:** on the app's first screen, are there 4 colored cards above the grey one? If no green "Call Dad" card is visible, that's a layout bug on the executor — say so and it gets fixed, no choreography will work until it exists.
- **Choreography (once green card confirmed):** phone A taps GREEN card and waits; phone B taps GREY card → Answer within ~15s.

- **Operator run (both phones, QA overlay):** `Call failed: NOT_FOUND` on both = CORRECT behavior — QA overlay is Firestore-blind; both sides opened it with no live OFFER in the room (caller must ring FIRST and stay on-screen; any hangup/decline teardown-deletes the room). Not a bug.
- **Executor robustness fixes (committed next):** (1) caller wipes the room before publishing (stale ANSWER/candidates from prior QA runs or crashes no longer poison new calls — callee side never wipes); (2) callee polls `fetchOffer` ~15s on NOT_FOUND only (absorbs two-human tap timing; other failures still throw immediately).
- **Correct choreography:** BLU taps Call Dad and WAITS on "Calling Dad…" → Moto taps gray QA button → overlay → Answer within ~15s → ANSWER published → both CONNECTED. Decline/hangup either side kills the room — start over if Retry appears.

## Where we are (2026-09-19, Phase 4 landed — executor lane, UNCOMMITTED)

- **Architect prompt executed (1 created, 5 modified, package `com.calldad`):** `CallState.Incoming`, `VideoRenderer` (composable-owned init/release, client EGL only, null = black placeholder), `WebRtcConfig.iceServers` (STUN + REPLACE_ME-gated TURN), WebRTCClient (ctor iceServers, `eglContext`/`localVideoTrack` accessors, `buildRtcConfig()`, attach*/detach* + fields DELETED), VM (EGL/track flows, `simulateIncomingCall()`, cancel-safe), CallScreen (Incoming overlay + Answer/No ≥160dp, InCall video Box with remote/PiP/timer/controls, `mode` param), nav-arg `call?mode={mode}`, DEBUG-only Home QA button (`buildConfig=true`), `CallStateTest`.
- **Executor scope call (ADR-006):** QA hook drives the REAL `answerCall()` (not a fake overlay) so two-device E2E is actually testable; production route untouched; decline-wipes-room accepted; per-call rooms → Phase 5.
- **Gates:** verify re-run next. Operator: rebuild, install both phones, BLU calls → Moto opens QA button → Answer → expect ANSWER published → both CONNECTED with video. Watch for EGL/black-screen issues (report exactly).

## Where we are (2026-09-19, two-device run: BOTH sides called, nobody answered — by design)

- **Operator run (Moto + BLU):** both logs show caller leg only (`OFFER published` in <2s both — Firestore healthy), then hangup. No ANSWER anywhere: no incoming-call UI exists yet (carried Phase 4 gap), so `answerCall()` was never invoked on either side. Nothing failed; the callee path is simply unreachable from the UI.
- **OFFER-overwrites-OFFER note:** single hardcoded room `dad_channel` + merge writes mean the second caller's OFFER clobbers the first — fine for one-channel testing, must go before multi-call (Phase 4: per-call rooms).
- **Decision needed (operator):** (A) executor builds minimal incoming-call overlay now (OFFER listener → Answer/Decline → answerCall()); (B) aiortc desktop-callee experiment to prove media without app changes; (C) wait for Gemini Phase 4 instructions (already requested).

## Where we are (2026-09-19, Phase 3 caller leg GREEN on device — both fixes proven)

- **Operator run (BLU, new build PID 17206, two sessions):** `OFFER publish started` → **`OFFER published`** (~3s, Firestore enabled) in BOTH sessions. No `Call failed: UNKNOWN`. No crash.
- **Executor lane check:** zero `FATAL/AndroidRuntime` for PID 17206 across two hangups; last crash remains 20:17 PID 16209 (old build). Double-dispose fix + cancel-rethrow both device-proven.
- **G3-device status:** caller leg GREEN (publish + clean teardown). Callee leg (ANSWER/CONNECTED) BLOCKED on second device — Moto G gaming. Renderers + TURN + incoming-call UI remain Phase 4.

## Where we are (2026-09-19, ROOT CAUSES PROVEN from device — both fixed in code / console)

- **Hang cause (Firestore, device-proven):** logcat shows `PERMISSION_DENIED: Cloud Firestore API has not been used in project calldad-508d7 before or it is disabled` — project exists and json matches, but **no Firestore database provisioned**. Writes pend in offline mode forever → "Calling Dad…" hang. Fix = console-side (operator): enable Firestore + create database + dev rules below. NOT a code bug.
- **Crash cause (double dispose, device-proven ×3):** `WebRTCClient.dispose:278` (`eglBase.release()`) from `onCleared` — endCall() disposes, hangup pops nav → VM cleared → onCleared disposes again → throw. Fixed: idempotent `disposed` guard. Plus `CancellationException` rethrow in startCall/answerCall (the stray `Call failed: UNKNOWN` was scope-cancel at clear, not an error).
- **Fix commit pending:** dispose guard + cancel-rethrow above. Rebuild + retest after console step.

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
