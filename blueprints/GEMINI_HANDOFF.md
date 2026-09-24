# GEMINI HANDOFF — Call-Dad R&D request (consolidated 2026-09-19, Chief Executor)

> **HISTORICAL (2026-09-24):** this describes the original LAN/UDP donor-port plan. What shipped is Firebase signaling + WebRTC + pair-scoped rooms. Current shape: `AGENTS.md` "Architecture notes", `blueprints/CURRENT_STATE.md` and `blueprints/decisions/ADR-015-pair-rooms.md`.

> Copy/paste to Gemini (Chief R&D). Operator order: recursive live-internet research over similar apps (family/kid calling, WebRTC + Firestore signaling) for bulletproof fixes to the known issues below. Full detail: SESSION_HANDOFF.md, ADRs, CURRENT_STATE.md.

## What this is

Native Kotlin Android app (`com.calldad`, launcher "Call of Daddy", minSdk 26 / compile-target 35, v0.2.0/vc3). A 6-year-old calls her dad: giant-button voice+video over WebRTC, Firestore SDP/ICE signaling through one hardcoded room (`calls/dad_channel` + `candidates` subcollection, project `calldad-508d7`). Single-Activity + Compose + ViewModel/StateFlow. No Hilt/Room yet. Toolchain: AGP 8.7.2 / Kotlin 2.0.21 / google-services 4.5.0 / Firebase BOM 34.19.0 / `io.getstream:stream-webrtc-android:1.3.10`.

## Device-proven working (BLU View 5 + Moto G 2025, same Wi-Fi)

- Caller leg: OFFER published <2s. Full call: OFFER→ANSWER→ICE CONNECTED both sides, two-way video (screenshots lane-witnessed), two-way audio (operator ears), timer, hangup-mirrors-home both sides, zero crashes.
- Fixes proven on device: double-dispose SIGSEGV, scope-cancelled teardown, grid-squeezed-to-zero layout, self-ring suppression, stale-offer immunity (60s), 15s zombie watchdog, stuck-overlay validation.
- Gates: `testDebugUnitTest` + `lintDebug` BUILD SUCCESSFUL (33 tasks, 9 host tests); `verify_project.py` PASS.

## Known issues for R&D (operator: research similar apps, bulletproof fixes)

1. **Single shared room.** Two simultaneous callers clobber each other's OFFER (last-writer-wins); one side ends up answering nothing/stale. Current mitigations: caller pre-publish wipe, callee 15s OFFER poll, 60s staleness expiry, own-offer registry, media watchdog. Real answer is per-call rooms — spec the scheme (room naming, discovery, cleanup, decline signaling so the caller stops ringing).
2. **No killed-app wakeup.** Listener lives only while Home is open. Real-phone behavior (daughter calls across town, dad's phone dead-asleep in pocket) needs FCM: message shape, who sends it (Cloud Function on room-write vs app-server), full-screen intent + ringtone/vibration path, battery/doze behavior, fallback when push is delayed. This is the Phase 5 core.
3. **Firestore rules dev-open** (unauthenticated read/write on `calls/dad_channel`). With no auth in the app, spec the lockdown path: App Check vs anonymous auth vs custom tokens, per-call unguessable room IDs, field-shape validation rules, what a minor's threat model actually requires (Play Families policy angle included).
4. **No TURN (K8).** STUN-only; symmetric-NAT (mobile data) calls will fail at ICE CHECKING. Recommend provider (Twilio/Metered/self-hosted coturn) + credential delivery that never touches the repo (local.properties → BuildConfig; sentinel pattern already in `WebRtcConfig`).
5. **Restart edge in self-recognition.** Own-offer registry is process-scoped; an app kill with a live stale room can ring once on next launch. Acceptable today; eliminate properly within whatever room scheme comes out of (1).
6. **No call history / missed-call state / decline signal to caller.** Callee decline currently just deletes the room (caller auto-homes via room-deleted observer — fine for QA, not a real UX). Spec the proper signaling states.
7. **Lock-screen behavior unpolished.** Connection persists, camera pauses on lock and resumes after unlock (accepted for now). Spec: pause UI, wake-lock policy, audio-only continuation.

## Constraints (RULES.md, non-negotiable)

Allowlist-only (Dad v0.1); Direct route only; no accounts/analytics/ads unless architect-approved; synthetic fixtures only; no secrets/credentials in repo (sentinel pattern for TURN); `WebRtcLog` guardrail — fixed strings/enum names only, never SDP/ICE/IPs/room IDs; explicit-path git; no push unless operator orders. Executor lane never runs Gradle/installs unprompted (operator is device hands; this session was an authorized exception for read-only adb + REST reads). Deliver Phase 5 instructions as copy/paste boxes for DeepSeek, same format as the Phase 3/4 prompts.

## Standing process notes for researchers

- Single-room clobbering means test choreography must stay one-caller-at-a-time until (1) lands.
- `versionCode` is the lane-checkable build fingerprint (`adb shell dumpsys package com.calldad`).
- Screenshots/fixtures with real faces stay OUT of the repo (`C:\venv-hub\call-dad\` lane only).
