# RULES.md — Call-Dad Operating Law
> **CANONICAL RULESET.** Every session MUST read this file before any work.
> If any other document contradicts this file, THIS FILE WINS.
> Workflow distilled read-only from: `C:\pathfinder_god`, `C:\Recovery for All`, `C:\sovereign_mantle`, `C:\sovereign_tagger`, `C:\Sovereign-Atlas-Engine`, `C:\vision engine` (primarily Vision Engine pattern + pathfinder Kotlin port + mantle comms donor). Nothing in those trees was modified.

---

## 1. ABSOLUTE RULES (never violated, no exceptions)

### 1.1 External directories are READ-ONLY
Never create, modify, move, or delete ANYTHING under:
```
C:\pathfinder_god
C:\Recovery for All
C:\sovereign_mantle
C:\sovereign_tagger
C:\Sovereign-Atlas-Engine
C:\vision engine
C:\Godot
C:\Program Files\Unity Hub
C:\Program Files\Microsoft Visual Studio
```
- Reading and copying FROM the six Sovereign-family trees into `C:\Call-Dad` is allowed. When in doubt: copy out, edit inside.
- `C:\Godot`, Unity Hub, Visual Studio installs are read-only tool homes — ask operator for override with written reason before creating anything there.
- The ONLY writable work areas: `C:\Call-Dad`, isolated lane `C:\venv-hub\call-dad\`, approved tool homes (`%USERPROFILE%\.gradle`, pub caches, `C:\android` SDK via sanctioned tools only).
- Source: Vision `RULES.md §1.1`, pathfinder `RULES.md §1.1`, Atlas `§1.2`.

### 1.2 Central venv hub — use AS-IS, settings immutable
- Approved Python: `C:\venv-hub\venv\Scripts\python.exe` (3.14.6). Use as-is.
- NEVER modify `C:\venv-hub\server.py`, `WakeHub.bat`, `venv\pyvenv.cfg`, or shared venv packages.
- You MAY create venvs/install packages inside `C:\venv-hub\call-dad\`. You MAY NOT `pip install` into shared `C:\venv-hub\venv` without explicit operator approval.
- Source: pathfinder `§1.1`, Vision `§1.2`.

### 1.3 Secrets, keys, and real child data never enter the repo
- Never commit: `.env`, API keys, keystores (`*.keystore`, `*.jks`), `local.properties`, `google-services.json`, signing passwords, Firebase service-account files.
- Never commit real child/family names, phone numbers, photos/videos, EXIF GPS, home addresses, school info. Fixtures use synthetic identities only (`DAD_TEST`, `KID_TEST`, fake numbers `+1-555-0100`, fixed fake lat/lng `44.9778,-93.2650` only as obviously-fake constant, stripped where possible).
- Kid photos shared during device testing stay on-device; never copied into repo/fixtures.
- Source: all six reference `RULES.md` secrecy laws + mantle consent/redaction posture.

### 1.4 Git discipline
- Stage by explicit path only. `git add -A` / `git add .` are FORBIDDEN.
- Never force-push, rebase public history, or delete branches unless explicitly asked.
- `blueprints\` IS committed for this project (working docs). `fixtures\` synthetic samples may commit; `*.csv` manifests, local `local.properties`, keystores, `google-services.json` are gitignored — do not "fix" this.
- Commit messages report gates status (unit test / lint / verify) only. NEVER claim build/install/device success — human builds in Android Studio.
- No push unless operator explicitly says so.
- Source: pathfinder `§1.3`, Atlas `§1.4`, Vision `§1.4`.

### 1.5 BUILD BOUNDARY — HARD RULE
- NEVER run: `assemble*`, `install*`, `connected*`, `flutter build/run`, emulator installs. The human builds + installs in Android Studio on Moto G 2025 / BLU View 5.
- Your lane ends at source correctness: `./gradlew :app:testParentDebugUnitTest :app:testChildDebugUnitTest :app:lintParentDebug :app:lintChildDebug` (run from the repo root; the parent/child flavors mean the unflavored `testDebugUnitTest`/`lintDebug` tasks do not exist), `tools/verify_project.py`, `node --test functions/ring.test.js`, the Firestore rules tests in `tools/rules-test/` (local emulator), `adb devices` / read-only `adb shell getprop`. Compiling via the unit-test tasks is source verification, not a build claim.
- Device/instrumented suites under `app/src/androidTest/` exist for the human's manual runs — do NOT execute them here unless explicitly asked.
- Debug device failures from the human's pasted output — never by rebuilding locally.
- Source: Vision `§1.5`, Atlas `§1.6`, Recovery `§1.5`.

### 1.6 Nothing outside the project without approval
Do not install software, modify system settings, or write outside `C:\Call-Dad` / `C:\venv-hub\call-dad\` / approved tool homes without asking first.

### 1.7 Kid-safe law (Call-Dad specific, non-negotiable)
- Allowlist-only contacts. v0.1 allowlist = Dad. No dial-pad to arbitrary numbers, no open discovery, no `CallRoute` broadcast — `Direct` only.
- Adding a contact requires parent approval (adapted mantle `DualKeyGate` pattern: biometric/parent-gate, never kid-self-service).
- No accounts, no analytics, no ads, no third-party SDKs phoning home in v0.1 (each new dep needs ADR justification).
- Big-button UX: one giant Call Dad, one-tap hangup, auto-reconnect on LAN. Kid can never get stuck or lost.
- Chat/PTT/photo events that would sync to any hub are redacted by default (mantle `hub_sync_bridge` posture: chat/PTT/telemetry/biometric never leave device without explicit parent opt-in).

### 1.7a Recorded architecture deviations (operator to ratify)
§1.7 and §2 were written for the original LAN/UDP donor-port plan. The shipped app took these routes by ADR; this block records them so the law matches the code until the operator amends §1.7/§2 directly:
- **Accounts / network services:** Firebase anonymous Auth + Firestore signaling + FCM wakeup + Cloud Function (ADR-002, ADR-007, ADR-013, ADR-015). No analytics, no ads, no user-visible accounts. Calls need internet for signaling; LAN-only calling (§2.3) is not implemented.
- **Media + crypto:** WebRTC (Stream fork) with DTLS-SRTP end-to-end media encryption (ADR-005) replaces the custom ECDH + AES-GCM frame cipher (§2.5).
- **Call flow:** the 7-state machine in `CallState.kt` (§2.6's Invite→Accept/Decline→End, extended with NoAnswer/Error).
- **Allowlist:** exactly one paired contact per phone, via a pair-scoped Firestore room that only those two UIDs can touch; pairing is parent-gated and mutual (ADR-015).
- **Open:** ML Kit (Play Services barcode) sends usage metrics to Google — keep, or decode with ZXing only (operator).

---

## 1A. CONTEXT & OUTPUT DISCIPLINE

- Filter terminal output; pipe for failures only. Never ingest passing noise.
- No massive file reads — probe large files/logs with short Python scripts via hub python, not full reads.
- Targeted verification during development; full suites reserved for staged-commit verification.
- Spawn subagents for deep exploration when available; return summaries, not raw dumps.
- Proactively compact context after each verified phase.
- Keep Gemini/DeepSeek handoffs short: deltas + next actions + open decisions.
- Source: mantle `AGENTS.md`, Atlas `§1A`, pathfinder `§1A`, Vision `§1A`.

---

## 2. PROJECT CONVENTIONS (Sovereign Directives, adapted)

1. **Full code only** — no partial snippets, no TODO stubs, no "insert here".
2. **No comments in code** — except the Genesis header every new `.kt`/`.py` file must carry:
   ```
   // ============================================================
   // As Above, So Below. As Within, So Without.
   // The Future Dictates the Past and the Past is Always Present.
   // ============================================================
   ```
   (Python uses `#` equivalents.)
3. **Offline-first:** call/text/photo on LAN work with zero network. Failure modes designed before any Firebase/network feature is called complete.
4. **Provenance travels:** sender, timestamp source, and consent scope travel with every message/photo record. Unknown > invented. Never fabricate timestamps/locations.
5. **E2EE by pattern:** per-call ECDH + AES-256-GCM per-frame (mantle `AudioFrameCipher` idiom); null-on-fail = drop frame, never log key material.
6. **Explicit ops:** call setup is `Invite→Accept/Decline→End` state machine (mantle `CallSignalingManager`); photo send is chunked + reassembled + verified (mantle `SovereignImageEngine`); manifests + undo where destructive.

---

## 3. TECHNICAL LAWS

| Law | Rule | Source |
|-----|------|--------|
| PowerShell mojibake BAN | NEVER modify source via `Get-Content/-replace/Set-Content`, `Out-File`, `Add-Content`. PS 5.1 mis-reads UTF-8 → mojibake. Editor tools only; Python with `encoding='utf-8'` if scripted; then analyze/lint + signature grep. | tagger `§3.1`, Vision `§3` |
| PS binary pulls | NEVER pipe `adb pull` / binary output through PS pipes — write straight to file. | Atlas `§3` |
| UI thread | Audio/call work off Main; Compose collects `StateFlow` with lifecycle; every `collect` after navigation needs lifecycle guard. No blocking calls in composables. | pathfinder port + tagger async law |
| Storage | MediaStore / SAF + app-private files only. Temp under cache; clean up on failure too. Runtime media/camera/mic permissions via Accompanist/permissions; `createWriteRequest` on Android 11+ where needed. | tagger Storage law, Vision `§3` |
| Audio | `AudioStreamingSession`/`LiveCallSession` socket template; Opus via Concentus; serialized executor for encode; jitter buffer on receive. Never log raw audio. | mantle comms |
| Crypto | Non-exportable Keystore keys; SQLCipher passphrase wrapped by Keystore; per-call ECDH ephemeral; `MemoryScrubber` idiom for key bytes. | mantle security |
| Dep ceiling | New deps need ADR justification. No major upgrades without dedicated session. Pins in `gradle/libs.versions.toml` are load-bearing until ADR (ADR-004); every dependency goes through a catalog alias. | tagger/Atlas/Vision ceiling |
| Analyzer scope | `lintParentDebug`/`lintChildDebug` errors + Kotlinc warnings stay at zero for touched modules. Don't loosen lint baselines to pass. | Recovery/Vision `§3` |
| Core/adapters boundary | Pure call/chat/photo domain logic dependency-free where possible; Android/MediaStore/Crypto/Net types stay in adapters. New modules need ADR. | Atlas `§2`, Vision arch |
| Reference law | Sovereign-family trees are REFERENCE ONLY. Every line for Call-Dad is authored here. Credit donor pattern in docs where directly ported. | Vision `§4.5` |

---

## 4. WORKFLOW LAW

### 4.1 Cold start (every session, in order)
1. Read `SESSION_HANDOFF.md`
2. Read THIS file (`RULES.md`)
3. Read `blueprints/CURRENT_STATE.md` → `blueprints/CHECKLIST.md`
4. Work from the relevant `blueprints/blueprint-sections/BP-*.md`

### 4.2 Session end (every session)
1. Update `SESSION_HANDOFF.md` ("Where we are" + "Next actions")
2. Tick `blueprints/CHECKLIST.md`; flip gates in `blueprints/CHECKPOINTS.md`
3. Refresh `blueprints/CURRENT_STATE.md`
4. Commit code by explicit path with descriptive message (never commit secrets, manifests, or real child data)

### 4.3 Verification law
No checklist item is done until its checkpoint gate passes (see `blueprints/CHECKPOINTS.md`). Evidence before status flips. New work → define its gate first. Host gates: unit test + lint + verify script. Device claims ONLY from device runs (human's pasted evidence).

### 4.4 Scope law
Big dreams (group calls, multi-contact, cloud backup, AI) live in blueprint phases first. Ship vertical slices; never let polish precede a passing LAN-call safety gate. No core rewrites to serve app convenience.

### DOCUMENT MAP
| # | File | Holds | When |
|---|------|-------|------|
| 0 | `AGENTS.md` | Compact ramp | automatic |
| 1 | `RULES.md` | CANONICAL law | EVERY session |
| 2 | `SESSION_HANDOFF.md` | Live deltas + next | EVERY session |
| 3 | `blueprints/CURRENT_STATE.md` | Verified file map + issues | before code |
| 4 | `blueprints/ROADMAP.md` | Phases 0–5 + gates | planning |
| 5 | `blueprints/CALL_DAD_MASTER_BLUEPRINT.md` | Frozen v0.1 spec | novel features |
| 6 | `blueprints/ARCHITECTURE.md` | UI/comms/data split | structural |
| 7 | `blueprints/blueprint-sections/BP-*.md` | Executable slices | task work |
| — | `SPEC_SHEET.md/.json` | v0.1 scope contract | scope questions |
| — | `*.gradle.kts`, `AndroidManifest.xml` | Executable truth | prose never overrides |
