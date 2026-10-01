# ADR-010 — Phase 8 execution deviations (executor, architect prompt as base)

- Status: DECIDED by executor within prompt scope (2026-09-20); DeepSeek/Gemini retro-review invited.
- **AMENDED 2026-10-01** — see (7). Item 3 as written below is history, not the current shape.

1. **Renegotiation adapted to the status machine.** Prompt's restart path leans on the deleted Phase 2 API (`publishOffer` + OFFER observer). Replaced with: new `updateOffer(callId)` (offer-only update, status stays CONNECTED) + callee-side offer-change watcher in `listenCall` (loop-guarded by `appliedRemoteOffer`). No new fields beyond that guard.
2. **No `currentRole` field.** Prompt asks for one; role derives from `(state as? InCall)?.role` — one less field to clear wrong.
3. **Reconnect trigger wired (prompt demands the logs, never connects the trigger).** `LaunchedEffect(health)` fires one caller-side `restartIce` per LOST entry; callee answers via (1). Plus a small non-scary banner in InCall while LOST/RECONNECTING (DEGRADED stays silent per spec).
4. **Callee observes call docs now.** Prompt's design left the callee blind post-answer (no `listenCall` in `answerCall`) — meaning the callee would ALSO miss peer hangup/decline. Fixed by starting it there too.
5. **Package `com.calldad.*`** throughout (operator order).
6. **game.html render() simplified:** prompt's `isMyTurn`/`isCalleeTurn` pair reduces to `turn === myMark` — behaviorally identical, half theBranches. No other JS change.

## (7) Amendment: this item was true, then untrue, then true again — and the middle version shipped

Worth recording as history, because the sequence is the lesson.

- **2026-09-20:** item 3 is accurate. `restartIce` is called per LOST entry.
- **2026-09-25 (Contract 10 audit):** the whole reconnect path was **removed** and recorded in `RULES.md` §1.7a as not implemented. `ConnectionHealth.RECONNECTING` was never assigned and `restartIce` appeared nowhere. Item 3 became false.
- **2026-09-30:** ICE-restart was re-landed as a first-class feature, scoped to a recoverable network drop.
- **2026-10-01 (Contract 11):** that re-landing was found to be **published but never completed on the caller side**. `maybeApplyRenegotiation` opened `if (amCaller) return`, so the side that published the restart offer never applied the answer — and the guard that should have caught it was keyed on `seq`, which the caller had already consumed for the original answer. Reconnect could not have recovered a call, ever, while every source-level test and every doc claimed it worked.

The general point, recorded because this ADR is where the original claim was made: **an item on a checklist that says "trigger wired" is a claim about a call graph, not about a data path.** It can be true of the trigger and false of the round trip, and nothing in the source or the tests distinguishes the two. A reconnect is not "we send an offer"; a reconnect is "we send an offer AND receive an answer AND apply it", and only the last clause was missing while the first two were present and reviewed.

The shape that works is a monotonic `negotiationRound` carried through the document and enforced in the rules, with both sides guarded on it. `IceRestartTest` now pins the caller side explicitly, because that is the half that was missing.

