# ADR-009 — Phase 7 execution deviations (executor, architect prompt as base)

- Status: DECIDED by executor within prompt scope (2026-09-20); DeepSeek/Gemini retro-review invited.

1. **No `override` on data-channel API.** Prompt declares `override val gameSyncMessages` / `override fun sendGameData`, but no super-interface exists — that is a compile error, not a style choice. Implemented as plain members.
2. **Shared CallViewModel accessor.** GameScreen needs the same activity-scoped instance for the bridge (a private per-screen VM would see no peer). `callViewModel()` visibility widened private → public; no duplication.
3. **No synthetic receiving pulse, no new host tests.** DataChannel/WebView need native + JS engines — untestable on host JVM. `JSONObject.quote` path is org.json (android stub). Verification is device-only per prompt §G; nothing fabricated.
4. **Package `com.calldad.*`** throughout (operator order).
5. **Imports normalized** (no fully-qualified inline names); `CallDadTheme` unused import dropped from GameScreen.
