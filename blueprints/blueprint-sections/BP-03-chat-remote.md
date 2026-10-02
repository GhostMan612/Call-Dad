# BP-03 — Chat + voice memo + remote rendezvous (Phase 3)

> **HISTORICAL (2026-09-24):** the rendezvous/relay plan below was retired by
> `blueprints/decisions/ADR-015-pair-rooms.md` — there is no relay, no `main.go`, no
> reflexive-addr path, and no hole-punch. Signaling is pair-scoped Firestore and voice memos are
> AAC clips in the pair room (ADR-016). Current shape: `AGENTS.md` "Architecture notes",
> `blueprints/CURRENT_STATE.md`, ADR-015 and ADR-016.

Goal: 1:1 Dad thread with receipts + Opus memos; remote reachability via rendezvous.

## Port
1. `SovereignCommsEngine` (ChatMessage/VoiceMemo, idempotent ingest, sent→delivered→read receipts) + `MemoReplayGuard` + `RealTimeTextChannel`.
2. `RendezvousClient` + `RendezvousRegistry` codec + relay `main.go` deploy (1vCPU VPS or home relay; reflexive-addr authoritative; TTL 45s). Relay carries signaling only.
3. `ChatViewModel` (thread, send text/memo, receipts UI — giant, read-aloud optional later).

## Gates
G3: receipt-transition tests, duplicate-ingest single-row, memo ≤2min encode/decode; human remote proof (different networks, hole-punch success + voice + chat receipt).
