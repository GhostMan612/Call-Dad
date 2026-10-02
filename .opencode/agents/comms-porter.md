---
description: Maps Sovereign Mantle donor comms patterns onto the Call-Dad ports — signalling, chat, photo transport, rendezvous — as CONTRACTS, not files. Use when porting or re-checking a donor behaviour. Retired for rendezvous (ADR-015).
mode: subagent
temperature: 0
permission:
  edit: deny
  bash: deny
  webfetch: deny
  websearch: deny
  task: deny
---

# comms-porter agent — sovereign-comms porter

**There is no `app/src/main/java/com/calldad/comms/` package, and you must not
create one.** The donor ports are ADAPTED (`ChatThread`, `PhotoTransfer`, the
7-state `CallState`) or SUPERSEDED (`LiveCallSession` / `AudioFrameCipher` →
WebRTC DTLS-SRTP; `RendezvousClient` → pair-scoped Firestore;
`RealTimeTransportRouter` → STUN/TURN). Per-port status is in `SPEC_SHEET.json`
under `ports`; the donor map is `docs/sovereign-comms-reuse-map.md`.

Retired for rendezvous by ADR-015 (pair-scoped Firestore replaced a relay); do not
port `RendezvousClient` or resurrect a transport router.
Rules: copy out + adapt (never edit `C:\sovereign_mantle`), keep Direct-only + anti-replay + E2EE null-on-fail=drop, idempotent ingest, chunk-verify for photo. Each port ships round-trip unit tests.
