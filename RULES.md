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
- **A blocked commit is an emergency, not a note for the handoff.** `git*add*` / `git*commit*` are `ask`, so green-but-uncommitted work can vanish entirely via a revert or a sync — it happened here. If a commit is denied, stop and escalate in the same turn; never end a session carrying uncommitted work and call it delivered. Details in §1.4a.
- Source: pathfinder `§1.3`, Atlas `§1.4`, Vision `§1.4`.

### 1.4a Tool use — the shell is a LAST resort, not a default (HARD RULE)

Added 2026-09-30 after three days of a plan being dominated by shell round-trips
for things `read`/`grep`/`glob`/`edit` already do. This is the single biggest
time sink in the repo's history. It is a hard rule because the failure mode is
silent: shelling does not look wrong, it just burns a round trip and the
operator's attention every 5–10 seconds for a file read.

**NEVER use the shell to read, search, inspect, or edit files. Dedicated tools exist.**

| Instead of | Use |
|---|---|
| `Get-Content`, `type`, `cat <file>` | **`read`** tool |
| `Select-String`, `grep`, `findstr`, `rg`, `Get-ChildItem -Recurse` | **`grep`** tool (content) / **`glob`** tool (filenames) |
| `Set-Content`, `>>`, `Out-File`, `-replace` | **`edit`** tool (exact string) or **`write`** tool (full file) |
| `Test-Path` | **`glob`** tool, or just `read` it |
| `git status` / `git diff` / `git log` to *read* | `git` is a legitimate shell use, but batch it — never one call per file |
| Piping Gradle/npm output through `Select-String` | Run the gate, accept full output, read it once |

**Batching is mandatory.** When a task needs many lookups, plan them and issue
them as ONE batch of parallel tool calls. A sequential chain of single-purpose
shell commands is the failure this section exists to prevent.

**Run a gate with the `gate` TOOL, not with a raw gradle invocation.** The
`gradlew :app:test...` command is the *human's* entry point and the operator
runbooks in `.opencode/commands/` legitimately show it. The agent's entry point
is the `gate` tool. Reaching for the raw task is what turned a single phase into
six sequential test runs on 2026-09-30 — and the pipe I wanted (`Select-String`
on gradle output) is denied, so the "efficient" path was never available. If you
find yourself wanting to filter gate output, you are in the wrong tool.

**This rule has already been broken once. Read that as evidence, not history.**
The deny list and this section's wording were both present, and the gate was
green, the whole time the per-edit loop was happening. A test can pin the deny
list and pin this text; neither can observe what you actually ran. So the
sanctioned command is named here, in the same breath as the rule, and
`ToolUseDisciplineTest` asserts this section, the skill, and the gate tool's own
description all agree on it.

**The shell is for:** running a gate (test / lint / verify / emulator / functions
test / build), `git` mutations the operator ordered, `adb` read-only evidence, and
`winget`/`npm -g` installs. It is **not** for reading or writing files.

**Test/analyze once, at the very END of a phase — not per edit.** Do not run a
gate after every single edit. Fix everything you can see, using `grep`/`edit`,
then run the gate once as the phase's closing step. Interleaving one gate per
edit is what turned a one-phase task into three days.

**A phase that cannot be committed is not finished — and a blocked commit is
an emergency, not a note for later.** `git*commit*` and `git*add*` are `ask`,
so a denied or unapproved commit leaves finished work sitting uncommitted in a
working tree where it is one `git reset`, one tool-side revert, or one
cloud-session sync away from being **gone with no trace in git history**.
That already happened here: the K12/K21 fixes were gated green, the commit
was blocked, the work was never re-applied, and the only reason anyone found
out was a source-scan test that failed on the missing code. Rules that keep
happening:

- **If a commit you were asked to make is blocked, say so immediately and
  stop.** Do not end the turn with "you may want to run this" and move on.
  Ask the operator to run the exact command, or get the block lifted.
- **Never treat "gates green" as "shipped".** A green gate certifies the tree
  as it stands *in the working directory*. It is not a claim that the work is
  recorded anywhere durable.
- **Before ending a session, confirm the work is in a commit.** If it is not,
  the session has not delivered. `git status` clean is the finish line.

Corollary: if you catch yourself shelling to look at a file you have already
read, or to check whether an edit you just made landed, **stop and use `read`/`grep`.**

- Source: operator correction 2026-09-30 (Contract 10/11, after the PTT phase).

### 1.5 BUILD BOUNDARY — HARD RULE
- NEVER run: `assemble*`, `install*`, `connected*`, `flutter build/run`, emulator installs. The human builds + installs in Android Studio on Moto G 2025 / BLU View 5.
- **Printing** an `assemble*` / `install*` command inside a runbook handed to the operator is permitted; **running** one is not. The `.opencode/commands/flash.md` command (`/flash`) is the sanctioned form.
- Your lane ends at source correctness: `./gradlew :app:testParentDebugUnitTest :app:testChildDebugUnitTest :app:lintParentDebug :app:lintChildDebug` (run from the repo root; the parent/child flavors mean the unflavored `testDebugUnitTest`/`lintDebug` tasks do not exist), `tools/verify_project.py`, `node --test functions/*.test.js` (**13 tests: 6 ring + 7 clip** — running only `ring.test.js` gives 6 and reads as a regression), the Firestore rules tests in `tools/rules-test/` (local emulator), `adb devices` / read-only `adb shell getprop`. **No suite in this repo compiles the app.** `8f47512` was pushed to `origin/main` with a duplicated brace and the full gate printed GREEN on it; `assembleParentDebug` is the only thing that has ever caught that class, and it is an `ask` task. Compiling via the unit-test tasks is source verification, not a build claim.
- **The two `node` gates need `node` on PATH (plus Java, for the Firestore emulator).** Where `node` is absent, `node --test functions/*.test.js` and the `tools/rules-test/` emulator are **SKIPPED, not failed**, and a previous run's count must never be carried forward as if it were this session's.
- There is **no `androidTest/` source set** in this repo. Every device result comes from a human run in Android Studio on Moto G 2025 / BLU View 5. If an instrumented suite is ever added it is the human's to run.
- Debug device failures from the human's pasted output — never by rebuilding locally.
- **Every terminal write is generation-checked.** A teardown that cannot see the current `seq` is a bug: it can cancel a newer call the peer is ringing. `SignalingClient.finishCall` and `finishCallDetached` both transact on `seq`; keep it that way.
- Source: Vision `§1.5`, Atlas `§1.6`, Recovery `§1.5`.

### 1.5a Machine-enforced boundaries (`opencode.json`, repo root)
`opencode.json` is the enforcement layer for §1.1, §1.4 and §1.5. It denies `firebase*deploy*`, `git*add*-A*|--all*|-u*`, `adb*uninstall*|push*|root*` and `adb*shell*pm*`; it gates `gradlew*assemble*|install*|connected*|clean*` and `git*add*|commit*|push*` behind `ask`; and it allowlists the writable paths. A denied command is not a suggestion to retry — report it and stop.

**Never write a real device serial, the operator's username, or any other machine identity into a committable file.** Ask for the serial at run time; `adb devices -l` has it.

**This is MACHINE-ENFORCED, not prose.** `tools/verify_project.py` fails the gate on a USB-style serial (14+ digits) or an adb-mDNS serial (`adb-<SERIAL>-…`) in any tracked text file, naming the match. It was added because this law existed for two sessions while six tracked files violated it — the tool that enforces the privacy rule was itself the source of the leak. Do not "work around" the gate by deleting a real serial from the ban list; redact the serial in the file instead. Fixtures may hold synthetic identifiers only, and must carry the literal token `synthetic-only`.

### 1.5b The agent fleet (`.opencode/`)
Read-only auditors (none may edit): `call-core-auditor`, `webrtc-media`, `firestore-rules-auditor`, `fcm-wakeup-auditor`, `doc-drift-auditor`, `kid-ux-guardian`, `native-dev`, `comms-porter`. Execution: `gate-runner`. Records: `handoff-writer`. Tools: `gate`, `device-evidence`, `contract-diff`. Commands: `/verify`, `/probe`, `/smoke`, `/sweep`, `/flash`, `/evidence`. A subagent's claim is not evidence: re-read the line before acting on it.

### 1.5c Device state is the operator's to hand back
Any device check leaves the phone however it found it. Before finishing, confirm nothing that evidence-gathering changed is still changed: **Wi-Fi network joined, airplane mode off, Bluetooth on, DND off, font scale and display size at default, no leftover `adb` pairings, no pulled fixtures left behind.** Restore anything that drifted and say which. Leaving a phone in a test state reads to a parent as "the app broke my phone" — the device belongs to the operator, not the lane.

### 1.6 Nothing outside the project without approval
Do not install software, modify system settings, or write outside `C:\Call-Dad` / `C:\venv-hub\call-dad\` / approved tool homes without asking first.

### 1.7 Kid-safe law (Call-Dad specific, non-negotiable)
- Allowlist-only contacts. v0.1 allowlist = Dad. No dial-pad to arbitrary numbers, no open discovery, no `CallRoute` broadcast — `Direct` only.
- Adding a contact requires parent approval (adapted mantle `DualKeyGate` pattern: biometric/parent-gate, never kid-self-service).
- No accounts, no analytics, no ads, no third-party SDKs phoning home in v0.1 (each new dep needs ADR justification).
- Big-button UX: one giant Call Dad, one-tap hangup. **Reconnect is scoped to a recoverable network drop** (ICE-restart, §1.7a) — it is not LAN-only calling and not the symmetric-NAT proof K8 never made; if the restart does not recover, the call still ends at `LOST_GRACE_MS` and the kid is returned home. **Unwitnessed on any device.** Kid can never get stuck or lost.
- Chat/PTT/photo events that would sync to any hub are redacted by default (mantle `hub_sync_bridge` posture: chat/PTT/telemetry/biometric never leave device without explicit parent opt-in).
- **Consent is fail-closed, and enforced at the point of use (ADR-017).** Scopes are `CALL`, `PTT`, `TEXT`, `PHOTO`; **absence of a grant DENIES.** Revocation is an append-only seq range, so a grant write cannot destroy it and a parent can deliberately re-authorise.
  - **Every consumer must read a scope — the screen AND the Firestore listener.** A gate that only stops a UI drawing is a switch, not a kill switch. A feature whose own gate nobody reads is a decorative feature, and that is the recurring defect in this repo.
  - **vc10, the build that sat on both phones until 2026-10-02, shipped a kill switch that enforced nothing** on calling or the walkie talkie, in either direction, and did not stop photo/chat downloads; the parent's own grant sequence was unreadable on the parent's phone, so the button could not act there. Fixed in vc11, which is now built and installed to both phones — **and still unwitnessed.**
  - **No version of this app has ever demonstrably enforced a kill switch.** Do not record one as working until a person has watched a child lose a permission.
  - When a feature is finished, **grep for what it should have touched and read the list.** Four of the worst bugs in this project were complete, documented, green-gated features that no consumer had actually wired up.

### 1.7a Recorded architecture deviations (operator to ratify)
§1.7 and §2 were written for the original LAN/UDP donor-port plan. The shipped app took these routes by ADR; this block records them so the law matches the code until the operator amends §1.7/§2 directly:
- **Accounts / network services:** Firebase anonymous Auth + Firestore signaling + FCM wakeup + Cloud Function (ADR-002, ADR-007, ADR-013, ADR-015). No analytics, no ads, no user-visible accounts. Calls need internet for signaling; LAN-only calling (§2.3) is not implemented.
- **Media + crypto:** WebRTC (Stream fork) with DTLS-SRTP end-to-end media encryption (ADR-005) replaces the custom ECDH + AES-GCM frame cipher (§2.5).
- **Call flow:** the 7-state machine in `CallState.kt` (§2.6's Invite→Accept/Decline→End, extended with NoAnswer/Error).
- **Allowlist:** exactly one paired contact per phone, via a pair-scoped Firestore room that only those two UIDs can touch; pairing is parent-gated and mutual (ADR-015).
- **Reconnect: ICE-restart, implemented 2026-09-30 (source).** §1.7's auto-reconnect promise is now satisfied for a recoverable network drop. `WebRTCClient.createOffer(iceRestart = true)` adds the `IceRestart` media constraint, which is what makes libwebrtc discard the dead candidate set and re-gather (a plain re-offer would carry the same unreachable candidates and reconnect to nothing). `SignalingClient.publishRenegotiation` / `publishRenegotiationAnswer` carry the restart OFFER/ANSWER within the same generation, but keyed by a **monotonic `negotiationRound`**, NOT by `seq`. A `seq`-keyed guard **shipped broken and was replaced 2026-10-01**: the caller had already consumed `seq` for the original answer, so the restart answer was compared against a sequence it had passed and silently discarded — the restart could never complete on any round. See ADR-010 §(7). The restart offer must not go through `publishOffer`, which would start a new generation, re-ring the peer mid-call and clear the exchanged candidates; publishing it also **deletes the stale answer** in the same write, or Firestore's echo of the caller's own write races the real answer and applies the wrong SDP. **Still unwitnessed: the first exercise of the fix is an open device check.** `CallViewModel.attemptIceRestart` publishes once per `LOST_RESTART_COOLDOWN_MS` (5s), caller-side only, because both sides restarting at once would overwrite each other's SDP. **The kid is never stranded:** if the restart does not recover the connection, the call still ends at `LOST_GRACE_MS` and the child returns home (§1.7a). This is a network-drop recovery, NOT the LAN-only calling in §2.3 and NOT the symmetric-NAT carrier proof K8 never made — a symmetric NAT can still fail with no path to recover from. `ConnectionHealth.RECONNECTING` remains unassigned and the UI renders "Connection lost…"; do not add the word "Reconnecting" without also assigning that state, and do not widen this entry to claim more than a re-gather on the same transport.
- **PTT leaves the device.** §1.7's last bullet is not satisfied by the walkie-talkie as shipped: clips are written to the pair's Firestore room (`ADR-016`) with **no parent opt-in, no setting, and no disclosure**. A parent must be told this before the feature is relied on. Clips are deleted only after they actually play to completion; a clip that fails to decode, is interrupted by a ring, or cannot be deleted is retained rather than silently destroyed.
- **ML Kit phone-home — ACCEPTED, operator sign-off 2026-09-30 (K9 CLOSED).** §1.7's "no third-party SDKs phoning home" has exactly one named exception. Unbundled ML Kit barcode scanning downloads its model from Google Play Services on first pairing scan and sends Play Services usage metrics to Google. **What leaves the device:** the request to fetch the barcode model, and anonymous Play Services usage telemetry. **What does NOT:** any image, any camera frame, any QR payload, any UID, any pairing nonce, any audio, any video. QR decoding is entirely on-device. The operator weighed this against ZXing-only decode and chose ML Kit because pairing is the one step that must not fail for a six-year-old's grown-up — a barcode read that works on a crooked, dim, moving phone is worth more than a 2.2MB APK saving. The on-device availability check is `ModuleAvailabilityCheck.kt`, and pairing already falls back to ZXing if the module is missing. Revisit only if the app ever handles media that must never transit Google infrastructure, in which case the exception does not cover it.
- **TURN relay is a third party in the media path — ACCEPTED, operator sign-off 2026-09-30 (K8 CLOSED).** The default relay is Open Relay, a public service run for open-source video with published long-lived credentials. WebRTC media stays DTLS-SRTP encrypted end-to-end, so the relay carries ciphertext and can see metadata (IPs, timing, volume) but not content. Those credentials are extractable from the APK; that is accepted for a two-person family app and is **not** acceptable for anything handling real child media on untrusted networks. `local.properties` overrides `TURN_URLS`/`TURN_USER`/`TURN_PASS` for a private relay, and that is the path to a real deployment. Never log these values (guardrail §G).

---

## 1A. CONTEXT & OUTPUT DISCIPLINE

- **Do not filter or pipe terminal output.** Read it once and take the summary from the gate's own verdict line (see §1.4a — piping build output through `Select-String` is denied). Never ingest passing noise.
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
5. **E2EE by pattern:** per-call ECDH + AES-256-GCM per-frame (mantle `AudioFrameCipher` idiom); null-on-fail = drop frame, never log key material. **SUPERSEDED (§1.7a, ADR-005): there is no raw frame path — WebRTC's DTLS-SRTP covers the media path, and `AudioFrameCipher` is not in the build.**
6. **Explicit ops:** call setup is `Invite→Accept/Decline→End` state machine (mantle `CallSignalingManager`); photo send is chunked + reassembled + verified (mantle `SovereignImageEngine`); manifests + undo where destructive.

---

## 3. TECHNICAL LAWS

| Law | Rule | Source |
|-----|------|--------|
| PowerShell mojibake BAN | NEVER modify source via `Get-Content/-replace/Set-Content`, `Out-File`, `Add-Content`. PS 5.1 mis-reads UTF-8 → mojibake. Editor tools only; Python with `encoding='utf-8'` if scripted; then analyze/lint + signature grep. | tagger `§3.1`, Vision `§3` |
| PS binary pulls | NEVER pipe `adb pull` / binary output through PS pipes — write straight to file. | Atlas `§3` |
| UI thread | Audio/call work off Main; Compose collects `StateFlow` with lifecycle; every `collect` after navigation needs lifecycle guard. No blocking calls in composables. | pathfinder port + tagger async law |
| Storage | MediaStore / SAF + app-private files only. Temp under cache; clean up on failure too. Runtime media/camera/mic permissions via Accompanist/permissions; `createWriteRequest` on Android 11+ where needed. **SUPERSEDED: no Accompanist and no `createWriteRequest` — neither is in `libs.versions.toml`. Runtime asks are hand-written (`ui/permissions/CallPermissions.kt`, `MainActivity.kt`).** | tagger Storage law, Vision `§3` |
| Audio | `AudioStreamingSession`/`LiveCallSession` socket template; Opus via Concentus; serialized executor for encode; jitter buffer on receive. Never log raw audio. **SUPERSEDED (§1.7a, ADR-005): WebRTC supplies DTLS-SRTP and Opus internally. `AudioStreamingSession`, `LiveCallSession`, `JitterBuffer` and Concentus are NOT in the build and are not to be added.** | mantle comms |
| Crypto | Non-exportable Keystore keys; SQLCipher passphrase wrapped by Keystore; per-call ECDH ephemeral; `MemoryScrubber` idiom for key bytes. **SUPERSEDED: SQLCipher is a deferral-with-justification (ADR-003, re-open condition sharpened by ADR-018) and `MemoryScrubber` is not in the build. WebRTC's DTLS-SRTP covers the media path (ADR-005).** | mantle security |
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
