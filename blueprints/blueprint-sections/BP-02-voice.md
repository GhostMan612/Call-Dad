# BP-02 — Voice call, LAN-first E2EE (Phase 2)

> **HISTORICAL (2026-09-24):** this describes the original LAN/UDP donor-port plan. What shipped is Firebase signaling + WebRTC + pair-scoped rooms. Current shape: `AGENTS.md` "Architecture notes", `blueprints/CURRENT_STATE.md` and `blueprints/decisions/ADR-015-pair-rooms.md`.

Goal: Kid taps giant button → Dad rings on LAN <3s → E2EE voice → one-tap hangup.

## Port (adapt from donor, author here)
1. `CallSignalingManager` (Invite→Accept/Decline→End, Direct-only, anti-echo/replay, expiry) + unit tests (state transitions, duplicate-invite idempotency, expired-invite decline).
2. `AudioFrameCipher` (AES-GCM per-frame, ECDH per-call; tamper→drop) + round-trip tests (encrypt→decrypt OK, flipped-bit→null, no key logging).
3. `SovereignAudioEngine` + Concentus-Opus encode/playback + `JitterBuffer`; `LiveCallSession` UDP `:8789` duplex + `RealTimeTransportRouter` P1 path.
4. UI: `CallViewModel` states (Idle→Inviting→Ringing→InCall→Ended), giant answer/hangup, auto-reconnect LAN.

## Gates
G2 host (tests above) → human Moto G LAN proof (G2-device): ring latency, ≥30s intelligible, hangup/redial, hotspot mode. Record evidence only from human paste.
