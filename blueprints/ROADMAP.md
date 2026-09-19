# ROADMAP.md — Call-Dad phases

> Phase tracker with gates. Status flips only on evidence (CHECKPOINTS.md).

- [ ] **Phase 0 — Scaffold (this session):** directory + workflow docs + reuse map + verify script. Gate G0.
- [ ] **Phase 1 — Skeleton (BP-01):** Studio-created `app/` (`com.calldad`), Compose Nav (Home/Call/Chat/Photo/Log), Hilt, Room entities (contacts/calls/messages), first unit test + lint clean. Gate G1. Owner: human (Studio create) + executor (wire).
- [ ] **Phase 2 — Voice (BP-02):** port `CallSignalingManager` + `LiveCallSession` + `AudioFrameCipher` + `SovereignAudioEngine` (Opus) + `RealTimeTransportRouter` P1; LAN call <3s ring, E2EE voice, hangup/reconnect. Gate G2 (host) + G2-device (Moto G human proof).
- [ ] **Phase 3 — Chat + remote (BP-03):** port `SovereignCommsEngine` (text+memo+receipts) + `RealTimeTextChannel` + `RendezvousClient` + relay `main.go` deploy; remote call + chat receipt matrix. Gate G3.
- [ ] **Phase 4 — Photo + video spike (BP-04):** port `SovereignImageEngine` (chunked photo) + CameraX video spike (extend-UDP vs WebRTC per Gemini research + ADR). Offline/no-internet-direct matrix. Firebase/FCM fallback design (deferred build). Gate G4.
- [ ] **Phase 5 — Hardening (BP-05):** parent gate (`DualKeyGate` adapt), consent cert + kill switch, SQLCipher (ADR-003), kid-UX audit, no-escape audit, release-signing plan (operator keystore). Gate G5 = v0.1 shippable to kid device.

Future (not v0.1): multi-contact, group calls, cloud backup, AI, store release.
