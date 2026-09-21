# ADR-012 — Phase 10 execution notes (executor, architect prompt as base)

- Status: DECIDED by executor within prompt scope (2026-09-20); DeepSeek/Gemini retro-review invited.

1. **Launcher labels preserve the operator order.** Prompt says child "Call Dad"; operator renamed to "Call of Daddy" (committed). Child flavor = "Call of Daddy", parent = "Call of Daddy (Parent)". Manifest label is now `@string/app_name` (flavor-driven).
2. **BLOCKER — Firebase console needs two Android apps.** Suffixed IDs (`com.calldad.parent`, `com.calldad.child`) match NO client in the current `google-services.json` → both flavor builds FAIL until the operator registers both apps in console and replaces the (gitignored) json with the merged download. No code can work around this; flagged before any other Phase 10 verification.
3. **Phase 9 game hub never landed (executor miss).** Phase 9's §H full-file replacement was skipped; disk still held the Phase 8 TTT-only shell (proven via git log + line count, not assumed). Phase 10's file supersedes both — no separate recovery commit needed, recorded here instead.
4. **Camera toggle verified present, no change:** CallScreen 140dp button → `onToggleCamera()` → `webrtc.toggleCamera()` (`videoTrack.setEnabled`), chain grep-confirmed.
5. **Package `com.calldad.*`** throughout (operator order).
