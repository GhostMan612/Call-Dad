# CURRENT_STATE.md — Call-Dad verified map

> Updated every session per RULES.md §4.2. Executable truth > prose.
> Refreshed 2026-09-30 after Contract 10: first device-proven build, install and
> two-way call. Superseded history lives in git.

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
| `MainActivity.kt` | Single activity (singleTop). Routes ring-notification taps, tracks foreground, one-time full-screen-intent ask |
| `CallDadApplication.kt` | Firebase init, anonymous auth with retry, silent `incoming_call_v2` channel, token registration |
| `navigation/AppNavigation.kt`, `Routes.kt` | Graph; pulls to the ring screen from any route; game ↔ call; grown-ups gate before pairing |
| `data/session/FamilySession.kt` | Auth UID + paired peer → `FamilyPair(ownUid, peerUid, roomId)` |
| `data/signaling/SignalingClient.kt` | Room ops: publishOffer (seq+1), publishAnswer(seq), finishCall(seq), ICE trickle, observe |
| `data/signaling/SignalingModels.kt` | SdpType, SessionDescription, IceCandidate, `CallRoom` (ids, ring freshness, no-answer window) |
| `webrtc/WebRTCClient.kt` | Single-use media stack per attempt; buffered remote ICE; safe dispose order; audio mode save/restore |
| `webrtc/WebRtcConfig.kt`, `WebRtcLog.kt`, `ConnectionState.kt` | ICE servers (STUN + optional TURN), log guardrail, health enum |
| `ui/screens/CallViewModel.kt` | Activity-scoped call session: room pipeline, generations, timers, teardown |
| `ui/screens/CallState.kt` | 7-state machine + `canTransition` table (host-tested) |
| `ui/screens/CallScreen.kt` | Ring / calling / in-call (camera, mic, flip, game, hang up) / no-answer / error / permission screens |
| `ui/components/VideoRenderer.kt` | SurfaceViewRenderer host with keyed sink attach/detach |
| `ui/components/ParentGate.kt` | Grown-ups-only multiplication gate |
| `audio/CallAudioManager.kt` | Process-wide single ringer (ring + vibrate) and caller ringback |
| `fcm/CallMessagingService.kt`, `CallForegroundService.kt`, `PushTokenRegistrar.kt` | Push receive → foreground-first ring service; token → `users/{uid}` |
| `pairing/*`, `ui/screens/Pairing*` | QR payload v2, QR bitmap, DataStore peer store, ML Kit module check, mutual handshake |
| `game/GameWebRtcBridge.kt`, `ui/screens/GameScreen.kt`, `assets/game.html` | 3 games; solo pass-and-play or synced over the call's data channel |
| `ptt/*`, `ui/screens/Ptt*` | Walkie-talkie: VoiceClipPttEngine (hold-to-record clips over the pair room, ADR-016); simulated engine for host tests only |
| `helper/KeywordBot.kt`, `ui/screens/Helper*` | Offline voice helper (whole-word keyword matching) |
| `chat/ChatThread.kt`, `chat/ChatText.kt` | 1:1 Dad thread: monotonic receipts, idempotent ingest, the no-link rule (BP-03, SPEC_SHEET §2.3) |
| `chat/ChatClient.kt` | The thread over `calls/{room}/chat/`; DELIVERED is stamped by the RECEIVER, never on write success |
| `ui/screens/ChatViewModel.kt`, `ui/screens/ChatScreen.kt` | Thread UI. Plain `Text` bodies, no autoLink/intents/autocorrect, 96dp controls |
| `history/CallLog.kt`, `history/CallLogStore.kt` | Call history + the missed-call callback card (BP-05 §4). DataStore, NOT Room — ADR-018 |
| `consent/ConsentCert.kt` | Grant/revoke/expiry/scope gate. Absence DENIES. Revocation is a seq RANGE, not a flag — ADR-017 |
| `consent/ConsentStore.kt` | Live certs from `calls/{room}/consents/` + append-only revocations; parent-side grant/revoke |
| `photos/PhotoTransfer.kt` | Chunked, SHA-256-verified photo transport + downscale policy (BP-04) |
| `photos/PhotoClient.kt` | Send/receive over `calls/{room}/photos/`; chunks first, manifest last, unverified bytes never published |
| `ui/screens/PhotoViewModel.kt`, `ui/screens/PhotoScreen.kt` | Picture screen. Decode gated on `verified`; system photo picker only, no share/save |
| `ui/screens/ConsentScreen.kt` | Parent-side grant/revoke behind `ParentGate` — the kill switch's only trigger |

Backend: `firestore.rules`, `functions/index.js` + `functions/ring.js` + `functions/clip.js`. Tests: `app/src/test/` (14 classes), `functions/ring.test.js`, `functions/clip.test.js`, `tools/rules-test/rules.test.js` (40 tests). Deploy config: `firebase.json` + `.firebaserc` (project `calldad-508d7`).

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
| K10 | Rules + function redeploy required: old `family_channel` clients cannot talk to new ones | **CLOSED 2026-09-30**: `firebase deploy --only firestore:rules,functions` completed; rules released, `onCallRoomWritten` live (v2, us-central1, nodejs22); both phones re-paired and called successfully. **RE-REDEPLOY NEEDED for the K9/K12 rules + `onPttClipWritten`** |
| K13 | Firestore database not provisioned — writes pend offline forever, the "Calling Dad…" hang | **CLOSED 2026-09-30, unintentionally**: the deploy's `ensuring required API firestore.googleapis.com is enabled` created `databases/(default)` (STANDARD). No console action was needed. First real call succeeded afterwards |
| K12 | Voice clips have no push yet: a killed app hears them on next open | **CLOSED in source 2026-09-30, awaiting deploy**: `onPttClipWritten` pushes a NORMAL-priority, data-only `{type: ptt_clip, callId}` to the peer's own token. Deliberately not a ring and not high priority — a voice message at 2am must not wake the house. No audio or content crosses FCM; the clip is fetched from Firestore. `CallMessagingService` posts a quiet "A message is waiting" heads-up. **Needs deploy + a screen-off device test** |
| K17 | `pairings/{uid}` readable by ANY signed-in install | **CLOSED 2026-09-30**: `get` now requires being the owner or the `peerUid` named in the doc. Was `request.auth != null`, so any anonymous install could read a real UID, real peer UID and a **live handshake nonce** by id — half of hijacking a pairing. `list` was already denied, but ids leak. Emulator test pins the stranger-denied case |
| K18 | Orphaned rooms/clips undeletable after a reinstall | **CLOSED 2026-09-30**: `calls` delete is allowed for a member when `status in ['ENDED','DECLINED']`; was `if false` for everything, so a reinstall's old room was written by an account that no longer existed and was undeletable by anyone, forever. Live rooms stay undeletable by anyone, tested both ways |
| K19 | No per-pair clip bound (unbounded storage if a phone stays offline) | **CLOSED 2026-09-30, operator decision**: prune only when the room holds >50 clips AND the oldest is >24h old, oldest-first. Played clips already delete immediately so a live pair never accumulates and the prune never engages. The 24h grace is what keeps a parent away for a weekend from losing a message. Prune runs AFTER the send resolves and a prune failure is never surfaced as a send failure |
| K16 | `firebase-functions@6.1.0` behind + 8 moderate advisories via `firebase-admin` 12.x | **CLOSED 2026-09-30**: bumped to firebase-functions 7.4.0 + firebase-admin 14.5.0 (major). `functions` gate 13/13 (was 6). Advisories 8 moderate → 2, both in `glob`/`teeny-request` on a path this app never touches. Lockfile re-pinned. **Needs deploy** |
| K11 | Device matrix for ADR-015 (killed-app ring, glare, no-answer, lost-peer end, game sync, pairing gate) | **PARTIAL, and the only remaining Phase 6/7 item that needs no code**: two-way call + answer + video + PTT + PTT tail + a mobile-data call all PROVEN on device. Remaining are human-witnessed behaviours no host test can assert: killed-app ring, force-stop, doze, no-answer timeout, lost-peer end, game sync mid-call, re-pair under the K17 read. (This row was duplicated in the table; the first copy is removed — one issue, one row.) |
| K14 | PTT clips clipped at the end of the last word when the button was released immediately | **CLOSED 2026-09-30, DEVICE-PROVEN on both phones, both directions**: no clipping, no dropped syllables. `stopTransmitting` waits `ENCODER_DRAIN_MS` (700ms) so the AAC encoder flushes before the MPEG-4 container is finalised |
| K15 | `firebase-functions@6.1.0` is behind current; the deploy CLI warned | **CLOSED — see K16** |
| K20 | Clip pruning and terminal-room deletion are client-triggered, so a pair that never opens the app never prunes | ACCEPTED: Firestore TTL would need a scheduled function and a billed index; the leak only accrues for a pair that stops using the app entirely. Cheaper than a nightly bill, and a dormant pair costs cents. Revisit if the app gains real usage |

## Last gates (2026-10-01, this lane)

All five gates GREEN. The **rules emulator is now wired into the `gate` tool**;
before this it was silently omitted, so a whole security gate could go unreported
while the tool printed green (see `LESSONS_LEARNED.md`).

- `tools/verify_project.py`: PASS.
- `:app:testParentDebugUnitTest :app:testChildDebugUnitTest`: PASS, both flavors.
- `:app:lintParentDebug :app:lintChildDebug`: PASS, 0 errors, 0 Kotlin warnings.
- `node --test functions/*.test.js`: PASS.
- **Firestore rules emulator: 40/40**, including the chat, photo, consent and
  kill-switch stanzas. Run with `JAVA_HOME` pointed at the Studio JBR — the
  emulator refuses to start without a JVM, which is how the suite went missing
  in the first place.

### Two things that will surprise an operator

1. **The new rules are NOT deployed.** `chat`, `photos`, `consents` and
   `revocations` exist in `firestore.rules` and are emulator-tested, but the live
   rules are still the vc9 set. `firebase deploy --only firestore:rules` is
   required before chat or consent work on a phone.
2. **A fresh install is inert until a parent grants.** Absence of a consent cert
   DENIES (ADR-017), so text and — as wired today — the whole app's communication
   entry points are closed by default. This is intended fail-closed behaviour,
   and it is the single most likely cause of "the app does nothing" on first run.

### Device evidence (VERIFIED, `dumpsys` fingerprint)

| Device | Package | versionCode | versionName |
|---|---|---|---|
| Moto G 2025 (PARENT) | `com.calldad.parent` | 7 | 0.2.4-parent |
| BLU View 5 (CHILD) | `com.calldad.child` | 7 | 0.2.4-child |

Source is at **versionCode 10 / 0.3.0** — ahead of both phones. See "NOT claimed".

Serials are deliberately absent (RULES §1.5a), and `tools/verify_project.py` now
fails the gate if one reappears in a tracked file. Resolve them at run time with
`adb devices -l` or the `device-evidence` tool, which maps roles from each
line's model field.

One flavor per device; no crossed install.

### Backend state (VERIFIED live)

- `firestore.rules` released to `calldad-508d7`.
- `onCallRoomWritten` live: v2, `us-central1`, nodejs22, 256MB.
- `databases/(default)` exists, STANDARD edition.

### Operator-witnessed behaviour (CLAIMED by operator, not lane-verified)

- Both phones call and answer each other; video good. **First proven E2E call.** (vc7, still current on hardware.)
- PTT works both directions.
- A call completes on **mobile data with Wi-Fi off** (operator-witnessed), so the Open Relay path is proven for a normal NAT (K8).
- PTT tail clipping FIXED and re-verified on both phones, both directions: no clipping,
  no dropped syllables (K14 closed). The operator's ears are the only instrument for this
  one — the encoder drain is an audio-domain change, so logcat could confirm the send and
  receive path was clean but could never have confirmed the audio was intact.

### NOT claimed

- **`versionCode 10 / 0.3.0` is SOURCE-ONLY.** Never built, never installed, never witnessed. Both phones are on **vc7 / 0.2.4**, which means two operator-reported defects are **live on the child's phone right now**: a voice message rings at full volume on a locked phone, and a call ring traps the grown-up on their own lock screen. The fixes are gated and reviewed in source; the device proof is what closes this. **This remains the single highest-priority open item.**
- **The Firestore rules deployed live are the vc9-era set.** Chat, photos, consents and revocations are emulator-tested (40/40) and **not deployed**. No new feature works on a device until `firebase deploy --only firestore:rules` runs.
- **The consent kill switch has never been exercised on a device.** The rules are proven against the emulator and the gate logic is host-tested, but no human has granted a scope and watched a child lose it.
- **Photo sharing has never sent a real photo.** The transport, digest, ordering, downscale policy and rules are all proven (byte-proof on synthetic fixtures, 40/40 emulator), but the Bitmap→WEBP path, the picker, and the screen have never run against a camera image. That is the largest untested surface in the app.
- **`app/proguard-rules.pro` is a stub** and no release build has ever run. A missing keep rule there is a runtime crash, not a smaller APK.
- **Text chat has never survived an app restart on a device.** The prune keeps unread history, but that is a source-level claim.
- K12's screen-off PTT push, K21 (keyguard), and K17's tightened pairing read remain unproven on any build.
