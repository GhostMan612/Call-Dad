# ADR-013 — Phase 11 execution deviations (executor, architect prompt as base)

- Status: DECIDED by executor within prompt scope (2026-09-20); DeepSeek/Gemini retro-review invited.

1. **REJECTED: flavor role gates (would brick the product).** Prompt gates `startCall` to parent-only and `answerCall` to child-only, claiming "the UI already prevents this". False on both counts: no flavor-differentiated UI exists anywhere in the tree, so the child build could neither ring nor answer — and the product's core promise is the DAUGHTER calling DAD (the prompt's own §G has the parent calling, backwards). Either side rings, either side answers. State this loudly: do not reintroduce without building the differentiated UI first.
2. **REJECTED: child-only topic subscription.** Same product-direction reason: Dad's phone must wake when the kid rings. BOTH flavors subscribe; over-delivery self-heals (own-SDP guard bounces the caller's own push in ~200ms). Killed-app wakeup still needs a launched-once app (FCM limitation, prompt-acknowledged).
3. **FGS foreground-first, verify-second.** Prompt reads the room before `startForeground()`; the FGS-start timeout makes network-on-that-path a crash risk. Notification posts immediately, room check follows async, stale push → `stopSelf()`. Same outcome, no timeout hazard.
4. **Callee observes call docs (prompt leaves it blind).** Without it the callee misses renegotiation offers, hangup/decline status, and the whole restart handshake the prompt demands logs for. Wired via existing observers; no new flows.
5. **Kept (not in prompt, still needed):** `observeCallDeleted` removed (nothing deletes anymore — status machine owns hangup); stale-snapshot guard restored on the Home listener via `updatedAt` (abandoned rooms must not ghost-ring); `CALLEE_UID` field/template retained but unread (superseded note); `simulateIncomingCall` kept as test seam.
6. **Package `com.calldad.*`** throughout (operator order).
