# CURRENT_STATE.md — Call-Dad verified map

> Updated every session per RULES.md §4.2. Executable truth > prose.
> Refreshed 2026-09-24 after the full-repo sweep + fix (ADR-015). Superseded history lives in git.

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
| `ptt/*`, `ui/screens/Ptt*` | Walkie-talkie (simulated engine unless the private module is present) |
| `helper/KeywordBot.kt`, `ui/screens/Helper*` | Offline voice helper (whole-word keyword matching) |

Backend: `firestore.rules`, `functions/index.js` + `functions/ring.js`. Tests: `app/src/test/` (7 classes), `functions/ring.test.js`, `tools/rules-test/rules.test.js`.

## Known-issue registry

| ID | Issue | Status |
|----|-------|--------|
| K1 | Studio sync + device install proof | OPEN (operator) |
| K2 | minSdk | DECIDED 26 (ADR-001) |
| K3 | Video approach | DECIDED WebRTC, stream-webrtc-android 1.3.10 (ADR-005) |
| K4 | Signaling | DECIDED Firestore (ADR-002), pair-scoped rooms (ADR-015) |
| K5 | SQLCipher | MOOT: no Room/database in the app; ADR-003 stays unaccepted |
| K6 | No release keystore | OPEN (operator) |
| K7 | Firebase CLI on PATH | OPEN for deploys (operator runs `firebase deploy`) |
| K8 | No TURN: symmetric-NAT / mobile-data calls can fail; TURN creds in BuildConfig can be extracted from the APK | OPEN: provider + short-lived credential issuer (operator decision) |
| K9 | ML Kit phone-home vs RULES §1.7 | OPEN: keep, or ZXing-only decode (operator) |
| K10 | Rules + function redeploy required: old `family_channel` clients cannot talk to new ones | OPEN: deploy, then re-pair both phones |
| K11 | Device matrix for ADR-015 (ring from every screen, killed-app ring, glare, no-answer, lost-peer end, game sync, pairing gate) | OPEN (operator) |

## Last gates (2026-09-24, this lane, cloud container with a local Android SDK)

- `:app:testParentDebugUnitTest :app:testChildDebugUnitTest`: PASS (80 tests, 0 failures).
- `:app:lintParentDebug :app:lintChildDebug`: 0 errors. Remaining warnings are dependency-version nags (frozen by ADR-004), portrait lock and missing launcher icon (pre-existing).
- Kotlin compile: 0 warnings, both flavors.
- `node --test functions/`: 6/6. Firestore rules emulator suite: 15/15.
- `tools/verify_project.py`: PASS.
- Game protocol: headless two-WebView simulation (solo, synced, validation, resync) PASS.
- NOT claimed: assemble, install, any on-device behavior.
