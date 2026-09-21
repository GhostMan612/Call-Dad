# ADR-010 — Phase 8 execution deviations (executor, architect prompt as base)

- Status: DECIDED by executor within prompt scope (2026-09-20); DeepSeek/Gemini retro-review invited.

1. **Renegotiation adapted to the status machine.** Prompt's restart path leans on the deleted Phase 2 API (`publishOffer` + OFFER observer). Replaced with: new `updateOffer(callId)` (offer-only update, status stays CONNECTED) + callee-side offer-change watcher in `listenCall` (loop-guarded by `appliedRemoteOffer`). No new fields beyond that guard.
2. **No `currentRole` field.** Prompt asks for one; role derives from `(state as? InCall)?.role` — one less field to clear wrong.
3. **Reconnect trigger wired (prompt demands the logs, never connects the trigger).** `LaunchedEffect(health)` fires one caller-side `restartIce` per LOST entry; callee answers via (1). Plus a small non-scary banner in InCall while LOST/RECONNECTING (DEGRADED stays silent per spec).
4. **Callee observes call docs now.** Prompt's design left the callee blind post-answer (no `listenCall` in `answerCall`) — meaning the callee would ALSO miss peer hangup/decline. Fixed by starting it there too.
5. **Package `com.calldad.*`** throughout (operator order).
6. **game.html render() simplified:** prompt's `isMyTurn`/`isCalleeTurn` pair reduces to `turn === myMark` — behaviorally identical, half theBranches. No other JS change.
