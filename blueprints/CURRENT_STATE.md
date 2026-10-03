# CURRENT_STATE.md — Call-Dad verified map

> Updated every session per RULES.md §4.2. Executable truth > prose.
> Refreshed 2026-10-02 after Contract 11, the vc10→vc11 consent-enforcement pass, and the
> doc-truth pass that pinned the recorded counts to the suite.
> Superseded history lives in git. **"Device-proven" anywhere below means proven under the
> build named in that row** — vc5, vc6 or vc7, all superseded. No `SPEC_SHEET` §2 feature has
> been exercised on any build after vc7, including the vc11 installed on both phones.

## Toolchain (source of truth: `gradle/libs.versions.toml`, ADR-004)

AGP 8.7.2 / Kotlin 2.0.21 / Gradle 8.13 / JVM 17 / compileSdk 35 / targetSdk 35 / minSdk 26 (ADR-001 B).
Compose BOM 2024.10.01, navigation 2.8.4, lifecycle 2.8.7, coroutines 1.10.2.
Firebase BOM 34.19.0 (firestore, auth, messaging) + google-services 4.5.0. stream-webrtc-android 1.3.10.
CameraX 1.4.2 (uniform), ML Kit barcode 18.3.1 (unbundled), ZXing 3.5.3, play-services-base 18.5.0, webkit 1.12.1, DataStore 1.1.1.
Test-only: JUnit 4.13.2, coroutines-test 1.10.2, org.json 20240303. Every dependency goes through a catalog alias.
Not in the build (docs that mention them are historical): Hilt, Room, SQLCipher, KSP, OkHttp, CBOR, Concentus.

## File map (`app/src/main/java/com/calldad/`)

| Path | Role |
|------|------|
| `MainActivity.kt` | Single activity (singleTop). Routes ring-notification taps, tracks foreground, one-time full-screen-intent ask, and the API 27+-guarded runtime opt-out of `showWhenLocked`/`turnScreenOn` |
| `ui/permissions/CallPermissions.kt` | Camera/mic permission state as a first-class UI state, not a crash |
| `ui/theme/{Theme,Color,Type}.kt`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | Flavor-aware theme (blue parent / pink child); backup and device-transfer exclusions |
| `CallDadApplication.kt` | Firebase init, anonymous auth with retry, silent `incoming_call_v2` channel, token registration |
| `navigation/AppNavigation.kt`, `Routes.kt` | Graph; pulls to the ring screen from any route; game ↔ call; grown-ups gate before pairing |
| `data/session/FamilySession.kt` | Auth UID + paired peer → `FamilyPair(ownUid, peerUid, roomId)` |
| `data/signaling/SignalingClient.kt` | Room ops: publishOffer (seq+1), publishAnswer(seq), finishCall(seq), ICE trickle, observe, publishRenegotiation{,Answer} |
| `data/signaling/SignalingClient.kt:332` `CallDocument` | The pair-room document itself: ids, seq, SDP, ICE, `renegotiating`, **`negotiationRound`** (line 350), `updatedAtMs`. It was moved here from a deleted `CallDocumentTest`'s model, and it is where the Contract-11 reconnect fix lives — the rules enforce `negotiationRound` monotonicity, so this field is a security-relevant invariant, not a convenience |
| `data/signaling/SignalingModels.kt` | SdpType, SessionDescription, IceCandidate, `CallRoom` (ids, ring freshness, no-answer window) |
| `webrtc/WebRTCClient.kt` | Single-use media stack per attempt; buffered remote ICE; safe dispose order; audio mode save/restore |
| `webrtc/WebRtcConfig.kt`, `WebRtcLog.kt`, `ConnectionState.kt` | ICE servers (STUN + optional TURN), log guardrail, health enum |
| `ui/screens/CallViewModel.kt` | Activity-scoped call session: room pipeline, generations, timers, teardown |
| `ui/screens/CallState.kt` | 7-state machine + `canTransition` table (host-tested) |
| `ui/screens/CallScreen.kt` | Ring / calling / in-call (camera, mic, flip, game, hang up) / no-answer / error / permission screens |
| `ui/components/VideoRenderer.kt` | SurfaceViewRenderer host with keyed sink attach/detach |
| `ui/screens/HomeScreen.kt`, `ui/screens/HomeViewModel.kt` | Home's six doors (Call / Messages / Pictures / Walkie Talkie / Play Games / Ask Helper) and the **missed-call callback card**, derived live from `CallLogStore` (BP-05 §4) |
| `ui/components/GiantComponents.kt` | The giant-button kit Home's grid and the Call screen are built from |
| `ui/components/ParentGate.kt` | Grown-ups-only multiplication gate |
| `audio/CallAudioManager.kt` | Process-wide single ringer (ring + vibrate) and caller ringback |
| `fcm/CallMessagingService.kt`, `CallForegroundService.kt`, `PushTokenRegistrar.kt` | Push receive → foreground-first ring service; token → `users/{uid}` |
| `pairing/*`, `ui/screens/Pairing*` | QR payload v2, QR bitmap, DataStore peer store, ML Kit module check, mutual handshake |
| `game/GameWebRtcBridge.kt`, `ui/screens/GameScreen.kt`, `app/src/main/assets/game.html` | 3 games; solo pass-and-play or synced over the call's data channel |
| `ptt/*`, `ui/screens/Ptt*` | Walkie-talkie: VoiceClipPttEngine (hold-to-record clips over the pair room, ADR-016); simulated engine for host tests only |
| `helper/KeywordBot.kt`, `ui/screens/Helper*` | Offline voice helper (whole-word keyword matching) |
| `chat/ChatThread.kt`, `chat/ChatText.kt` | 1:1 Dad thread: monotonic receipts, idempotent ingest, the no-link rule (BP-03, SPEC_SHEET §2.3) |
| `chat/ChatClient.kt` | The thread over `calls/{room}/chat/`; DELIVERED is stamped by the RECEIVER, never on write success |
| `ui/screens/ChatViewModel.kt`, `ui/screens/ChatScreen.kt` | Thread UI. Plain `Text` bodies, no autoLink/intents/autocorrect, 96dp controls |
| `history/CallLog.kt`, `history/CallLogStore.kt` | The call-history **store** and the callback card's *model* (`CallLog.callbackCard`). The card is **rendered by `HomeScreen.CallbackCard`** from `HomeViewModel` — `history/` holds no UI. DataStore, NOT Room — ADR-018 |
| `consent/ConsentCert.kt` | Grant/revoke/expiry/scope gate. Absence DENIES. Revocation is a seq RANGE, not a flag — ADR-017 |
| `consent/ConsentStore.kt` | Live certs from `calls/{room}/consents/` + append-only revocations; parent-side grant/revoke |
| `photos/PhotoTransfer.kt` | Chunked, SHA-256-verified photo transport + downscale policy (BP-04) |
| `photos/PhotoClient.kt` | Send/receive over `calls/{room}/photos/`; chunks first, manifest last, unverified bytes never published |
| `ui/screens/PhotoViewModel.kt`, `ui/screens/PhotoScreen.kt` | Picture screen. Decode gated on `verified`; system photo picker only, no share/save |
| `ui/screens/ConsentScreen.kt` | Parent-side grant/revoke behind `ParentGate` — the kill switch's only trigger |

Backend: `firestore.rules`, `functions/index.js` + `functions/ring.js` + `functions/clip.js`. Tests: `app/src/test/` (27 classes, 283 tests per flavor / 566 across both flavors), `functions/ring.test.js`, `functions/clip.test.js`, `tools/rules-test/rules.test.js` (49 tests). Deploy config: `firebase.json` + `.firebaserc` (project `calldad-508d7`).

## Known-issue registry

| ID | Issue | Status |
|----|-------|--------|
| K1 | Studio sync + device install proof | **CLOSED 2026-09-30**: clean `assembleParentDebug`+`assembleChildDebug` (33 tasks executed), both installed, `dumpsys` fingerprint `versionCode=5` on Moto G 2025 (parent) and BLU View 5 (child) |
| K2 | minSdk | DECIDED 26 (ADR-001) |
| K3 | Video approach | DECIDED WebRTC, stream-webrtc-android 1.3.10 (ADR-005) |
| K4 | Signaling | DECIDED Firestore (ADR-002), pair-scoped rooms (ADR-015) |
| K5 | SQLCipher | MOOT: no Room/database in the app; ADR-003 stays unaccepted |
| K6 | No release keystore | OPEN (operator) |
| K7 | Firebase CLI on PATH | **CLOSED 2026-09-30**: Node 22.23.2 (`OpenJS.NodeJS.22`) + firebase-tools 15.32.0 installed; `firebase deploy` completed |
| K8 | No TURN: symmetric-NAT / mobile-data calls fail | **CLOSED AND DEVICE-PROVEN 2026-09-30**: a call completed with mobile data on and Wi-Fi off (operator-witnessed). Open Relay wired as the default (published long-lived creds, no account, no billing). WebRTC media stays DTLS-SRTP E2E so the relay carries ciphertext, but it is a third party in the media path and its creds are APK-extractable — accepted for a 2-person family app, NOT for real child media. `local.properties` TURN_* overrides for a private relay. **NOT yet proven:** a symmetric-NAT carrier (mobile-data pass covers the common case, not every NAT) |
| K9 | ML Kit phone-home vs RULES §1.7 | **CLOSED 2026-09-30, operator sign-off**: named exception recorded in RULES §1.7a with exactly what leaves the device (barcode model fetch + anonymous Play Services telemetry) and what does not (no image, frame, QR payload, UID, nonce, audio, video). ZXing fallback already wired. Chose ML Kit because pairing must not fail for a grown-up |
| K10 | Rules + function redeploy required: old `family_channel` clients cannot talk to new ones | **CLOSED 2026-09-30**: `firebase deploy --only firestore:rules,functions` completed; rules released, `onCallRoomWritten` live (v2, us-central1, nodejs22); both phones re-paired and called successfully. **DEPLOYED 2026-10-01** — `onPttClipWritten` live (v2, us-central1, nodejs22) and the rules released. The PTT push is still unwitnessed on a device. |
| K13 | Firestore database not provisioned — writes pend offline forever, the "Calling Dad…" hang | **CLOSED 2026-09-30, unintentionally**: the deploy's `ensuring required API firestore.googleapis.com is enabled` created `databases/(default)` (STANDARD). No console action was needed. First real call succeeded afterwards |
| K12 | Voice clips have no push yet: a killed app hears them on next open | **DEPLOYED 2026-09-30; rules re-released 2026-10-01**: `onPttClipWritten` pushes a NORMAL-priority, data-only `{type: ptt_clip, callId}` to the peer's own token. Deliberately not a ring and not high priority — a voice message at 2am must not wake the house. No audio or content crosses FCM; the clip is fetched from Firestore. `CallMessagingService` posts a quiet "A message is waiting" heads-up. **Still needs a screen-off device test** |
| K17 | `pairings/{uid}` readable by ANY signed-in install | **CLOSED 2026-09-30**: `get` now requires being the owner or the `peerUid` named in the doc. Was `request.auth != null`, so any anonymous install could read a real UID, real peer UID and a **live handshake nonce** by id — half of hijacking a pairing. `list` was already denied, but ids leak. Emulator test pins the stranger-denied case |
| K18 | Orphaned rooms/clips undeletable after a reinstall | **CLOSED 2026-09-30**: `calls` delete is allowed for a member when `status in ['ENDED','DECLINED']`; was `if false` for everything, so a reinstall's old room was written by an account that no longer existed and was undeletable by anyone, forever. Live rooms stay undeletable by anyone, tested both ways |
| K19 | No per-pair clip bound (unbounded storage if a phone stays offline) | **CLOSED 2026-09-30, operator decision**: prune only when the room holds >50 clips AND the oldest is >24h old, oldest-first. Played clips already delete immediately so a live pair never accumulates and the prune never engages. The 24h grace is what keeps a parent away for a weekend from losing a message. Prune runs AFTER the send resolves and a prune failure is never surfaced as a send failure |
| K16 | `firebase-functions@6.1.0` behind + 8 moderate advisories via `firebase-admin` 12.x | **CLOSED 2026-09-30**: bumped to firebase-functions 7.4.0 + firebase-admin 14.5.0 (major). `functions` gate 13/13 (was 6). Advisories 8 moderate → 2, both in `glob`/`teeny-request` on a path this app never touches. Lockfile re-pinned. **Deployed 2026-09-30.** |
| K11 | Device matrix for ADR-015 (killed-app ring, glare, no-answer, lost-peer end, game sync, pairing gate) | **PARTIAL, and the only remaining Phase 6/7 item that needs no code**: two-way call + answer + video + PTT + PTT tail + a mobile-data call all PROVEN on device. Remaining are human-witnessed behaviours no host test can assert: killed-app ring, force-stop, doze, no-answer timeout, lost-peer end, game sync mid-call, re-pair under the K17 read. (This row was duplicated in the table; the first copy is removed — one issue, one row.) |
| K14 | PTT clips clipped at the end of the last word when the button was released immediately | **CLOSED 2026-09-30, DEVICE-PROVEN on both phones, both directions**: no clipping, no dropped syllables. `stopTransmitting` waits `ENCODER_DRAIN_MS` (700ms) so the AAC encoder flushes before the MPEG-4 container is finalised |
| K15 | `firebase-functions@6.1.0` is behind current; the deploy CLI warned | **CLOSED — see K16** |
| K20 | Clip pruning and terminal-room deletion are client-triggered, so a pair that never opens the app never prunes | ACCEPTED: Firestore TTL would need a scheduled function and a billed index; the leak only accrues for a pair that stops using the app entirely. Cheaper than a nightly bill, and a dormant pair costs cents. Revisit if the app gains real usage |

## Last gates (2026-10-01, this lane, re-run `--rerun-tasks` against `313e566` — first recorded at `4a5c555`)

All five gates GREEN. The **rules emulator is now wired into the `gate` tool**;
before this it was silently omitted, so a whole security gate could go unreported
while the tool printed green (see `LESSONS_LEARNED.md`).

**These certify the HOST gates, not a build.** `8f47512` was pushed to
`origin/main` with a duplicated brace in `ChatViewModel.init` and this same gate
printed GREEN on it; `4a5c555` fixed it. No suite in this repo compiles the app,
so `assembleParentDebug` is the only thing that has ever caught that class, and
`tools/prove_gates_bite.py` now asserts the gate goes red on one via the compiler.

- `tools/verify_project.py`: PASS (10 dirs + 102 files, includes the control-byte ban).
- `:app:testParentDebugUnitTest :app:testChildDebugUnitTest`: **283 tests per flavor / 566 across both, 0 failures**.
- `:app:lintParentDebug :app:lintChildDebug`: PASS, **0 errors** both flavors.
- `node --test functions/*.test.js`: PASS, **13/13** (6 ring + 7 clip).
- **Firestore rules emulator: 44/44**, including the chat, photo, consent,
  kill-switch and `negotiationRound` stanzas. Run with `JAVA_HOME` pointed at the
  Studio JBR — the
  emulator refuses to start without a JVM, which is how the suite went missing
  in the first place.

### The build gate is no longer hypothetical — 2026-10-02

`clean assembleParentDebug assembleChildDebug` **BUILD SUCCESSFUL**, run under an
explicit operator override of RULES §1.5. **77 actionable tasks, 77 executed, 0
from cache** — the first run of the day reported 34 `FROM-CACHE` including
`compileParentDebugKotlin`, i.e. the compiler was skipped, so it was thrown away
and re-run with `--no-build-cache` to force `compileParentDebugKotlin` and
`compileChildDebugKotlin` to actually execute. A cached compile is not evidence
the tree compiles, and this repo has been burned by exactly that class.

`output-metadata.json` confirms `com.calldad.parent` / `versionCode 11` /
`0.3.1-parent` and `com.calldad.child` / `versionCode 11` / `0.3.1-child`.
**That was the vc11 build.** The tree has since moved to **vc12 / 0.3.2** (the
wakeup-path kill switch, 2026-10-02) and this evidence does **not** cover it —
see §3 for why vc11 and vc12 are not interchangeable.

**This retires the standing caveat.** Every row above from `8f47512` forward said
the gates certify the host and not the build; as of this commit, `e0cb047` is
the first commit in the sequence proven to compile by the compiler itself rather
than merely green.

### Three things that will surprise an operator

1. **A fresh install is inert until a parent grants — CHECK THIS FIRST.**
   Absence of a consent cert DENIES (ADR-017), so on first launch the Messages
   and Pictures tiles both read "turned off right now. Ask a grown-up." and the
   call is gated on a CALL grant. This is intended fail-closed behaviour and it
   is the single most likely cause of "the app does nothing". The fix is the
   shield icon top-right (beside the gear): the grown-ups gate, then
   "Allow everything". The grown-up's OWN phone is not gated — see ADR-017 and
   the `isGrantor` derivation in `ConsentStore`, because the rules make a
   self-named grant impossible and a naive gate would lock the parent out of the
   app they are configuring.
2. **The rules are live but unwitnessed.** Deployed 2026-10-01 and 44/44 on the
   emulator, but no phone has executed a single chat write, a photo transfer, a
   consent grant, or an ICE restart against them.
3. **The build that sat on both phones until 2026-10-02 was vc10, and vc10 lies
   about the kill switch.** Both devices now run vc11 / 0.3.1 (table below), but the
   defect is why the version bump existed: vc10 did not enforce the consent gate on
   calling or the walkie talkie in either direction and did not stop chat/photo
   downloads, so a parent on vc10 was told the app was switched off while it was not.
   **This is now the first build on the phones that should enforce the gate — which is
   exactly why it must be checked rather than assumed. An install is not a witness.**

### Device evidence (VERIFIED, `dumpsys` fingerprint)

| Device | Package | versionCode | versionName | installed | SDK |
|---|---|---|---|---|---|
| Moto G 2025 (PARENT) | `com.calldad.parent` | **11** | **0.3.1-parent** | 2026-10-02 10:18:11 | 36 |
| BLU View 5 (CHILD) | `com.calldad.child` | **11** | **0.3.1-child** | 2026-10-02 10:17:56 | 34 |
| Q8K tablet (target, unpaired) | neither | NOT INSTALLED | — | — | **30** |

**SOURCE IS vc12 / 0.3.2 AND THE PHONES ARE STILL ON vc11 / 0.3.1.** They were
the same build for one commit (`c440ad4`) and then deliberately diverged: vc11 is
the build that rings a child's killed phone after the grown-up switched calling
off, and that posts its FGS notification on the loud channel before consent can
be checked. **Do not treat the table as stale — the gap is the point.**

The Q8K is **SDK 30**, which makes it the first device in the fleet below API 31.
That is not trivia: it is why the Ask Helper tile is withheld on it. The Helper is
on-device-only (the network recognizer uploads a child's voice), and
`isOnDeviceRecognitionAvailable` is API 31+, so on Android 11 the feature is
*structurally* unavailable rather than merely unconfigured. Found by connecting
real hardware, not by reasoning about the boundary.

One flavor per device, no crossed install (each phone reports the other flavor as
NOT INSTALLED, verified before and after).

**What the install proves: that the tree compiles, packages, and installs with
its data intact. What it does not prove: anything about behaviour.** Installed
`-r`, so the anonymous Firebase account, the paired peer UID
(`peer_store.preferences_pb`, still dated 2026-09-30) and the child's existing
consent grant all survived — **no re-pair needed.** Still zero human-witnessed
feature behaviour on any build after vc7; the kill switch remains unwitnessed.

The vc7 → vc10 flash closed the widest source/device gap in the project's history,
and with it the K12 loud-voice-message and K21 lock-screen-trap defects that had
been live on the child's phone throughout. Both fixes are now **installed but
unwitnessed**.
That "unwitnessed" is not a formality, and the reason is recorded here rather than
discovered later: **vc10 was found to ship a parental kill switch that enforced
nothing on calling or the walkie talkie.** Both are the two oldest features in
the app, both predate the consent model, and neither was ever revisited. A parent
pressing "Turn everything off" on vc10 closed Messages and Pictures while calling,
inbound voice clips, and photo/chat downloads carried on regardless — and the
parent's own grant sequence was structurally unreadable, so the button could not
act on the phone that owns it. Both phones now run vc11 / 0.3.1, where the
enforcement is real in source. **Neither build has been witnessed, and vc11 is the
first one that should enforce it — which is exactly why it must be checked rather
than assumed.**

Installed with `-r`, so the anonymous Firebase account, the peer UID, and the
pairing handshake all survived — no re-pair needed, and the child kept its
consent grant from the pair room.

The Moto is a WIRELESS-adb device (mDNS), and its advertised host:port changes per
session, so resolve it at run time with `adb devices -l` rather than hardcoding
it.

Serials are deliberately absent (RULES §1.5a), and `tools/verify_project.py` now
fails the gate if one reappears in a tracked file. Resolve them at run time with
`adb devices -l` or the `device-evidence` tool, which maps roles from each
line's model field.

### Backend state (VERIFIED live)

- `firestore.rules` released to `calldad-508d7` — **2026-10-01.** Compiled cleanly and released. This stanzas-in: `chat`, `photos` (manifest + chunks), `consents`, `revocations`, and the `negotiationRound` guards on the call document. Until this deploy, every one of those was denied on a real device, so the app's text thread, photo sharing, consent flow and auto-reconnect could not have worked on hardware even with a correct build.
  - **THE LIVE RULESET IS NOW BEHIND THE TREE (2026-10-02).** `git log -- firestore.rules` shows the file was last committed at `d16fabd`, i.e. it was byte-identical to the 2026-10-01 release and has NOT since been redeployed — but today's audit pass changed it in three load-bearing ways, and the emulator proves each change against the old behaviour. **The operator must run `firebase deploy --only firestore:rules` or the running app keeps the old rules**, and each gap stays live in the field:
    1. **Consent read was member-level-broken.** `allow read: if isMember() && granteeUid in members()` cannot be proven for a query, so both of the app's filtered consent listeners were denied → the whole app inert (fail-closed) after a real grant, on BOTH phones. Now `allow read: if isMember()`.
    2. **Chunk count was unbounded.** Per-chunk 256KB with no ceiling on the INDEX meant an unbounded number of chunks (~2.5GB under an 800KB manifest). Now `index >= 0 && index < 32`.
    3. **`negotiationRound` never reset per call.** Monotonic-on-every-update plus a non-merging `set()` meant call #1's round survived into call #2, so call #2's first ICE restart was permanently `PERMISSION_DENIED`. Now monotonic within a generation, free to reset when `seq` advances.
  - **NOT fixed, by design:** a member can still write a cert naming the PEER (a "cross-grant"). It cannot be closed in the rules — the room id is two symmetric UIDs and nothing in Firestore says which is the grown-up, so denying it would deny the legitimate direction too. It needs a trust root (ADR-017). Pinned loudly as a KNOWN GAP in the emulator suite rather than hidden; see that test's banner.
- `onCallRoomWritten` live: v2, `us-central1`, nodejs22, 256MB.
- `onPttClipWritten` live: v2, same region/runtime.
- `databases/(default)` exists, STANDARD edition.

### Operator-witnessed behaviour (CLAIMED by operator, not lane-verified)

- Both phones call and answer each other; video good. **First proven E2E call.** (Proven under vc7. Re-prove under **vc11**, which is what is on both phones now — a call proved on vc10 would not have cleared the consent gate, so that proof was never worth collecting.)
- PTT works both directions.
- A call completes on **mobile data with Wi-Fi off** (operator-witnessed), so the Open Relay path is proven for a normal NAT (K8).
- PTT tail clipping FIXED and re-verified on both phones, both directions: no clipping,
  no dropped syllables (K14 closed). The operator's ears are the only instrument for this
  one — the encoder drain is an audio-domain change, so logcat could confirm the send and
  receive path was clean but could never have confirmed the audio was intact.

### NOT claimed

- **Every feature in `SPEC_SHEET.md` §2 is BUILT, DEPLOYED and INSTALLED, and none
  is WITNESSED.** `vc11 / 0.3.1` is on both phones as of 2026-10-02 (10:17:56 child,
  10:18:11 parent), and the rules are live. "Installed" is not "working": no phone
  has yet made a call, sent a text, exchanged a photo, granted a consent, or
  recovered an ICE restart against this build. Treat every §2 row as **untested on
  hardware** until an operator says otherwise, and do not read a successful install
  as evidence of any feature.
- **The two locked-phone defects are no longer live but are unverified.** A voice
  message ringing at full volume on a locked phone, and a call ring trapping the
  grown-up on their own lock screen, were both open on the child's phone under vc7.
  vc10 contained the fixes (K12 quiet PTT channel, K21 keyguard) and vc11 carries
  them. Whether they work is exactly the question the next operator session answers.
- **The Firestore rules deployed live are the vc10 set (2026-10-01).** Chat, photos, consents, revocations and `negotiationRound` are emulator-tested (44/44) AND live. The emulator suite is the only evidence for them until a phone exercises them, so the rules are now deployed-but-unwitnessed, which is a different and better state than emulator-only — but it is not "proven".
- **The consent kill switch has never been exercised on a device, and the version
  that sat on both phones until 2026-10-02 was a lie.** vc10 ships a "Turn
  everything off" button that enforced nothing on calling or the walkie talkie, in
  either direction, and did not stop photo or chat downloads. The parent's own grant
  sequence was also unreadable on the parent's phone, so the button could not act at
  all. All of it is fixed in vc11 / 0.3.1, pinned by
  `ConsentEnforcementRegressionTest`, and vc11 is **now installed on both phones**.
  Until a human grants a scope and watches a child lose it, the honest statement is
  that no version of this app has ever demonstrably enforced a kill switch.
- **vc11 is on the phones and has still never been run by a human.** That is a new
  position, not a solved one, and it is the same position v0.1 was declared
  "complete" in, twice — and both declarations were wrong. Compilation, packaging
  and installation are now proven; behaviour is not, and an install that succeeds
  is the *weakest* possible evidence that a feature works.
- **Photo sharing has never sent a real photo.** The transport, digest, ordering, downscale policy and rules are all proven (byte-proof on synthetic fixtures, 44/44 emulator), but the Bitmap→WEBP path, the picker, and the screen have never run against a camera image. That is the largest untested surface in the app.
- **`app/proguard-rules.pro` is written but UNTESTED** — 74 real keep rules (`org.webrtc.**`, Firebase/GMS, the three `fcm/` services, the WebView JS bridge, ML Kit) plus line-number tables for readable release traces — and no release build has ever run, so nothing has proved them. A missing keep rule there is a runtime crash, not a smaller APK, which is exactly why it must not be assumed correct.
- **Text chat has never survived an app restart on a device.** The prune keeps unread history, but that is a source-level claim.
- K12's screen-off PTT push, K21 (keyguard), and K17's tightened pairing read remain unproven on any build.
