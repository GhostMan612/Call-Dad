# ADR-002 — v0.1 signaling: sovereign P2P-first vs Firebase-first

- Status: PROPOSED (owners: DeepSeek + Gemini).
- Context: paid Firebase/Firestore available; donor stack is serverless UDP + rendezvous + DTN (no accounts/cloud). Kid privacy favors no-cloud.
- Options: A) P2P-first (LAN → rendezvous → DTN; Firebase deferred to Phase 4 FCM-wake fallback). B) Firebase-first (FCM signaling + Firestore thread sync now).
- Recommendation (executor): A — matches donor, zero cloud for v0.1, relay sees signaling only; keeps `google-services.json` out of repo until needed.
- Decision: PENDING. Blocks BP-03 relay-vs-FCM scope.
