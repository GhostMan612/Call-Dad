# Firebase / Firestore plan (paid account — ACTIVE for Phase 2 signaling)

- Status: ACTIVE for call signaling (2026-09-19 operator directive, ADR-002 DECIDED). Operator places `google-services.json` into `app/` (gitignored, verify-banned) — without it the first Firestore call throws `IllegalStateException: Default FirebaseApp is not initialized`.
- Phase 2 scope: Firestore `calls/dad_channel` SDP OFFER/ANSWER + `candidates` ICE trickle via `SignalingClient`; INTERNET + ACCESS_NETWORK_STATE granted; RECORD_AUDIO/CAMERA stay commented until Phase 3 peer connection. No FCM/auth/analytics.
- Privacy: media/E2EE keys never via Firebase; kid thread/photo on-device; sovereign P2P/rendezvous DEFERRED to BP-04 revisit.

- History: account available since scaffold; `google-services.json` absent from repo (verified 2026-09-19). P2P-first was the v0.1 plan until the operator's Phase 2 directive.
- Service-account files never enter repo. FCM/auth/analytics NOT added (future phases only, need ADR).
- R&D (Gemini): review `SignalingClient` error mapping + offline UX; Architect (DeepSeek): confirm media-path privacy before Phase 3 peer connection.
