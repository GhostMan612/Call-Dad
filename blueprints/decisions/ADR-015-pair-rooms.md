# ADR-015 — Pair-scoped rooms, targeted push, one call session (full-repo fix, 2026-09-24)

- Status: DECIDED by executor under operator order "fix everything so the app works" (2026-09-24). Supersedes ADR-013 §static-room/topic and ADR-014 §3/§6. DeepSeek/Gemini retro-review invited.
- Evidence: 4-lane repo sweep (call core, security, tests/features, docs drift), each top finding re-verified against source by the executor.

## Decisions

1. **Room per pair, id = the two UIDs.** `calls/{min(uidA,uidB)}_{max(uidA,uidB)}` (`CallRoom.idFor`). Replaces the fixed `calls/family_channel`.
   - Why: the fixed id was world-readable (SDP/ICE incl. home IPs to any anonymous user), squattable (first creator owned it forever, nobody could delete), and bricked calling after any reinstall (new anon UID ≠ recorded parties → PERMISSION_DENIED shown as "line busy").
   - Rules authorize from `callId.split('_')` alone, so the publish transaction's pre-read of a missing doc still passes (the reason ADR-013 had opened `get` to everyone).
   - Rules also freeze parties within a generation, forbid seq going backwards, and require a new generation to be the writer's own RINGING offer. 15 emulator tests: `tools/rules-test/`.
2. **Push goes to the callee's own device token**, not the `incoming_calls` topic. Token lives in owner-only `users/{uid}.fcmToken` (`PushTokenRegistrar`). Function `onCallRoomWritten` (all writes, so the room's FIRST call now pushes too; the old `onDocumentUpdated` never fired on create) validates both parties against the room id. Pure decision in `functions/ring.js`, 6 node tests. Node 22 (20 is being retired).
3. **One call session per activity.** `callViewModel()` is activity-scoped (it was silently back-stack-entry-scoped despite its comment). The session observes the paired room from every screen, so rings are never missed off Home; the game screen shares the live call's data channel. `HomeViewModel`'s own listener is gone.
4. **WebRTCClient is single-use per attempt.** New instance per call/answer, one `teardownMedia()` exit path. Dispose order: `pc.dispose()` → local tracks/sources/capturer → factory. The shared `EglBase` lives for the ViewModel's lifetime.
   - This is the root cause of the recurring hangup SIGSEGV: `endCall()` freed the factory while the remote track's Java wrapper was still sink-attached.
   - It also fixes Try Again, which re-used a disposed client, and re-ring, which leaked the camera.
5. **Candidates are ordered.** Local candidates are queued until our own SDP is published. Remote candidates are buffered in the client until the remote description is set. Before this, candidates were lost on both sides, giving black video.
6. **Every exit writes the room, checked by generation.** `finishCall(seq)` covers hang up, decline, no-answer (45s) and connection lost (20s grace). A stale teardown can never kill a newer call. There is no busy/takeover logic any more: a two-person room has no third party to be busy with. When both phones call at once, the side with the lower seq auto-answers.
7. **Pairing is mutual and parent-gated.**
   - A grown-ups gate (a two-digit multiplication) sits in front of `Routes.PAIRING`.
   - The peer is stored ONLY after `pairings/{peerUid}` names us with the same session nonce. The watch is a single-document listener: `list` is denied by rules.
   - A phone's own code is rejected.
   - The QR is v2 `{v, uid, nonce}` (no FCM token). v1 codes are still accepted.
8. **Killed-app service is foreground-first.** `startForeground()` runs synchronously, before any I/O. The ring is then validated against the paired room, rings through the single `CallAudioManager` (now a process singleton, also used in-app, plus caller ringback), and the service removes itself when the ring ends or after 60s. The notification channel is silent (`incoming_call_v2`), so there is no double sound.

## Deliberately not changed

- **ML Kit stays:** RULES §1.7 phone-home review is still open for the operator (ZXing-only decode is the alternative).
- **TURN credentials are still BuildConfig:** they can be extracted from the APK. Short-lived credentials need a server-side issuer: operator decision + provider (K8).
- **No new runtime dependencies.** Test-only: `org.json` (JVM tests only; the android.jar stub returns defaults).
