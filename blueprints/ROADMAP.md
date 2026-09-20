# ROADMAP.md — Call-Dad phases

> Phase tracker with gates. Status flips only on evidence (CHECKPOINTS.md).

- [x] **Phase 0 — Scaffold:** workflow docs + reuse map + verify script. Gate G0 GREEN.
- [x] **Phase 1 — Skeleton:** `com.calldad` Compose Nav (Home/Call/Ptt/Game/Helper) + theme + VMs + RoutesTest. Gate G1 GREEN (host).
- [x] **Phase 2 — Signaling:** Firestore `SignalingClient` + `CallState` + VM/Screen rewire (ADR-002 DECIDED Firebase). Gate G2 GREEN (host).
- [x] **Phase 3 — Peer connection:** Stream WebRTC 1.3.10 + trickle ICE + STUN-only + callee fix (ADR-005). Gate G3 GREEN (host + device caller leg).
- [x] **Phase 4 — Rendering + incoming:** VideoRenderer + overlay + auto-popup + ringtone + crash/robustness fixes (ADR-006). Gates GREEN on device (E2E video + audio both ways, zero crashes).
- [ ] **Phase 5 — Per-call rooms + FCM + lockdown (CODE LANDED, UNPROVEN):** anonymous auth + FGS wakeup + strict rules + TURN injection (ADR-007). Needs: Studio sync, functions+rules deploy, Anonymous enable, CALLEE_UID swap-builds, killed-app test.
- [ ] **Phase 6 — Hardening:** parent gate, consent cert + kill switch, SQLCipher (ADR-003), token plumbing (`users/{uid}.fcmToken`), per-call cleanup TTL, kid-UX audit, release-signing plan. Gate = v0.1 shippable.

Future (not v0.1): multi-contact, group calls, cloud backup, AI, store release.
