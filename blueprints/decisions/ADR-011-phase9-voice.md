# ADR-011 — Phase 9 execution deviations (executor, architect prompt as base)

- Status: DECIDED by executor within prompt scope (2026-09-20); DeepSeek/Gemini retro-review invited.

1. **Single ringer.** Prompt adds `CallAudioManager` but leaves the Phase 4 overlay-local ringtone in place — two ringers, double sound, and the old one ignores audio-mode state. Removed the overlay player; `CallAudioManager` (VM-driven: start on Incoming, stop on every exit) is the sole owner.
2. **Answer stops the ring.** Prompt wires stop() into start/end/clear but not the Incoming→InCall transition — the ringtone would loop under the live call. Added stop() to `answerCall` entry (plus startCall/decline/end/clear/onCleared for full coverage).
3. **Shared Helper VM (same bug class as Phase 3/6).** Prompt's HelperScreen uses default `viewModel()` on an AndroidViewModel (instant crash) — fixed with `HelperViewModelFactory` + `rememberHelperViewModel()` (activity-scoped, `LocalContext` cast — `LocalActivity` is still unresolved in activity-compose 1.9.3, device-proven Phase 6).
4. **Keyword order fix.** Prompt's list puts "another joke" after "joke", making the second joke unreachable despite its own specific-first contract. Reordered; responses untouched.
5. **No fake receiving pulse** (same doctrine as Phase 6): template confirms Idle-forever accepted.
6. **Package `com.calldad.*`** throughout (operator order).
