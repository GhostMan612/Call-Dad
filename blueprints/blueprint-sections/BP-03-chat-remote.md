# BP-03 — Chat + voice memo + remote rendezvous (Phase 3)

Goal: 1:1 Dad thread with receipts + Opus memos; remote reachability via rendezvous.

## Port
1. `SovereignCommsEngine` (ChatMessage/VoiceMemo, idempotent ingest, sent→delivered→read receipts) + `MemoReplayGuard` + `RealTimeTextChannel`.
2. `RendezvousClient` + `RendezvousRegistry` codec + relay `main.go` deploy (1vCPU VPS or home relay; reflexive-addr authoritative; TTL 45s). Relay carries signaling only.
3. `ChatViewModel` (thread, send text/memo, receipts UI — giant, read-aloud optional later).

## Gates
G3: receipt-transition tests, duplicate-ingest single-row, memo ≤2min encode/decode; human remote proof (different networks, hole-punch success + voice + chat receipt).
