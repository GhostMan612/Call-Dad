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
- [x] Human Studio run GREEN 2026-09-19: `:app:testDebugUnitTest` + `:app:lintDebug` → BUILD SUCCESSFUL, 33 tasks (K2 analysis-API warnings = toolchain noise only). Device install proof still pending. — **superseded:** those unflavored task names no longer exist (ADR-012 flavors landed 2026-09-20 and rename every variant task); the live names are `testParentDebugUnitTest` / `testChildDebugUnitTest` / `lintParentDebug` / `lintChildDebug`.
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
> **SUPERSEDED by ADR-015** (pair-scoped rooms replaced `family_channel`). The `[x]` below records what landed and was then retired; nothing here should be re-ticked.

- [x] Static family_channel + seq + status + structured rules + topic FCM + foreground-first FGS + callee observation (role gates + child-only sub REJECTED)
- [ ] Human: Studio sync (no new deps) → deploy rules+functions (REPLACES old rules) → §G matrix — **VOID: superseded by the Contract 8 pair-scoped deploy, which is DONE**

## Phase 9 — Ringtone + multi-TURN + games hub + voice bot (architect prompt landed 2026-09-20, ADR-011)
- [x] TURN_URLS + audio-mode set/reset + single-ringer + voice helper (STT→bot→TTS) + game hub + host-tested bot
- [ ] Human: TURN lines or empty → §K matrix (looping ringtone, vibration, jokes, airplane STT, multi-game sync, clean logcat) — **partly superseded: TURN is now Open Relay by default and K8 is device-proven. Ringtone/vibration/jokes/multi-game remain unwitnessed.**

## Phase 10 — Flavors + games hub + hardening (architect prompt landed 2026-09-20, ADR-012)
> **DONE.** Listed after Phase 11/9 in the original file purely by accident of authoring order; moved here so the numbers read in order.

- [x] parent/child flavors (blue/pink, APP_THEME, dynamic off) + full game hub (c4 bounds guard) + camera toggle verified
- [x] Console registration for `com.calldad.parent` + `com.calldad.child` + merged google-services.json (builds fail until then)
- [x] `assembleParentDebug` + `assembleChildDebug` + side-by-side install + blue/pink check + feature colours + c4 bounds torture (99/-1/banana) + clean logcat — **device-proven through vc7**

## BP-03 — Chat + remote (was mislabelled "Phase 3", which collided with the Phase 3 delivery phase)
- [x] `ChatThread` pure reducer: monotonic receipts + idempotent ingest (BP-03's G3 gate, `ChatThreadTest`)
- [x] `ChatText` no-link rule + `ConsentScope.TEXT` gate (`ChatTextTest`)
- [x] `ChatClient` over `calls/{room}/chat/`; DELIVERED stamped by the RECEIVER, never on write success
- [x] `ChatViewModel` + `ChatScreen`; activity-scoped so two listeners cannot race the receipts
- [x] Rules stanza + emulator tests (bounded body, immutable sender/body, receipts-only update)
- [x] `ChatKidSafetyTest`: no ClickableText/autoLink/intents/autocorrect, asserted against CODE
- [x] `RendezvousClient` + relay — **NOT NEEDED, SUPERSEDED by ADR-015**: the Firebase pair room replaced the rendezvous relay, so the donor port is retired rather than ported. The spec's "remote works via rendezvous" is met by pair-scoped Firestore signaling plus an ICE relay for media (ADR-004 WebRtcConfig, K8 device-proven). This line is closed by a DECISION, not by omission.
- [ ] Human: thread survives app restart on both phones; a 500-char message and a link-shaped message both behave

## BP-04 — Photo + video (was mislabelled "Phase 4")
- [x] `PhotoTransfer` chunk + zero-padded index + SHA-256 verify (`PhotoTransferTest` byte-proof on synthetic fixtures)
- [x] `PhotoClient` send/receive; chunks first, manifest last; unverified bytes never published (`PhotoClientTest`)
- [x] `PhotoViewModel` + `PhotoScreen`; decode gated on `verified`
- [x] Permissionless system picker: NO `READ_MEDIA_IMAGES`, no MediaStore write, and the **photo path** uses no camera (`PhotoSafetyTest`). NOTE: the app DOES declare `CAMERA` for the video call (ADR-005), feature `required="false"`. An earlier row here claimed "NO `CAMERA`" and a test asserted it — that assertion was **wrong on its own terms** and only ever "passed" because it threw `FileNotFoundException` before running.
- [x] Rules stanza: immutable manifest, index==docId, per-chunk and total byte caps
- [x] Video spike decision — **ADR-005, WebRTC (stream-webrtc-android 1.3.10)**, and the donor UDP `LiveCallSession` port was dropped in its favour. Device-proven both ways.
- [ ] Human: a real photo is sent, verified, and rendered on the other phone
- [ ] Human: offline/direct-mode matrix (airplane+WiFi, hotspot host, hotspot join, Wi-Fi-Direct). Source behaviour is bounded — a lost call ends at `LOST_GRACE_MS` and the child returns Home.

## BP-05 — Hardening (was mislabelled "Phase 5")
- [x] Parent gate (`ParentGate.kt`) + `ConsentScreen` behind it, `remember` not `rememberSaveable`
- [x] Consent cert + kill switch — **in source, enforced from vc11** (was NOT enforced in vc10, which is what is on both phones; never witnessed on a device): append-only seq-range revocation (ADR-017), pinned by `ConsentEnforcementRegressionTest`
- [x] SQLCipher closed as a deferral-with-justification (ADR-003, sharpened by ADR-018)
- [x] kid-UX audit sheet + `KidUxAuditTest` (12 criteria, machine/HUMAN split)
- [x] Release-signing plan (`docs/release-signing.md`, `signingConfigs`, proguard stub)
- [x] Missed-call callback card + call log (ADR-018: DataStore, not Room)
- [ ] v0.1 device proof on Moto G + BLU View 5 (human) — the remaining BP-05 gate

## Contracts 2–6 — Signaling rebuild → pairing → reliability (landed 2026-09-21/22, pushed)
- [x] C2&3: 7-state CallState + per-call rooms + topic wakeup + wakelock + strings (Mama/Dad)
- [x] Legacy cleanup: duplicate CallDocument deleted, seq-tracked SDP, Home listener restored, try/catch call launches, ICE trickle, setup timeouts, instruments, caller-label fix, stale-track crash guard, SecurePeerStore
- [x] C4: CameraX 1.4.2 + unbundled ML Kit 18.3.1 + ZXing pairing (QR both ways, DataStore persistence)
- [x] C5: PAIRING route + gear + backup rules both API ranges + allowBackup false + camera unbind
- [x] Timer fix: elapsed timer on Connected (was frozen at 00:00)
- [x] Operator-run clean assembleParentDebug+assembleChildDebug BUILD SUCCESSFUL; first E2E call GREEN (offer/answer live)
- [ ] Human: fresh-install both flavors → pair both ways → strict one-caller test → hangup test (observer-guard confirmation)

## Contract 7 — Stale takeover + mutual pairing + debt (committed f52d9ea; takeover/heartbeat superseded by ADR-015)
- [ ] ~~Heartbeat (120s batch: room updatedAt + pairings presence) + stale-CONNECTED takeover (20-min threshold + peer-unreachability guard)~~ **NOT SHIPPED — removed by ADR-015:18 ("no busy/takeover logic any more").** No heartbeat writer exists anywhere in `app/src`. This line was ticked by mistake; leave it unticked. `hasPendingWrites` guards DO ship (see Contract 8).
- [x] Mutual handshake (QR-derived nonce, presence docs, 30s peer wait, 1.5s hold) + pairings rules stanza
- [x] CallStateTest rewritten (7-state fromDocument); CallDocumentTest deleted — **the model was moved, not retired**: `CallDocument` lives at `SignalingClient.kt:332` and is the pair-room document consumed by `CallViewModel`, `CallForegroundService`, `ConsentStore`, `ChatClient` and `PhotoClient`, carrying the `negotiationRound` that Contract 11 depends on. deprecation suppressions + FID TODO
- [x] ADR-014 (pairing) + LESSONS_LEARNED.md + CURRENT_STATE/CHECKLIST updated
- [ ] Human: deploy pairings rules (console — new stanza, lane cannot deploy) → assemble both → takeover matrix (§2) + handshake matrix (§3) + heartbeat check (§4)

## Contract 8 — Full-repo sweep + fix (ADR-015, 2026-09-24)
- [x] Pair-scoped rooms `calls/{uidA_uidB}`; rules authorize from the id (no eavesdrop, no squat, no reinstall brick); 15 emulator rules tests
- [x] Token-targeted ring push (`users/{uid}.fcmToken`), function fires on create too, Node 22; 6 node tests
- [x] Activity-scoped call session: rings from every screen, game shares the live call, Home listener retired
- [x] WebRTCClient single-use per attempt + safe dispose order (hangup SIGSEGV root cause) + shared EglBase
- [x] ICE ordering both sides (queue local until SDP published, buffer remote until description set)
- [x] Seq-checked finishCall on hang up / decline / no-answer (45s) / lost peer (20s); publish-in-flight hangup cancels the ring; glare auto-answer
- [x] Ringtone + vibration actually wired (single process ringer) + caller ringback; silent notification channel
- [x] Foreground service: startForeground first, paired-room validation, self-dismiss
- [x] Pairing: grown-ups gate, mutual handshake before storing, own-code rejection, QR v2
- [x] In-call: mute, flip camera, play-a-game door; camera resume respects the kid's toggle; permission-denied screen
- [x] Games: solo pass-and-play + validated synced play + late-join resync; WebView 403s all network; CSP
- [x] PTT never stays hot (try/finally, focus loss, call start); Helper silenced on leave, TTS stop, on-device STT fallback, whole-word matching
- [x] Gates: 80 host tests PASS, lint 0 errors both flavors, verify PASS
- [ ] Operator: `firebase deploy --only firestore:rules,functions` → install both flavors → re-pair both phones → device matrix K11 (CURRENT_STATE)

## Contract 9 — Real walkie-talkie (ADR-016, 2026-09-25)
- [ ] Device evidence **UNVERIFIABLE as recorded**: `versionCode` stayed 4 across ADR-015 and ADR-016, so the flashed build cannot be told apart via `dumpsys`. The only dated device artifact is an operator logcat of **2026-09-24**, which predates Contract 9. Corrected by the Contract 10 bump to `versionCode 5`. Re-flash before ticking.
- [x] PTT proven fake before this contract ("Sovereign Mantle not on classpath" — the simulated engine reported success without sending)
- [x] VoiceClipPttEngine: hold-to-record AAC clips → pair room `ptt/` → auto-play on any screen, paused during calls
- [x] Rules stanza + 2 emulator tests (suite was 17 then; it is **44 now**). The `pairings` delete clause is no longer the untested one — it moved to `firestore.rules:315-336` when the stanza was rewritten, and is now covered twice: a live handshake cannot be deleted early, and an expired one is clearable.
- [ ] "Deleted after play" was FALSE until Contract 10: the clip was deleted after the first playback *attempt*, on the 30-minute staleness sweep, and on a ring interrupt. Deletion is now gated on `onCompletionListener`. A failed or interrupted clip is retained.
- [ ] "SENT!" was FALSE until Contract 10: the Firestore `add()` was fire-and-forget, so a queued, offline, or rules-rejected write still flashed a green confirmation. It is now awaited.

## Contract 10 — Full-repo sweep + fix (2026-09-25, `/sweep` @ `5bef86b`)
Six read-only auditors (call core, media, Firestore rules, FCM wakeup, kid UX, doc drift) audited `5bef86b`; top findings were re-verified in source before anything was written. No build, install, or device run was claimed.
- [x] PTT honesty: `add()` awaited; delete only on real completion; 30-min sweep no longer destroys unplayed clips; listener errors surface as `PttAudioState.Error`; `setOnInfoListener` handles `MAX_DURATION_REACHED` (was: silent death at 15s, then "Hold the button down"); bytes read before the size check; press/release race fixed with `isSending`
- [x] `finishCallDetached(callId, seq, status)` now generation-checked (was: no `seq`, so a stale teardown cancelled a newer call)
- [x] `sendLocalCandidate` carries the attempt + client reference (was: generation-*n* ICE landed in generation *n+1*)
- [x] `onPairChanged` writes the room before resetting (was: silent local `Idle`, peer rang to its own timeout)
- [x] Removed the first-launch system-Settings escape (`MainActivity` was the app's only `startActivity` — the only route out of the sandbox)
- [x] "Connection lost. Reconnecting…" → "Connection lost…" (`RECONNECTING` is still never assigned) — **and this line's claim that "no `restartIce` exists" is now OBSOLETE: ICE-restart ships, and the screen string is still correct because the restart is transparent.** Fixed again in Contract 11, where the restart turned out to be published-but-never-answered on the caller side.
- [x] Raw `Throwable.message` no longer rendered on the child's Error card
- [x] Speakerphone engaged on ICE connect, not on peer-connection creation (was: caller's phone switched to speaker for the whole 45s ring; a receive-only phone never switched)
- [x] `VideoRenderer.removeSink` guarded — the `MediaStreamTrack has been disposed` twin of the hangup crash (Compose disposes the sink one frame after `pc.dispose()`); false "no-op" KDoc corrected
- [x] `startRingback` refuses to steal a service-owned incoming ring (ownership fence bypassed)
- [x] FGS identity wait raised 3s → 15s and "couldn't check" separated from "not for you" (a slow cold start used to drop a real ring, logged identically to a spoof)
- [x] FCM start failures distinguished and a downgraded/unavailable foreground service now posts a heads-up fallback instead of failing silently forever. (NOTE: no explicit `MessagePriority` read — the compile-safe path is exception handling plus the fallback, not a priority check.)
- [x] "Dad is talking" banner above the NavHost (a parent's voice used to play on the Game screen with no visual cue and no replay)
- [x] `ParentGate` unlock is `remember`, not `rememberSaveable` (process death restored the child past the gate)
- [x] Fleet self-audit: real device serials removed from `.opencode/commands/flash.md`; operator username removed from `opencode.json`
- [x] **Serial leak CLOSED (was open for two sessions).** That first pass only cleaned `flash.md`; six tracked files still carried both serials, including a hardcoded "expected mapping" table in `.opencode/tools/device-evidence.ts` — the evidence tool itself. All six redacted, and `tools/verify_project.py` now FAILS the gate on a 14+ digit serial or an `adb-<SERIAL>-…` form, so the law is enforced rather than written down. `fixtures/` text must carry `synthetic-only`.
- [x] **`tools/verify_project.py` has a self-test** — `VerifyProjectSelfTest` asserts the device-identity bans exist, are wired into the scan loop, and report the match. A gate never watched fail is a gate nobody can trust; that gap is how the leak survived.
- [x] **K12 quiet PTT channel re-landed** — dedicated `CHANNEL_PTT_MESSAGE` at `IMPORTANCE_LOW`, no sound, no vibration. It shipped on the IMPORTANCE_HIGH call channel, and from Android 8+ the channel wins over the per-notification priority, so a voice message rang at full volume on a locked phone. Call channel untouched.
- [x] **K21 keyguard takeover fixed** — the manifest **still carries** `android:showWhenLocked="true"` and `android:turnScreenOn="true"` (`AndroidManifest.xml:46-47`); what changed is that `MainActivity.onCreate` opts **OUT** at runtime (`setShowWhenLocked(false)`/`setTurnScreenOn(false)`, `SDK_INT >= O_MR1` guarded, minSdk 26) and the ring's full-screen intent is conditional on `isKeyguardLocked` (`fcm/CallForegroundService.kt:185`) instead of hardcoded `true`. `QuietNotificationTest` pins the runtime opt-out. Do not "tidy" those two manifest attributes back on — the runtime opt-out is the fix. `CATEGORY_CALL` + `PRIORITY_MAX` retained so a locked phone still rings. **Both unproven on device — vc9 is source-only.**
- [x] **`LESSONS_LEARNED.md` split** into PART 1 mistakes-we-made (each with root cause + the check that stops it) and PART 2 platform reference.
- [x] **`RULES.md` §1.4/§1.4a** now treat a blocked commit as an emergency (escalate the same turn), after green-but-uncommitted K12/K21 work was lost from the working tree.
- [x] **`RULES.md` §1.5c added** — leave the device as you found it (Wi-Fi, airplane, Bluetooth, DND, font scale, no leftover pairings).
- [x] **Doc drift fixed (twice — the first pass was incomplete).** `docs/setup-android-studio.md` told the operator to create the project at Kotlin 2.1.0 / AGP 8.13.2 / minSdk 30 with Hilt/Room/SQLCipher/OkHttp/Concentus/KSP, none of which are in the build; it now points at `libs.versions.toml`. `CURRENT_STATE.md`'s device table said vc5; the phones moved to **vc7** and the source to vc9 (now vc11). `BP-01-skeleton.md` got the *toolchain* half of that fix here and kept its dep list — Hilt, Room 2.6.1 (KSP), Compose BOM 2024.12.01, core-splashscreen — and its unflavored gate names **until 2026-10-02, when the second half was also fixed** (`BP-01-skeleton.md:9,15` now negates all eight rejected deps and names the four flavored tasks). A doc-drift claim of "fixed" was itself wrong because only half the file had been edited.
- [x] **vc10 → device DONE 2026-10-01.** Both phones flashed, `dumpsys` confirms: Moto G 2025 (parent) `com.calldad.parent` vc10 / 0.3.0-parent at 08:19:05; BLU View 5 (child) `com.calldad.child` vc10 / 0.3.0-child at 08:19:19. One flavor per device, no crossed install. Installed `-r`, so the anonymous Firebase account, peer UID, pairing handshake and the child's existing consent grant all survived. The vc9→vc7 gap that left the loud-voice-message and lock-screen-trap defects live on the child's phone is closed; whether the fixes work is now a witness question, not an install question.
- [x] Docs: heartbeat line unticked (never shipped), ADR-015 §6 amended, `RULES.md` §1.5/§1.5a/§1.5b/§1.7a corrected
- [x] **Gate G-C10 host** — GREEN 2026-09-30: `verify_project.py` PASS; 55 unit tests 0 failures; lint 0 errors; `functions` 6/6; rules emulator 17/17. The two Node gates were SKIPPED for all of Contract 9 and now actually run (Node 22.23.2 + Android Studio JBR were on the machine, not on this lane's PATH).
- [x] **`.firebaserc` added** — `firebase.json` says WHAT to deploy, `.firebaserc` says WHERE. It was missing, so `firebase deploy` had no target and the error reads like an auth fault.
- [x] **`functions/package-lock.json` tracked** — pins the deploy to the dependency tree that passed the gate. `tools/rules-test/package-lock.json` stays ignored: it never deploys, so its resolution may float. The asymmetry is deliberate.
- [x] **Operator: `firebase deploy --only firestore:rules,functions`** COMPLETE 2026-09-30. Rules released; `onCallRoomWritten` live (v2, us-central1, nodejs22). First attempt failed on first-time Eventarc service-agent propagation; a plain retry succeeded — not a code bug. The deploy also created the missing `databases/(default)`, which had been the "Calling Dad…" hang since 2026-09-19.
- [x] **Operator: clean `assembleParentDebug`+`assembleChildDebug`** BUILD SUCCESSFUL, 33 tasks executed from scratch. Installed to Moto G 2025 (parent) and BLU View 5 (child). `dumpsys` confirms `versionCode=5` on both, one flavor per device. Operator authorised this from the lane over RULES §1.5 — a one-off, not an amendment.
- [x] **Operator: first end-to-end call PROVEN** 2026-09-30 — both phones call and answer, video good, PTT works both ways. (Operator-witnessed; no logcat read by the lane.)
- [x] **Rules emulator "evaluation error" noise triaged** — 22 log lines despite 17/17 pass. Cosmetic: probes confirmed all six legitimate member operations return ALLOWED with no error, so no denial is masking a real allow. Recorded so it is not re-investigated.
- [x] **PTT tail clipping fixed AND device-proven (K14 CLOSED)** — operator reported every clip cuts the end of the last word on immediate release. `stopTransmitting()` stopped the recorder the instant the finger lifted; an AAC encoder's priming delay plus unsent frames are discarded when the MPEG-4 container is finalised. Now records a `ENCODER_DRAIN_MS` (700ms) silent tail first. Three source-invariant tests pin it. vc6/0.2.3 clean-built and installed to both phones; operator re-verified BOTH directions: no clipping, no dropped syllables.
- [x] **`versionCode` 5 → 6 / `0.2.2` → `0.2.3`** — deliberately NOT reusing 5, because 5 is the fingerprint that proves the clipping bug. A `dumpsys` reading of 5 after the fix would be meaningless.
- [ ] **Operator: build + install vc6, then re-test PTT tail.** The drain fix is source-only until it is on a phone. Release the button immediately after speaking; the last word must survive.
- [ ] **Operator: ADR-015 device matrix (K11)** — killed-app ring, force-stop, doze, no-answer timeout, lost-peer end, game sync during a call, pairing-gate bypass.
- [ ] **Operator: mobile-data call** to measure how badly the missing TURN server (K8) actually bites.
- [x] **K8 CLOSED AND DEVICE-PROVEN** — a call completed with **mobile data on and Wi-Fi off** (operator-witnessed 2026-09-30). That was the only test that could prove the relay: STUN-only calls worked on the sofa and died anywhere else, and a child reads that failure as "Dad isn't answering". Default relay is Open Relay (public, published creds, no account, no billing); `local.properties` TURN_* still overrides it for a private relay. Recorded honestly in RULES §1.7a and `WebRtcConfig`: WebRTC media is DTLS-SRTP so the relay carries ciphertext, but it IS a third party in the media path and the creds are APK-extractable. Accepted for a 2-person family app, NOT for real child media. `hasTurnRelay` exposed so the app can say so instead of failing mysteriously. **Not proven: a symmetric-NAT carrier.**
- [x] **K9 CLOSED — ML Kit phone-home accepted with operator sign-off.** RULES §1.7a now names the single exception and states exactly what leaves the device (barcode model fetch + anonymous Play Services telemetry) and what does not (no image, camera frame, QR payload, UID, nonce, audio, video). Rationale recorded: pairing must not fail for a grown-up, and ZXing fallback is already wired for a missing module.
- [x] **K12 deployed and live; screen-off behaviour still to witness** — `onPttClipWritten` created in the live project (v2, us-central1, nodejs22, verified via `firebase functions:list`). Pure decision in `functions/clip.js` with 7 tests, mirroring `ring.js`. `CallMessagingService` posts a quiet "A message is waiting" heads-up and skips when the app is already foreground. **NOT yet witnessed: a locked phone actually showing it, and — the part that matters — that it stays quiet.**
- [x] **K17 CLOSED — `pairings/{uid}` no longer world-readable to any signed-in install.** Was `get: if request.auth != null`, so any anonymous install could read any pairing doc by id and take a real UID, a real peer UID, and a **live session nonce** — which is half of hijacking a pairing. Now requires being the owner or the `peerUid` named in the doc, which is the only read the mutual handshake needs. Also: a live handshake can no longer be deleted early (that would strand a pair mid-scan), while an expired one is clearable so a phone that died mid-handshake doesn't leak it. Emulator pins all three cases.
- [x] **K18 CLOSED — orphan cleanup after a reinstall.** `calls` delete is now allowed for a member when `status in ['ENDED','DECLINED']`; it was `if false` for every case, so a reinstall's old room was written by an account that no longer existed on the phone and was undeletable by anyone, forever. Live rooms remain undeletable by anyone — tested for caller, callee AND outsider, because a deletable ringing room would be the same class of bug as the stale teardown.
- [x] **K19 CLOSED — per-pair clip bound (operator decision: count + 24h grace).** Prune only when the room holds >50 clips AND the oldest is >24h old, oldest-first. Played clips already delete immediately, so a live pair never accumulates and the prune never engages; the 24h grace is what stops a parent away for a weekend from losing a message. Runs AFTER the send resolves, and a prune failure never surfaces as a send failure — the words were sent; a failed prune is storage pressure, not a lost message.
- [x] **K16 CLOSED — dependency bump, and DEPLOYED.** firebase-functions 6.1.0 → 7.4.0, firebase-admin 12.7.0 → 14.5.0 (both major). `functions` gate 6 → 13 tests. `npm audit` 8 moderate → 2, both in `glob`/`teeny-request` on a path this app never touches. `functions/package-lock.json` re-pinned. The MAJOR bump deployed clean: both functions live on nodejs22, and the CLI's "outdated firebase-functions" warning is gone.
- [x] **`versionCode` 6 → 7 / `0.2.3` → `0.2.4`** — the APK's ICE servers changed, and a phone left on 6 would silently lack a relay and fail on mobile data. `dumpsys` cannot see that at all, so the bump is the only way to tell the builds apart.
- [x] **Gate G-C11 host (2026-09-30, Contract 11 close-out)** — GREEN: verify PASS (102 files); 68 unit tests 0 failed; lint 0 errors; `functions` 13/13; rules emulator 22/22 (was 17).
- [x] **Operator: re-deploy DONE** — `firebase deploy --only firestore:rules,functions` completed. Rules released; `onCallRoomWritten` updated; **`onPttClipWritten` created** (v2, us-central1, nodejs22, 256MB, confirmed via `firebase functions:list`). The K12/K16/K17/K18 changes are all live.
- [x] **Operator: clean build + install vc7 DONE** — `clean assembleParentDebug assembleChildDebug` BUILD SUCCESSFUL (37 tasks executed). `dumpsys` confirms vc7 / 0.2.4 on both: Moto G 2025 (parent) 10:20:55, BLU View 5 (child) 10:21:01. One flavor per device. (Moto is a WIRELESS-adb device: `adb mdns services` gives its current host:port, and the port changes per session.)
- [x] **Operator: mobile-data call PASSED** — the test that could only be run by a human, and it closed K8. See the K8 line above.
- [ ] **Operator: screen-off PTT (K12).** Send a clip with the receiving app closed or the screen locked. Expect a quiet "A message is waiting" notification — **no ringtone, no vibration, no full-screen anything.** If it rings loudly, the normal priority is wrong and that is a bug to fix immediately, not a preference.
- [ ] **Operator: re-pair both phones under the tightened K17 read.** The rule is deployed and emulator-tested, but whether a real phone completes a fresh pairing under owner-or-named-peer only is unwitnessed. If the client assumed any-signed-in could read, pairing fails here — which is the correct, safe failure.
- [ ] **Operator: K11 remainder** — killed-app ring, force-stop, doze, no-answer timeout, lost-peer end, game sync mid-call. All human-witnessed; no host test can assert them.
- [ ] **Still open (tracked in CURRENT_STATE):** K20 pruning is client-triggered so a fully-dormant pair never prunes (accepted — a Firestore TTL would need a scheduled function and a billed index, and a dormant pair costs cents); TURN creds are long-lived and APK-extractable (accepted, override path documented); K8 proven for a normal NAT, not a symmetric one.

## Contract 11 — v0.1 feature completion + the reconnect fix (2026-10-01)

Source-only work closing the gap between `SPEC_SHEET.md` §2 and the app. No device claim anywhere in this section.

- [x] **Text chat (BP-03)** — thread, monotonic receipts, idempotent ingest, the no-link rule, pair-scoped rules, UI. `ChatThreadTest` + `ChatTextTest` + `ChatKidSafetyTest`.
- [x] **Call log + missed-call callback card (BP-05 §4)** — the criterion the kid-UX audit had been recording as an open FAIL. `CallLogTest` pins the card, the 24h window, the decline exclusion, the cap and the drop order.
- [x] **Consent cert + kill switch, enforced (ADR-017)** — was an inert domain model. Grant/revoke path, `ConsentStore`, rules, and a `ConsentScreen` behind `ParentGate`. Revocation redesigned as an append-only **seq range** after two emulator failures showed a `revokedAt` flag is destroyed by a re-grant AND that making it permanent bricks a child's phone on one accidental tap.
- [x] **Photo sharing (BP-04)** — the Android half, which the previous commit left as transport-only. Encoder, client, permissionless picker, screen, rules. `PhotoTransferTest` + `PhotoClientTest` + `PhotoSafetyTest`.
- [x] **STORE DECISION: no Room (ADR-018)** — the call log is DataStore, the chat thread is pair-scoped Firestore. `SPEC_SHEET` §2.5's "local Room" and §4's "stay on-device" are both recorded as **NOT met** rather than reinterpreted. ADR-003 stays closed with a sharpened re-open condition.
- [x] **ICE-restart reconnect FIXED — it shipped broken and was declared working.** `maybeApplyRenegotiation` opened `if (amCaller) return`, so the side that published the restart offer never applied the answer; and the guard that would have caught it was keyed on `seq`, which the caller had already consumed for the original answer. Replaced with a monotonic `negotiationRound` carried through the document and the rules. Six new host tests plus four emulator cases pin it. **This is the single most important bug in this contract** — see the lesson in `LESSONS_LEARNED.md`.
- [x] **`gate` tool fixed** — it reported GREEN from a tree where `compileParentDebugKotlin` was failing. It had **no `rules` gate at all**, and `r.out || r.err` discarded the Kotlin diagnostics. Now: a real emulator gate with a JVM path, concatenated output, a 200-line tail, and a verdict that says "GREEN BUT n SKIPPED".
- [x] **`versionCode` 9 → 10 / `0.2.6` → `0.3.0`** — MUST NOT be reused, because absence of a consent grant now DENIES: a phone on 9 and a phone on 10 behave OPPOSITELY on a fresh install, and two phones on the same "version" would disagree about whether the app works.
- [x] **`versionCode` 10 → 11 / `0.3.0` → `0.3.1` — the CONSENT ENFORCEMENT pass.** 10 shipped a parental kill switch that enforced **nothing** on calling or the walkie talkie, in either direction, and did not stop photo/chat downloads; the parent's own grant sequence was structurally unreadable so the button could not act on the phone that owns it. Both are the two oldest features (they predate the consent model, so nobody went back) and both failures are silent. MUST NOT be reused: a parent on 10 is told the app is switched off while it is not, which is the worst failure this app can have. Fixed in 11 and pinned by `ConsentEnforcementRegressionTest` (14 tests).
- [x] **Gate integrity: `verify_project.py` now fails on a literal control byte**, and `tools/prove_gates_bite.py` injects a known defect and asserts the gate goes RED and recovers. Found a real 0x1F inside a comment in `CallLogStore.kt` and a 0x00 + 0x1F in `ChatText.kt` — the latter in the comment explaining why control characters matter. A raw control byte makes git treat the file as binary, which silently breaks grep, diff and review on exactly the file least likely to be reviewed. Both had passed every previous gate, which is the point: green means nothing until it has been seen to go red.
- [x] **Blueprints repaired** — three duplicate phase numbers, BP-03/04/05 retitled and re-ticked, superseded phases marked VOID rather than left looking open.
- [x] **The tree at `8f47512` DID NOT COMPILE, and the gate said GREEN.** **No version bump:** `4a5c555` fixed a compile error *inside* vc11, and reusing 11 is correct because what 11 means did not change. (An earlier draft of this row called it "vc12 / 0.3.2"; that number was invented and never assigned — there is no vc12.) A duplicated `viewModelScope.launch {` in `ChatViewModel.init` (from an edit that re-emitted the opening brace while the original kept its own) made the app uncompilable, and the full five-suite gate still printed **GATES GREEN** on it. `8f47512` is therefore a **broken commit pushed to `origin/main`** — anyone cloning it gets an app that will not build. Caught only by running `assembleParentDebug`, which is an `ask` task and had been refused earlier in the session, so the refusal deferred the build and nobody noticed what the green had been hiding.
  - **Root cause is the whole lesson.** Five separate times now the gate has reported green over a tree that did not compile: the stale `gate` tool, nine shipped feature defects, a `verify_project.py` scan that hid a control byte behind `errors="replace"`, a unit run whose verdict was read out of a stale report directory, and now this. Every one was "the check ran, and the check was not looking at what I thought".
  - **Six more test failures were sitting in that green tree too**, all invisible, all now fixed. `PhotoSafetyTest.theAppHoldsNoGalleryPermission` and `theAppTakesNoCameraPermission` read `app/src/main/AndroidManifest.xml` while the test CWD is `app/`, so **both threw `FileNotFoundException`** — the gallery-permission kid-safety check was asserting nothing at all while appearing to be enforced. `theAppTakesNoCameraPermission` was ALSO WRONG ON ITS OWN TERMS: it demanded the app hold no CAMERA permission, but the video call legitimately needs one (Phase 3, ADR-005), and it only ever "passed" because it never reached its assertion. Rewritten to pin the narrow true property — the *photo path* uses no camera, and the camera *feature* stays `required="false"` so an audio-only device can install. `RoutesTest` matched a literal `composable(Routes.CALL)` that no longer appears, because the destination is `route = CALL_ROUTE`; it now resolves local aliases, and its "mode argument" assertion likewise looked for a literal string instead of the template. And two `WiringRegressionTest` cases sliced source with `substringAfter(x).substringBefore(y)`, where **`substringBefore` returns the ENTIRE remaining file when its delimiter is absent** — moving the marker silently widened the slice to the whole file, and both tests then found their forbidden token inside the very comment explaining its absence. All such slices are now brace-matched and comment-stripped.
  - **Check:** `tools/prove_gates_bite.py` injects a **duplicated brace** into a `.kt` file — the exact shape of this bug — and asserts the gate goes red *via the Kotlin compiler*. Verified end to end: baseline PASS → injected `compileParentDebugKotlin FAILED` → restored PASS, file byte-for-byte identical. The previous version injected a control byte, which proved only that `verify_project.py` reads bytes and never exercised the compiler, which is precisely the hole this fell through.
  - **Standing rule this produced: after any edit to a test that slices source, run it immediately.** Three of these six failures were *introduced by the fix for the first*, so a stale-green tree accumulates damage in the act of repairing itself.
- [x] **Gate G-C11 host (re-run, post-`4a5c555`)** — GREEN: verify PASS; unit PASS both flavors; lint 0 errors 0 warnings; `functions` 13/13; **rules emulator 44/44**. **SUPERSEDED — see the G-C11 row at line 189 above: that row was recorded against a tree that did not compile, so "GREEN" here proved nothing.**
- [x] **Operator: clean `assembleParentDebug`+`assembleChildDebug`** BUILD SUCCESSFUL, `versionCode 10` / `0.3.0` confirmed in `output-metadata.json` for both flavors. Not installed.
- [x] **Operator: `firebase deploy --only firestore:rules` COMPLETE 2026-10-01.** `firestore.rules` compiled cleanly and released to `calldad-508d7`. The `chat`, `photos` (manifest + chunks), `consents`, `revocations` and `negotiationRound` stanzas are now live. Until this ran, **every one of them was denied on a real device** — so the text thread, photo sharing, the consent flow and auto-reconnect could not have worked on hardware even with a correct build. Run by the operator: `opencode.json` denies `firebase*deploy*` for this lane and the denial stands (the temporary narrow allow was removed on 2026-10-02 — the deploy is done, and an allow that outlives its one use is a hole in the law, not a permission). The permission set is read at session start, so a config change never affects the session that made it.
- [x] **Operator: flash vc10 to both phones DONE 2026-10-01** (see Contract 11 for the `dumpsys` fingerprints). The witness steps below are still all open.
- [ ] **Operator: flash vc11 / 0.3.1.** REQUIRED before any consent check below means anything — vc10, which is what is on both phones, enforces no part of the kill switch on calling or the walkie talkie.
- [ ] **Operator: witness vc11, in this order:**
  1. **First-run consent.** Shield icon top-right (beside the gear) → grown-ups gate → "Allow everything". Until then the app is deliberately inert and will look broken. The grown-up's own phone is not gated (ADR-017 `isGrantor`). The grant already exists from before the `-r` install, so this is a *check*, not a blocker — but if the app looks inert, re-grant here first.
  2. The locked-phone checks (K12 quiet PTT notification, K21 keyguard takeover) — fixed in vc10, never verified, open since vc9.
  3. A call, end to end. The existing E2E proof is from vc7.
  4. A text message both ways, including a link-shaped one (must be refused) and a 500-char one.
  5. A real photo sent, verified, rendered. No photo has ever been sent.
  6. The missed-call callback card.
  7. **An ICE restart recovered** — pull Wi-Fi mid-call or enable airplane mode, and confirm the call RECOVERS rather than ending. First ever exercise of the fix.
  8. **THE KILL SWITCH, which nothing has ever verified.** On the parent's phone, shield icon → "Turn everything off", then on the CHILD's phone confirm all of: the Call button refuses with "Calling is turned off right now"; an incoming call does not ring; the walkie talkie says "TURNED OFF" and the button does nothing; Messages and Pictures show their "ask a grown-up" notice; and **the child's phone pulls no photo chunks** (switch off mobile data first, so any download is visible as usage). Then "Allow everything" and confirm the child gets them all back. This is the single highest-value unwitnessed item in the project — it is a parental safety control, and no version of this app has ever demonstrably enforced one.
