# ADR-006 — Phase 4 rendering + real (not fake) QA callee path

- Status: DECIDED by executor within architect prompt scope (2026-09-19); DeepSeek retro-review invited.
- Context: prompt §I allowed a "temporary debug button" for `simulateIncomingCall()`. A button that fakes the overlay would leave the REAL callee path (`fetchOffer→setRemote→createAnswer→publish`) untested on device.
- Decisions:
  1. Real-path QA hook: `call?mode=incoming` nav arg + DEBUG-gated Home button (`BuildConfig.DEBUG`, `buildConfig=true` added). Incoming mode skips auto-start, drives `simulateIncomingCall()` → overlay → `answerCall()` for real. Production flow (plain `call` route) untouched. The hook is gray, labeled QA, and compiled out of release builds — kid-ux-guardian cleared (never shippable UI).
  2. Decline wipes the room (`endCall→teardown` deletes OFFER): caller's screen stays "Calling Dad…" until their own hangup. Accepted QA behavior; a real decline-signal is Phase 5 scope.
  3. Single hardcoded room `dad_channel` retained for Phase 4 testing (second OFFER clobbers first); per-call rooms deferred to Phase 5 with FCM.
  4. VideoRenderer ownership: composable owns init/release via AndroidView factory/onRelease; WebRTCClient exposes `eglContext`/`localVideoTrack` only (deleted attach*/detach* + fields). No second EglBase anywhere.
  5. TURN stays sentinel-off (STUN-only); no credentials in repo (K8 unchanged).
  6. AUTO-POPUP (operator-asked, 2026-09-19): HomeViewModel listens for NEW OFFERs (deduped by SDP hash) and auto-navigates to the incoming overlay; overlay plays system ringtone + vibration via scoped DisposableEffect (silenced on leave). Home-scoped VM = listener dies off-Home (no drain, no yanking). QA button REMOVED once auto-popup proven (operator-asked). Killed-app wakeup still Phase 5 (FCM). VIBRATE is a normal (auto-grant) permission.
  7. REMOTE HANGUP (operator-asked, 2026-09-19): `observeRoomDeleted` (present-then-gone only, started post-publish) → `endCall()` in both roles; CallScreen auto-homes on any return-to-Idle after activity. `mode=incoming` route + `simulateIncomingCall` retained as the Phase 5 FCM entry point.
  8. SELF-RING (operator bug, 2026-09-19): double-call/hangup made a phone hear its OWN offer (shared room, no sender identity) and ring itself; answering own stale offer → ghost InCall. Fix: process-scoped `OwnOfferRegistry` (published offer SDPs) skipped by the Home listener AND `fetchOffer`; caller pre-publish wipe retained. versionCode 3 = lane-checkable build fingerprint via dumpsys.
