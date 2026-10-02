# CALL_DAD_MASTER_BLUEPRINT.md — frozen v0.1 product spec

> **HISTORICAL (2026-09-24):** this describes the original LAN/UDP donor-port plan. What shipped is Firebase signaling + WebRTC + pair-scoped rooms. Current shape: `AGENTS.md` "Architecture notes", `blueprints/CURRENT_STATE.md` and `blueprints/decisions/ADR-015-pair-rooms.md`.
>
> The banner covers a plan that was **superseded**. It does not cover a plan that was
> **rejected**: Hilt, Room, SQLCipher, OkHttp, Concentus and KSP are not in
> `gradle/libs.versions.toml` and were never adopted. Sections 3 and 5 named several of
> them as if they shipped, so each such line is now negated inline.

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

- `app/` native Kotlin: `com.calldad`, single-Activity + Compose Navigation + **hand-written ViewModel factories (no Hilt)**, screens: Home (giant button), Call (in-call), Chat (text), Photo (send/view), Ptt (walkie talkie), Game, Helper, Consent, Pairing. One ViewModel/screen, StateFlow+SharedFlow.
- `core/` domain (pure Kotlin where possible): call-state machine, message models, photo-chunk codec, consent-scope check.
- `comms/` Android adapters (ported donor): signaling, UDP voice session, frame cipher, image engine, rendezvous client, transport router, audio engine (Opus), Wi-Fi-Direct manager. **NOT BUILT** — what shipped is pair-scoped Firestore signaling (ADR-015) and WebRTC media with DTLS-SRTP + Opus internally (ADR-005). Only the mantle *idiom* was ported, not these transports.
- **No `data/` Room and no SQLCipher.** The call log is DataStore preferences (ADR-018) and the thread is pair-scoped Firestore `calls/{room}/chat/`. There is no `contacts` table — v0.1 is one paired peer UID in an in-memory store, backed by a mutual `pairings/` handshake.
- Relay (ops, not app): **SUPERSEDED.** There is no Go `main.go` rendezvous relay. Media NAT traversal is a TURN relay configured in `WebRtcConfig`; signaling needs none.

## 4. Protocol (donor-faithful)

`P1 Wi-Fi-Direct/Aware/Hotspot UDP 4096B frames (E2EE per-peer ECDH + AES-GCM voice, SealedPayload text) → P2 SIM-data via rendezvous REGISTER/LOOKUP/CANDIDATE + UDP hole-punch :8791/8792 → DTN store-carry-forward fallback (Phase 3+)`. Live voice `LiveCallSession:8789` bypasses ledger; chat/PTT/photo ride chunked events. Relay reflexive-addr authoritative.

## 5. Security / consent

- Allowlist-only, pair-scoped rooms only. **SUPERSEDED: the signed consent cert with an `expires` field is not what shipped** — ADR-017 replaced it with fail-closed per-scope grants (`CALL`/`PTT`/`TEXT`/`PHOTO`) in the pair room, where absence DENIES and revocation is an append-only seq range.
- Keys: **no SQLCipher passphrase, no `MemoryScrubber` — neither is in the build.** WebRTC's DTLS-SRTP covers the media path (ADR-005); Keystore non-exportable keys are used for the signing key. Parent approval for contact-add is the `ParentGate` grown-ups gate, not a `DualKeyGate`.
- Redaction: chat/PTT/photo/telemetry never leave device to any hub/cloud without explicit parent opt-in.

## 6. UX law (see docs/kid-safe-ux.md)

Big touch targets (≥96dp primary), high contrast, one action per screen, no text-entry traps (voice-memo first), no escape to browser/store/settings, auto-reconnect + loud ring, missed-call = giant "Call back" card.

## 7. Gates (see CHECKPOINTS.md)

BP-01 skeleton → BP-02 voice → BP-03 chat+memo → BP-04 photo+video-spike → BP-05 hardening (parent gate, offline matrix, kid-UX audit). **Firebase is the shipped signaling + wakeup path (ADR-002 DECIDED, ADR-015), not a Phase 4 fallback** — `firestore.rules` is deployed and both Cloud Functions are live.
