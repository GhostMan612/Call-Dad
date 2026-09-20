# ADR-007 — Phase 5 execution deviations (executor, architect prompt as base)

- Status: DECIDED by executor within prompt scope (2026-09-19); DeepSeek/Gemini retro-review invited.
- Context: prompt assumes FCM token plumbing (deferred to Phase 6 by its own gaps) and KTX artifacts (removed tree-wide in Phase 2).

1. **No KTX, ever.** `firebase-auth`/`firebase-messaging` are BOM-managed non-KTX (`FirebaseAuth.getInstance()`); prompt's `ktx.auth`/`ktx.Firebase` imports rewritten. Same doctrine as the Phase 2 `getInstance()` fix.
2. **BOM-managed versions.** `firebase-auth`/`firebase-messaging` declared WITHOUT pins (BOM 34.19.0 decides — forced pins risk untested combos). `play-services-auth:21.3.0` pinned explicitly (not BOM-managed, per prompt). Informational `firebaseFunctions` pin skipped (dead catalog entry).
3. **Ring-pointer bridge (functional deviation).** Prompt's design is untestable app-to-app until Phase 6 (no way to address the callee). Kept `ring/dad`: presence-only `{callId, callerUid, createdAt}`, strict-ish rules (auth-read, caller-write, any-auth-delete), Home listener + entry validation + staleness carried over. No SDP ever in ring docs. Removed/superseded when FCM targeting lands.
4. **CALLEE_UID provisions locally** (`local.properties` → BuildConfig, empty default = kid-safe "ask a parent" error card). Each phone needs the other's anonymous uid (console Auth tab); build/install per phone with swapped values. Documented in `local.properties.template`.
5. **POST_NOTIFICATIONS runtime request added** (not in prompt, required by it): killed-app path posts on API 33+; without the grant the FSI chain is dead on both our devices (SDK 34/36). One-shot, non-blocking.
6. **Stable-previous behavior preserved:** 15s media watchdog, 45s ring timeout (new — FCM wakeup needs room on dozing phones), cancel-safe catches, idempotent dispose, `observeCallDeleted` backup, `OwnCallRegistry` (renamed from offers to callIds; file replaced).
7. **`simulateIncomingCall` kept @VisibleForTesting** per prompt §I (test seam; production now uses callId routing).
