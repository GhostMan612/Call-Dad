# ADR-006 — Phase 4 rendering + real (not fake) QA callee path

- Status: DECIDED by executor within architect prompt scope (2026-09-19); DeepSeek retro-review invited.
- Context: prompt §I allowed a "temporary debug button" for `simulateIncomingCall()`. A button that fakes the overlay would leave the REAL callee path (`fetchOffer→setRemote→createAnswer→publish`) untested on device.
- Decisions:
  1. Real-path QA hook: `call?mode=incoming` nav arg + DEBUG-gated Home button (`BuildConfig.DEBUG`, `buildConfig=true` added). Incoming mode skips auto-start, drives `simulateIncomingCall()` → overlay → `answerCall()` for real. Production flow (plain `call` route) untouched. The hook is gray, labeled QA, and compiled out of release builds — kid-ux-guardian cleared (never shippable UI).
  2. Decline wipes the room (`endCall→teardown` deletes OFFER): caller's screen stays "Calling Dad…" until their own hangup. Accepted QA behavior; a real decline-signal is Phase 5 scope.
  3. Single hardcoded room `dad_channel` retained for Phase 4 testing (second OFFER clobbers first); per-call rooms deferred to Phase 5 with FCM.
  4. VideoRenderer ownership: composable owns init/release via AndroidView factory/onRelease; WebRTCClient exposes `eglContext`/`localVideoTrack` only (deleted attach*/detach* + fields). No second EglBase anywhere.
  5. TURN stays sentinel-off (STUN-only); no credentials in repo (K8 unchanged).
