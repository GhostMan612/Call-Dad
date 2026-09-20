# GEMINI HANDOFF — Call-Dad Phase 4 request (consolidated 2026-09-19, Chief Executor)

> Copy/paste to Gemini (Chief R&D). Source: repo `main` + SESSION_HANDOFF.md (full detail there).

## Repo state

- Remote: `https://github.com/GhostMan612/Call-Dad` (local `main` UNPUSHED — operator pushes).
- Package `com.calldad`, launcher label **"Call of Daddy"**, minSdk 26 / compile-target 35, v0.2.0/vc2.
- Commits: d6399d9 (Phase 1 UI) → 50d9626 (package) → 3c5a236 (Phase 2 signaling) → c40959c (toolchain box) → afaab75 (Phase 3 WebRTC) → f03fe3d (coords fix) → 86f6a81 (label + wrapper) → 7ee6351 (operator) → 6f49cfd (crash fix) → 4a33e41 (caller green) → c6546e1 (two-device run).
- Toolchain (ADR-004, catalog is source of truth): AGP 8.7.2 / Kotlin 2.0.21 / google-services 4.5.0 / Firebase BOM 34.19.0 / `io.getstream:stream-webrtc-android:1.3.10` (corrected — `webrtc-android:1.1.0` never existed) / coroutines-play-services 1.9.0.
- Decisions: ADR-001 minSdk 26; ADR-002 Firebase-for-signaling; ADR-003 SQLCipher pending; ADR-004 toolchain; ADR-005 WebRTC (Stream fork, trickle ICE, STUN-only, callee fix, WebRtcLog guardrail).

## Device-proven evidence (BLU View 5 B160V/sdk34 + Moto G)

- Host: `testDebugUnitTest` + `lintDebug` BUILD SUCCESSFUL (33 tasks; 8/8 tests; lint clean).
- Caller leg GREEN both phones: `OFFER published` <2s, clean teardown, zero crashes (double-dispose + cancel-rethrow fixes proven).
- Firestore project `calldad-508d7`: database created, dev rules on `calls/dad_channel` + `candidates` (unauthenticated — Phase 5 must harden).
- Two-device run: BOTH sides caller-only (no incoming-call UI exists) — nothing failed; callee path unreachable from UI.

## Gaps for Phase 4 (your spec, please)

1. **Incoming-call overlay** (operator's likely immediate pick): OFFER listener → giant Answer/Decline → `answerCall()`. Note single-room overwrite issue (`dad_channel` clobbering) — per-call rooms if you spec them.
2. **Renderer wiring**: `SurfaceViewRenderer` attach on CallScreen (hooks exist in WebRTCClient).
3. **TURN provider decision** (K8 blocker before mobile-data field testing): Twilio / Metered / self-hosted coturn.
4. **Privacy review**: unauthenticated Firestore signaling for a minor's 1:1 calls; Play Families implications; Phase 5 lockdown path.

## Constraints (RULES.md, non-negotiable)

Allowlist-only (Dad v0.1); Direct route only; no accounts/analytics/ads; synthetic fixtures only; no secrets in repo; `WebRtcLog` guardrail (fixed strings/enum names only — never SDP/ICE/IPs/room IDs); explicit-path git; no push unless operator orders. Executor lane never runs Gradle/installs unprompted; operator is the device hands (BLU = test phone, Moto G = truth device).
