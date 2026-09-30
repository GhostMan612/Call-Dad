---
name: calldad-conventions
description: Use when working in the Call-Dad repo (C:\Call-Dad) — any change to app/, functions/, firestore.rules, blueprints/, or a Call-Dad handoff. Encodes the lane split (operator builds/installs, agent edits and verifies), the flavored Gradle task names, the no-build/no-deploy rule, version fingerprinting before flashing, explicit-path git staging, Genesis-header requirement, and the log privacy guardrail.
---

# Call-Dad working conventions

## Tool use first — the shell is a last resort (RULES §1.4a, HARD RULE)

This is the biggest time sink in this repo's history. Shelling to read a file does
not look wrong, it just burns a round trip and the operator's attention.

- Read a file → `read`. Search contents → `grep`. Find by name → `glob`.
  Change a file → `edit` (exact string) or `write` (whole file).
- NEVER shell out to read, search, or modify a file: `cat`, `type`, `Get-Content`,
  `Select-String`, `findstr`, `rg`, `grep`, `Get-ChildItem`, `Test-Path`, `dir`,
  `ls`, `Set-Content`, `Add-Content`, `Out-File`. All denied in `opencode.json`.
- **Batch.** Many independent lookups = one parallel batch, not a chain of
  single-purpose commands.
- **Gate once at the END of a phase**, not after each edit. Fix everything visible
  via `grep`/`edit`, then close with one gate run. One gate per edit is exactly
  what turned a single phase into three days.
- Shell IS for: running a gate, operator-ordered `git` mutations, read-only `adb`
  evidence, and machine installs.

## Lane split (non-negotiable, from RULES.md)
- **This lane edits, verifies, and reasons.** It runs `tools/verify_project.py`, flavored
  `test*UnitTest` / `lint*Debug`, `node --test`, and the rules emulator.
- **The operator builds and installs.** Never run `assemble*`, `install*`, `connected*`, `run`,
  or `firebase deploy`. Hand back the exact command instead. `RULES.md` is canonical; a request
  to "just run the build" does not lift the rule by itself — the operator must lift it.
- Never claim device success. A green unit test is not a green build; a green build is not a
  green install; neither is a witnessed call.

## Gradle task names are flavored
`:app:testParentDebugUnitTest`, `:app:testChildDebugUnitTest`, `:app:lintParentDebug`,
`:app:lintChildDebug`. The unflavored `testDebugUnitTest` / `lintDebug` do not exist — their
task-not-found error masquerades as a broken build.

## Before any flash, resolve the fingerprint
Read `versionCode` / `versionName` from `app/build.gradle.kts` with the `read` tool, check
tree state with ONE batched `git status --porcelain`, and compare against the installed
`versionCode` on both devices (`dumpsys package` via the `device-evidence` tool). A dirty
tree or a version mismatch means the logs describe a different binary than the source — say so
before diagnosing anything. Bump `versionCode` whenever a new build may be installed, so an
install is distinguishable from the last one.

## Git
Explicit paths only. Never `git add .` or `-A`. Never commit secrets (`google-services.json`,
`local.properties`, `*.keystore`). Commit and push only when asked. Unrelated working-tree
changes (line-ending noise, `.kotlin/`) stay unstaged.

## Code conventions
- New `.kt`/`.py` files need the Genesis header; `tools/verify_project.py` enforces it.
- Toolchain truth is `gradle/libs.versions.toml` (ADR-004). AGP 8.7.2, Kotlin 2.0.21, Gradle
  8.13, compile/target 35, minSdk 26, Firebase BOM 34.19.0, stream-webrtc-android 1.3.10,
  CameraX 1.4.2, ML Kit barcode 18.3.1, ZXing 3.5.3, DataStore 1.1.1. No Hilt/Room/OkHttp/Concentus.
- No Hilt, no Room, hand-written ViewModel factories. One Activity + Compose Navigation.
- One `WebRTCClient` per call attempt; teardown order is pc → tracks → factory.
- Never touch a `MediaStreamTrack` after `dispose()` — this crashed hangup more than once.
- `WebRtcLog.transition` takes fixed state names only. Never log SDP, ICE, IPs, room ids, UIDs,
  tokens, or FCM payloads.
- Fixtures are synthetic. No real child names, photos, numbers, locations, or device serials —
  including in prose, docs, and handoffs.
- Kid-safe: one paired contact, grown-ups gate before pairing, no accounts/analytics/ads, no
  browser/store/settings escape.

## Fleet (this repo)
Auditors: `call-core-auditor`, `webrtc-media`, `firestore-rules-auditor`, `fcm-wakeup-auditor`,
`doc-drift-auditor`, `kid-ux-guardian`, `native-dev`. Execution: `gate-runner`. Records:
`handoff-writer`. Tools: `gate`, `device-evidence`, `contract-diff`.
Commands: `/verify`, `/probe`, `/smoke`, `/sweep`, `/flash`, `/evidence`.

## Evidence vocabulary for handoffs
`VERIFIED` (ran here) / `CODE-READ` (read source) / `CLAIMED` (someone else said so) /
`UNVERIFIED` (no evidence) / `STALE` (a pull or reset invalidated it). Never launder another
lane's claim as your own result.
