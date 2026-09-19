# Firebase / Firestore plan (paid account — DEFERRED, Phase 4 fallback)

- Status: account available; **zero `google-services.json` in repo or `C:\android` (verified 2026-09-19) — intentional.**
- v0.1 (BP-01..05): NO Firebase. Sovereign P2P-first: LAN → rendezvous relay (`main.go` :8792/udp, signaling only) → DTN. Keeps kid thread/photo off-cloud, zero accounts.
- Phase 4 fallback (design in BP-04, build only if ADR-002 authorizes): FCM data-message wake for NAT-hard remote (Dad device asleep / carrier NAT), optional Firestore presence/thread mirror with parent opt-in + redaction default. Keys/media never go through Firebase; signaling tokens only.
- If authorized later: operator downloads `google-services.json` into `app/` (gitignored), adds `com.google.gms.google-services` plugin + BOM pins via ADR, executor wires opt-in flag (default OFF). Service-account files never enter repo.
- R&D (Gemini): validate FCM-vs-relay wake latency + privacy trade; Architect (DeepSeek): rule ADR-002.
