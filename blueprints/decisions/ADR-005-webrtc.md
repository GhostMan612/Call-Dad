# ADR-005 — Phase 3 WebRTC: Stream fork + trickle ICE + STUN-only

- Status: DECIDED by architect (DeepSeek, 2026-09-19 via pipeline prompt); executed this session.
- Context: `google-webrtc` archived 2021 (no targetSdk 34+ support, no 16KB page-size support). Phase 2 built a `candidates` subcollection; Phase 2 callee path had a latent re-apply-OFFER bug.
- Decisions:
  1. `io.getstream:stream-webrtc-android:1.1.0` — drop-in (`org.webrtc.*` names unchanged). Cost: ~20MB APK native libs (acceptable for family app; flag if ever public).
  1b. COORDINATES CORRECTION (executor, Maven Central proof 2026-09-19): `webrtc-android:1.1.0` does not exist — real artifact is `io.getstream:stream-webrtc-android`, latest stable **1.3.10**. Alias `libs.webrtc.android` unchanged so no other file moves. DeepSeek retro-review invited.
  2. Trickle ICE (`GATHER_CONTINUALLY`) — publish SDP right after `setLocalDescription`, stream candidates; matches existing schema (fat-SDP alternative would orphan it).
  3. STUN-only, NO TURN — symmetric-NAT calls WILL fail. Correct for Phase 3 (data layer is the deliverable); Phase 4/5 blocker, not a field-discovery. Carried as K8.
  4. Callee fix: description-observation scoped to caller only (`listenForAnswer`); callee applies fetched OFFER directly.
  5. Logging guardrail: all WebRTC-touching logs via `WebRtcLog.transition` (fixed strings / enum names only; never SDP/ICE/IPs/room IDs). KDoc on `WebRtcLog` is a STANDING RULES-§2 EXCEPTION (safety contract, architect-mandated).
- Executor fixes on top of the prompt (soundness, non-negotiable):
  1. Prompt's `CallViewModel(application)` + default `viewModel()` would CRASH on navigation (no zero-arg constructor) — added manual `ViewModelProvider.Factory` (`callViewModel()`).
  2. Package rewritten `com.calldad.app.*` → `com.calldad.*` (operator order).
  3. Prompt delta H.2 (explicit Connecting branch) verified already-present — no-op, layout untouched. No renderers added (Phase 4 scope respected).
