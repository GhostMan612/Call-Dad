# ARCHITECTURE.md — Call-Dad

> Structural truth. Code > prose on conflict.

## UI (pathfinder KOTLIN_PORT_SPEC §1 pattern)

Single-`Activity` + Compose Navigation (`Home / Call / Chat / Photo / Log`), Hilt DI, one `ViewModel` per screen, `StateFlow<UiState>` (single emission per mutation) + `SharedFlow<Event>` one-shots (ring, snackbar, navigate). `collectAsStateWithLifecycle()` in composables. Splash via `core-splashscreen` until `BootState.Ready`.

Screens: `HomeViewModel` (giant Call Dad + status), `CallViewModel` (Invite/ringing/in-call/hangup), `ChatViewModel` (thread + receipts + memo record/send), `PhotoViewModel` (pick/capture/send/view), `LogViewModel` (call history), `SettingsViewModel` (Dad-gated only — PIN/biometric).

## Comms (mantle donor ports — see docs/sovereign-comms-reuse-map.md)

- `CallSignalingManager` (transport-agnostic): `Invite→Accept/Decline→End`, `Direct` route only, anti-echo/replay, expiry.
- `LiveCallSession` + `AudioStreamingSession`: UDP `:8789` duplex voice sockets; sequence-tagged frames.
- `AudioFrameCipher`: AES-256-GCM `IV(12)||ciphertext+tag(16)` per-frame, per-call ECDH key, null-on-fail=drop.
- `SovereignCommsEngine`: `ChatMessage`/`VoiceMemo` ingest (idempotent) + delivery receipts.
- `RealTimeTextChannel`: `RT+ver+origin/dest+SealedPayload` typing/live-caption datagrams.
- `RealTimeTransportRouter`: link probes → ranked `P1 WIFI_DIRECT/HOTSPOT (0-cost)` → `P2 SIM_DATA`.
- `RendezvousClient` + `RendezvousRegistry` codec (`REGISTER 0x01 / LOOKUP 0x02 / CANDIDATE 0x03`) + Go relay `:8792/udp` (signaling only).
- `SovereignMeshTransport` 4096B framing discipline for local mode; `WifiDirectGroupManager` for no-internet direct.
- `SovereignAudioEngine` + `VoiceCodec`/`JitterBuffer` (Concentus-Opus 1.0.2); `SovereignImageEngine` chunked photo.
- `SovereignNodeService` foreground-service pattern for ringing/call persistence.

## Data

One `CallDadDatabase` (Room): `contacts` (allowlist: id, label=DAD, key, consent-scope, expiry), `calls` (dir, state, started/ended, route), `messages` (thread=DAD, kind=text/memo/photo, payload-ref, receipt, timestamps). `kotlinx.serialization` converters for blobs. SQLCipher-at-rest per ADR-003 (passphrase wrapped by Keystore, Room readable in background).

## Consent (mantle consent.py model)

`CONSENT_GRANTED_PAYLOAD{granter=DAD, grantee=KID, scope=[call,text,photo], purpose, expires}` signed cert; prospective-by-default, self-revocation-only; revocation = kill switch (calls/messages/photo blocked + explicit UI).
