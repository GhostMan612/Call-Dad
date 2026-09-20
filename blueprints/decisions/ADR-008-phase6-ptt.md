# ADR-008 — Phase 6 execution deviations (executor, architect prompt as base)

- Status: DECIDED by executor within prompt scope (2026-09-19); DeepSeek/Gemini retro-review invited.

1. **Shared activity-scoped PTT VM (soundness fix).** Prompt §H claims default `viewModel()` calls in CallScreen and PttScreen share one PttViewModel. Wrong twice: the default factory cannot construct an AndroidViewModel (instant crash — same bug as Phase 3's CallViewModel), and `viewModel()` is NavBackStackEntry-scoped, so each screen would get a PRIVATE instance and the mic interlock would silently never fire. Fix: `PttViewModelFactory` + `rememberPttViewModel()` (activity-scoped, single definition in PttViewModel.kt) used by BOTH screens. The interlock now actually interlocks.
2. **No synthetic receiving pulse.** Prompt's docstring promises a 2s Receiving pulse but its code never emits one; its own handoff template confirms "emits Idle forever" as the accepted Phase 6 state. A fake "Dad is talking" firing unprompted would confuse QA and the child — deliberately NOT implemented. Removed the now-unused `delay` import.
3. **Double-assignment wart fixed** (`engine = SimulatedPttEngine().also { engine = it }` → single assignment) + inbound re-subscription on fallback swap (`watchEngine()`), so a future emitting engine works without further VM surgery.
4. **Host-test infra (donor-proven):** `testOptions.unitTests.isReturnDefaultValues = true` (android.jar stubs no-op instead of throw) + `kotlinx-coroutines-test` (runTest). Unlocks `SimulatedPttEngineTest` (4 tests). No device needed.
5. **Package `com.calldad.*` throughout** (operator order; prompt uses `com.calldad.app.*`).
