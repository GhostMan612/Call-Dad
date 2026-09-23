# ADR-014 — Pairing architecture (executor, Contracts 4–7)

- Status: DECIDED by executor within prompt scope (2026-09-21/22); DeepSeek/Gemini retro-review invited.

1. **Plaintext DataStore, no Tink.** Two-person sideloaded threat model excludes rooted devices/adb backup. Mitigations: `allowBackup="false"` + `data_extraction_rules.xml` (D2D block, API 31+) + `backup_rules.xml` (API ≤30) + pairing_token never persisted (ViewModel memory only).
2. **QR payload is plaintext JSON** `{v, uid, fcm, nonce}`. Photograph-replayable within the nonce window — accepted for a two-person family app.
3. **Pairing is two independent one-way stores**, NOT a synced handshake at the store layer: each device scans the other and persists locally. Mutual awareness (Contract 7) is a SEPARATE presence layer (`pairings/{uid}` docs, nonce-scoped, 20-min expiry) precisely because conflating "I stored you" with "you stored me" caused the false-paired confusion in device testing.
4. **CameraX 1.4.2 uniform, unbundled ML Kit 18.3.1.** 1.6.1 demands AGP 8.9.1+/SDK36 (frozen: 8.7.2/35); mlkit-vision never shipped 1.3.x stable (Google Maven index). Unbundled pivot is for APK size, not 16KB evasion (resolved in 18.3.1).
5. **No KTX for new Firestore flows.** `observeHandshake` uses callbackFlow (Phase-2 tree law); the ktx `snapshots()` extension is banned.
6. **Rules are not filters.** Pairing reads are auth-open; expiry is checked client-side. A protected read would abort the takeover transaction instead of returning null. Session-nonce NOT required on write (heartbeat creates nonce-less presence docs) — handshake queries filter by nonce so they stay invisible.
7. **Package `com.calldad.*`** throughout (operator order).
