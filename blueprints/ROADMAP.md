# ROADMAP.md — Call-Dad phases

> Phase tracker with gates. Status flips only on evidence (CHECKPOINTS.md).

- [x] **Phase 0 — Scaffold:** workflow docs + reuse map + verify script. Gate G0 GREEN.
- [x] **Phase 1 — Skeleton:** `com.calldad` Compose Nav (Home/Call/Ptt/Game/Helper) + theme + VMs + RoutesTest. Gate G1 GREEN (host).
- [x] **Phase 2 — Signaling:** Firestore `SignalingClient` + `CallState` + VM/Screen rewire (ADR-002 DECIDED Firebase). Gate G2 GREEN (host).
- [x] **Phase 3 — Peer connection:** Stream WebRTC 1.3.10 + trickle ICE + STUN-only + callee fix (ADR-005). Gate G3 GREEN (host + device caller leg).
- [x] **Phase 4 — Rendering + incoming:** VideoRenderer + overlay + auto-popup + ringtone + crash/robustness fixes (ADR-006). Gates GREEN on device (E2E video + audio both ways, zero crashes).
- [x] **Phase 5 — Per-call rooms + FCM + lockdown (PROVEN ON DEVICE 2026-09-30):** anonymous auth + FGS wakeup + strict pair-scoped rules (ADR-015) + token-targeted FCM (ADR-016). Deployed live: rules released, `onCallRoomWritten` v2 us-central1 nodejs22, `databases/(default)` created. First end-to-end call proven: both phones call and answer, video good, PTT both ways, `dumpsys` vc5 on both. Still open inside this phase: **TURN unprovisioned (K8)**, killed-app/doze/force-stop ring untested, Anonymous sign-in enablement not separately confirmed in console.
- [ ] **Phase 6 — Hardening:** parent gate, consent cert + kill switch, SQLCipher (ADR-003), per-pair clip bound, orphan cleanup after reinstall, kid-UX audit, release-signing plan, dependency bump (`firebase-functions` 6.1.0 behind + 8 moderate advisories via `firebase-admin` 12.x). Gate = v0.1 shippable.
- [x] **Phase 6a — PTT encoder-drain re-test (K14):** CLOSED 2026-09-30. vc6/0.2.3 clean-built and installed to both phones; operator-witnessed both directions, no clipping and no dropped syllables. The 700ms silent tail lets the AAC encoder flush before the MPEG-4 container is finalised.

Future (not v0.1): multi-contact, group calls, cloud backup, AI, store release.
