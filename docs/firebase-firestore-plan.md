# Firebase / Firestore plan (paid account — Phase 5: per-call rooms + locked rules)

> **Current schema as DEPLOYED (ADR-015 room shape; stanza set released 2026-10-01 to `calldad-508d7`):**
> - `calls/{uidA_uidB}` (sorted UIDs): {status, seq, callerUid, calleeUid, offer, answer, callerCandidates, calleeCandidates, **renegotiating**, **negotiationRound**, updatedAt}. Only the two UIDs in the id can read or write it, and `negotiationRound` is monotonic by rule (ADR-010 §7) — that field is the whole ICE-restart fix.
> - `users/{uid}.fcmToken`: owner-only.
> - `pairings/{uid}`: handshake {peerUid, sessionNonce, expiresAt}; get by id only, no list; delete only after the handshake expires.
> - `calls/{uidA_uidB}/ptt/`, `chat/`, `photos/{photoId}` + `photos/{photoId}/chunks/{index}`, `consents/{granteeUid}`, `revocations/{revocationId}` — one level below the room, which is the only shape the rules can evaluate reliably.
> - Cloud Function `onCallRoomWritten` pushes to the callee's token; `onPttClipWritten` pushes a clip.
> - The original schema in ADR-015 (2026-09-24) had no `renegotiating`/`negotiationRound` and none of the subcollections above; they were added by Contracts 10–12. 44 emulator cases pin the deployed set.
> - Everything further below is history (`dad_channel`, `ring/dad`, `family_channel` + topic).

- Status: ACTIVE for call signaling (2026-09-19 operator directive, ADR-002 DECIDED). Operator places `google-services.json` into `app/` (gitignored, verify-banned) — without it the first Firestore call throws `IllegalStateException: Default FirebaseApp is not initialized`.
- Phase 2 scope: Firestore `calls/dad_channel` SDP OFFER/ANSWER + `candidates` ICE trickle via `SignalingClient`; INTERNET + ACCESS_NETWORK_STATE granted; RECORD_AUDIO/CAMERA stay commented until Phase 3 peer connection. No FCM/auth/analytics.
- Privacy: media/E2EE keys never via Firebase; kid thread/photo on-device; sovereign P2P/rendezvous DEFERRED to BP-04 revisit.

- History: account available since scaffold; `google-services.json` absent from repo (verified 2026-09-19). P2P-first was the v0.1 plan until the operator's Phase 2 directive.
- Service-account files never enter repo. FCM/auth/analytics NOT added (future phases only, need ADR).
- R&D (Gemini): review `SignalingClient` error mapping + offline UX; Architect (DeepSeek): confirm media-path privacy before Phase 3 peer connection.
