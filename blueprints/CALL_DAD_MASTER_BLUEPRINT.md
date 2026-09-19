# CALL_DAD_MASTER_BLUEPRINT.md — frozen v0.1 product spec

> Frozen v0.1 target (2026-09-19 scaffold). Changes require ADR + operator sign-off.
> Pattern source: Vision Engine MASTER (frozen) + pathfinder KOTLIN_PORT_SPEC + mantle comms donor.

## 1. Product definition

Kid-safe native Android app. Kid device: one giant **Call Dad** button + photo button + voice-memo button + text thread (big type, no keyboards traps). Dad device: full thread + answer/decline + parent gate. v0.1 allowlist = Dad only.

## 2. User journeys

- J1 Kid→Dad voice (LAN): tap giant button → Dad rings <3s → talk → one-tap hangup.
- J2 Kid→Dad voice (remote): same UX via rendezvous hole-punch; relay carries signaling only.
- J3 Video (LAN v0.1): same signaling + CameraX previews; mute-video → voice continues.
- J4 Text/voice-memo: big send, delivery receipt (sent→delivered→read), Opus voice memo ≤2min.
- J5 Photo: pick/take → downscale+WEBP cap → chunked send → verified reassemble → receipt.
- J6 Parent gate: kid cannot add contacts; Dad approves (biometric/PIN) on Dad device or setup.

## 3. Architecture (see ARCHITECTURE.md)

- `app/` native Kotlin: `com.calldad`, single-Activity + Compose Navigation + Hilt, screens: Home (giant button), Call (in-call), Chat (text+memo), Photo (share/view), Log (call history). One ViewModel/screen, StateFlow+SharedFlow.
- `core/` domain (pure Kotlin where possible): call-state machine, message models, photo-chunk codec, consent-scope check.
- `comms/` Android adapters (ported donor): signaling, UDP voice session, frame cipher, image engine, rendezvous client, transport router, audio engine (Opus), Wi-Fi-Direct manager.
- `data/` Room: `contacts` (allowlist), `calls` (log), `messages` (thread + receipts + photo refs), SQLCipher per ADR-003.
- Relay (ops, not app): Go `main.go` :8792/udp (stdlib-only), TTL 45s/sweep 30s; signaling only, never media/keys.

## 4. Protocol (donor-faithful)

`P1 Wi-Fi-Direct/Aware/Hotspot UDP 4096B frames (E2EE per-peer ECDH + AES-GCM voice, SealedPayload text) → P2 SIM-data via rendezvous REGISTER/LOOKUP/CANDIDATE + UDP hole-punch :8791/8792 → DTN store-carry-forward fallback (Phase 3+)`. Live voice `LiveCallSession:8789` bypasses ledger; chat/PTT/photo ride chunked events. Relay reflexive-addr authoritative.

## 5. Security / consent

- Allowlist-only, Direct route only. Consent cert Dad-grants-Kid `[call,text,photo]` + expiry; revocation = kill switch (modeled on mantle `consent.py`).
- Keys: Keystore non-exportable; SQLCipher passphrase wrapped; per-call ECDH ephemeral; `DualKeyGate`-style parent approval for contact-add.
- Redaction: chat/PTT/photo/telemetry never leave device to any hub/cloud without explicit parent opt-in.

## 6. UX law (see docs/kid-safe-ux.md)

Big touch targets (≥96dp primary), high contrast, one action per screen, no text-entry traps (voice-memo first), no escape to browser/store/settings, auto-reconnect + loud ring, missed-call = giant "Call back" card.

## 7. Gates (see CHECKPOINTS.md)

BP-01 skeleton → BP-02 voice → BP-03 chat+memo → BP-04 photo+video-spike → BP-05 hardening (parent gate, offline matrix, kid-UX audit). Firebase = Phase 4 fallback only.
