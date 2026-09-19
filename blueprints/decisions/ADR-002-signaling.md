# ADR-002 — v0.1 signaling: sovereign P2P-first vs Firebase-first

- Status: DECIDED — Firebase-first for CALL SIGNALING (2026-09-19, operator Phase 2 directive; overrides P2P-first recommendation).
- Decision: Firestore `calls/dad_channel` SDP + ICE signaling ships in Phase 2 (operator-provided `SignalingClient` + `CallState` + VM rewire). Sovereign P2P/rendezvous/DTN DEFERRED (media path + offline matrix revisit in BP-04). `google-services.json` stays gitignored, operator-placed in `app/`; media/E2EE keys never go through Firebase.
- Privacy note for Gemini/DeepSeek review: call MEDIA still has no transport (Phase 3 peer connection); kid thread/photo remain on-device; no FCM/auth/analytics added.
- Context: paid Firebase/Firestore available; donor stack is serverless UDP + rendezvous + DTN (no accounts/cloud). Kid privacy favors no-cloud.
- Options: A) P2P-first (LAN → rendezvous → DTN; Firebase deferred to Phase 4 FCM-wake fallback). B) Firebase-first (FCM signaling + Firestore thread sync now).
- Recommendation (executor): A — matches donor, zero cloud for v0.1, relay sees signaling only; keeps `google-services.json` out of repo until needed.
- Decision: PENDING. Blocks BP-03 relay-vs-FCM scope.
