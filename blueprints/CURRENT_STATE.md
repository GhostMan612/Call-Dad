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

Backend: `firestore.rules`, `functions/index.js` + `functions/ring.js`. Tests: `app/src/test/` (9 classes), `functions/ring.test.js`, `tools/rules-test/rules.test.js`. Deploy config: `firebase.json` + `.firebaserc` (project `calldad-508d7`).

## Known-issue registry

| ID | Issue | Status |
|----|-------|--------|
| K1 | Studio sync + device install proof | **CLOSED 2026-09-30**: clean `assembleParentDebug`+`assembleChildDebug` (33 tasks executed), both installed, `dumpsys` fingerprint `versionCode=5` on Moto G 2025 (`ZT4222BMWN`) and BLU View 5 (`7040016025040287`) |
| K2 | minSdk | DECIDED 26 (ADR-001) |
| K3 | Video approach | DECIDED WebRTC, stream-webrtc-android 1.3.10 (ADR-005) |
| K4 | Signaling | DECIDED Firestore (ADR-002), pair-scoped rooms (ADR-015) |
| K5 | SQLCipher | MOOT: no Room/database in the app; ADR-003 stays unaccepted |
| K6 | No release keystore | OPEN (operator) |
| K7 | Firebase CLI on PATH | **CLOSED 2026-09-30**: Node 22.23.2 (`OpenJS.NodeJS.22`) + firebase-tools 15.32.0 installed; `firebase deploy` completed |
| K8 | No TURN: symmetric-NAT / mobile-data calls can fail; TURN creds in BuildConfig can be extracted from the APK | OPEN: provider + short-lived credential issuer (operator decision) |
| K9 | ML Kit phone-home vs RULES §1.7 | OPEN: keep, or ZXing-only decode (operator) |
| K10 | Rules + function redeploy required: old `family_channel` clients cannot talk to new ones | **CLOSED 2026-09-30**: `firebase deploy --only firestore:rules,functions` completed; rules released, `onCallRoomWritten` live (v2, us-central1, nodejs22); both phones re-paired and called successfully |
| K13 | Firestore database not provisioned — writes pend offline forever, the "Calling Dad…" hang | **CLOSED 2026-09-30, unintentionally**: the deploy's `ensuring required API firestore.googleapis.com is enabled` created `databases/(default)` (STANDARD). No console action was needed. First real call succeeded afterwards |
| K12 | Voice clips have no push yet: a killed app hears them on next open | OPEN (ADR-016 next step). PTT otherwise PROVEN two-way on device 2026-09-30 |
| K11 | Device matrix for ADR-015 (killed-app ring, glare, no-answer, lost-peer end, game sync, pairing gate) | PARTIAL: two-way call + answer + video + PTT PROVEN. Remaining items OPEN (operator) |
| K14 | PTT clips clipped at the end of the last word when the button was released immediately | **CLOSED 2026-09-30, DEVICE-PROVEN on both phones, both directions**: no clipping, no dropped syllables. `stopTransmitting` waits `ENCODER_DRAIN_MS` (700ms) so the AAC encoder flushes before the MPEG-4 container is finalised |
| K15 | `firebase-functions@6.1.0` is behind current; the deploy CLI warned | OPEN: deliberate upgrade commit with its own gate run, not a deploy-day change |
| K16 | `functions/package-lock.json` now pins the deploy tree; 8 moderate advisories inherited via firebase-admin 12.x → deprecated `uuid@9/10` | OPEN (operator): dependency bump is a separate contract |

## Last gates (2026-09-30, this lane)

All five gates GREEN. The two Node gates were SKIPPED for the whole of Contract 9 and
are no longer: Node 22.23.2 and Java (Android Studio JBR) were already on the machine
but not on this lane's PATH.

- `tools/verify_project.py`: PASS (10 dirs + 102 git-tracked files, no secrets).
- `:app:testParentDebugUnitTest :app:testChildDebugUnitTest`: PASS, 55 tests, 0 failures.
- `:app:lintParentDebug :app:lintChildDebug`: PASS, 0 errors, 0 Kotlin warnings.
- `node --test functions/ring.test.js`: 6/6.
- Firestore rules emulator suite: 17/17. 22 "evaluation error" log lines were
  investigated with throwaway probes and are cosmetic: all six legitimate member
  operations return ALLOWED with no error, so no denial is masking a real allow.
- `clean assembleParentDebug assembleChildDebug`: BUILD SUCCESSFUL, 33 tasks executed
  from scratch (not up-to-date reuse). 0 errors, 0 warnings.

### Device evidence (VERIFIED, `dumpsys` fingerprint, 2026-09-30)

| Device | Serial | Package | versionCode | versionName |
|---|---|---|---|---|
| Moto G 2025 (PARENT) | `ZT4222BMWN` | `com.calldad.parent` | 5 | 0.2.2-parent |
| BLU View 5 (CHILD) | `7040016025040287` | `com.calldad.child` | 5 | 0.2.2-child |

One flavor per device; no crossed install. Both match `app/build.gradle.kts`.

### Backend state (VERIFIED live)

- `firestore.rules` released to `calldad-508d7`.
- `onCallRoomWritten` live: v2, `us-central1`, nodejs22, 256MB.
- `databases/(default)` exists, STANDARD edition.

### Operator-witnessed behaviour (CLAIMED by operator, not lane-verified)

- Both phones call and answer each other; video good. **First proven E2E call.**
- PTT works both directions.
- PTT tail clipping FIXED and re-verified on both phones, both directions: no clipping,
  no dropped syllables (K14 closed). The operator's ears are the only instrument for this
  one — the encoder drain is an audio-domain change, so logcat could confirm the send and
  receive path was clean but could never have confirmed the audio was intact.

### NOT claimed

- No `logcat` evidence has been read by this lane.
- PTT drain fix is **unverified on device** — it is in source only until vc6 is flashed.
- Killed-app ring, doze, force-stop, mobile-data/TURN, and multi-generation call
  sequences remain unproven.
