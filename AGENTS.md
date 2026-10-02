# AGENTS.md — Call-Dad

> Cold-start ramp. **Canonical law is `RULES.md` — it wins every conflict.**
> Then `SESSION_HANDOFF.md` (live state). Then the blueprint section for the task.

## What this is

Kid-safe native Android app: a 6-year-old video-calls, walkie-talkies and plays games with ONE paired grown-up (v0.1 allowlist = one contact).
Core promise: **one giant Call Dad button that always works; nothing and nobody else reachable.**

Two flavors from one codebase: `parent` (blue, "Call of Daddy (Parent)") and `child` (pink, "Call of Daddy"). Both install side by side.

## Project structure

```
Call-Dad/
├── SPEC_SHEET.md / SPEC_SHEET.json   # original v0.1 scope contract (see ADR-015 for what shipped)
├── AGENTS.md / RULES.md / SESSION_HANDOFF.md / CLAUDE.md / LESSONS_LEARNED.md
├── blueprints/                 # CURRENT_STATE + CHECKLIST + CHECKPOINTS + ROADMAP + ARCHITECTURE + MASTER + FCM_WAKEUP (superseded) + blueprint-sections/BP-01..05 + decisions/ADR-001..018
├── docs/                       # setup-android-studio, device-profiles, firebase-firestore-plan, kid-safe-ux, release-signing, sovereign-comms-reuse-map (last one historical)
├── app/                        # com.calldad — single Activity, Compose, hand-written ViewModel factories (no Hilt)
│   └── src/main/java/com/calldad/
│       ├── data/signaling/     # SignalingClient (Firestore room), SignalingModels (CallRoom ids, SDP/ICE)
│       ├── data/session/       # FamilySession: auth UID + paired peer → room id
│       ├── webrtc/             # WebRTCClient (single-use per call), config, log guardrail
│       ├── fcm/                # push receiver, ringing foreground service, token registrar
│       ├── pairing/            # QR payload, QR generator, peer store (DataStore), ML Kit check
│       ├── audio/ ptt/ helper/ game/ chat/ consent/ photos/ history/
│       ├── navigation/         # AppNavigation.kt — the AppNavHost composable (ring pull-in from any screen, parent-gated pairing)
│       └── ui/                 # screens, components (GiantComponents, VideoRenderer, ParentGate), theme
├── functions/                  # Cloud Function: ring push to the callee's device token (Node 22)
├── firestore.rules             # pair-scoped rooms, owner-only users, get-only pairings
├── tools/verify_project.py     # repo gate (stdlib only)
├── tools/prove_gates_bite.py   # injects a duplicated brace, asserts the gate goes red VIA THE KOTLIN COMPILER and recovers
├── tools/rules-test/           # Firestore rules tests (local emulator)
├── fixtures/ assets/           # synthetic only — never real child data (the game asset is app/src/main/assets/game.html)
```

## Key commands

### READ, SEARCH, AND EDIT WITH THE DEDICATED TOOLS — NOT THE SHELL (RULES §1.4a)

This is the first rule of every session, ahead of every command below.

- Read a file → **`read`**
- Search file contents → **`grep`**
- Find files by name → **`glob`**
- Change a file → **`edit`** (exact string) or **`write`** (whole file)
- **Never** `cat` / `type` / `Get-Content` / `Select-String` / `findstr` / `rg` /
  `Get-ChildItem` / `Test-Path` / `Set-Content` / `Out-File` via the shell. Those
  are denied in `opencode.json`.
- **Batch every lookup.** Issue independent reads/searches as one parallel batch, not
  a sequential chain.
- **Run the gate ONCE at the END of a phase, not after each edit.** Fix everything
  visible first via `grep`/`edit`, then close the phase with one gate run.

### Native app gates (human builds/installs in Android Studio — NEVER assemble/install here)

Run these ONCE, as the closing step of a phase — and via the **`gate` tool**, which is the agent's entry point (RULES §1.4a). The raw gradle lines below are the *operator's* equivalent; an agent should not type them. **Note: no suite here compiles the app.** `assembleParentDebug` is the only check that has ever caught a non-compiling tree, and the gate printed GREEN on one.

```powershell
# repo root. Flavored task names: the unflavored testDebugUnitTest/lintDebug do not exist.
.\gradlew.bat :app:testParentDebugUnitTest :app:testChildDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:lintParentDebug :app:lintChildDebug --no-daemon --console=plain
```

### Repo, function and rules gates (this lane may run)

```powershell
C:\venv-hub\venv\Scripts\python.exe tools\verify_project.py
node --test functions/ring.test.js functions/clip.test.js   # 13 tests: 6 ring + 7 clip; ring.test.js alone gives 6 and reads as a regression
cd tools\rules-test; npm install; npx firebase emulators:exec --only firestore --project demo-calldad "node --test"
```

Node gates need Node on PATH **and** Java for the emulator. If either is missing they
are **SKIPPED, not failed** — say so; never carry a previous run's count forward.

### Operator deploy steps (console / CLI — never from this lane)

```powershell
firebase deploy --only firestore:rules,functions
```

`opencode.json` denies every `firebase*deploy*` for this lane. There is no exception and
there is no narrow allow — if you find one, it is a bug, not a permission.

### Read-only device checks (allowed, no installs)

```powershell
C:\android\sdk\platform-tools\adb.exe devices
C:\android\sdk\platform-tools\adb.exe shell getprop ro.build.version.sdk
```

### Environment

- Python: `C:\venv-hub\venv\Scripts\python.exe` — use as-is. Isolated lane: `C:\venv-hub\call-dad\`.
- Android SDK `C:\android\sdk`; Studio `C:\android\Android Studio` (JBR). JVM 17 toolchain.
- **Toolchain truth is `gradle/libs.versions.toml`** (ADR-004): AGP 8.7.2 / Kotlin 2.0.21 / Gradle 8.13 / compileSdk 35 / targetSdk 35 / minSdk 26 / Compose BOM 2024.10.01 / Firebase BOM 34.19.0 / stream-webrtc-android 1.3.10 / CameraX 1.4.2 / ML Kit barcode 18.3.1 / ZXing 3.5.3 / DataStore 1.1.1. No Hilt, Room, SQLCipher, OkHttp or Concentus in the build.
- Devices: Moto G 2025 (primary) + BLU View 5.
- Firebase: `app/google-services.json` is gitignored and operator-placed (one merged file with both `com.calldad.parent` and `com.calldad.child` clients). TURN creds via gitignored `local.properties` (see template).
- Keystores: debug only; never commit.

## Critical rules (from RULES.md — abridged, not a substitute)

1. **External dirs READ-ONLY** — six Sovereign-family paths + Godot/Unity/VS installs.
2. **NEVER build/install** — lane ends at the gates above. Human builds in Studio.
3. **Git explicit paths only** — never `git add .` / `-A`. Never claim build/device success. No push unless told.
4. **Synthetic data only** — no real child names/photos/numbers/locations/device serials in code, tests, fixtures or docs. Machine-enforced: `tools/verify_project.py` fails the gate on a 14+ digit serial or an `adb-<SERIAL>-…` form in a tracked file. Fixtures need a `synthetic-only` token. Resolve real serials at run time from `adb devices -l`; never bake a mapping into a tool.
5. **Genesis header** on every new `.kt`/`.py` file (verify_project.py enforces it).
6. **Kid-safe + parent-gate** — one paired contact, pairing behind the grown-ups gate and a mutual handshake, no accounts/analytics/ads, and a **parental kill switch** (fail-closed per-scope consent) that is **enforced from vc11 and has never been witnessed on a device**.

## Session workflow

1. Read `SESSION_HANDOFF.md` → `RULES.md` → `blueprints/CURRENT_STATE.md`
2. Work from the relevant ADR / blueprint section
3. Smallest coherent unit + host tests with it
4. Gates green → handoff + checklist + current-state updated → commit by explicit path → **no push unless told**
5. Slash commands in `.opencode/commands/`: `/verify`, `/probe`, `/smoke`, `/sweep`, `/flash`, `/evidence`

## Architecture notes (ADR-015 is the current shape)

- **UI:** single Activity + Compose Navigation. `CallViewModel` is ACTIVITY-scoped (`callViewModel()`): one call session observes the paired room from every screen; AppNavHost pulls the kid to the ring screen from anywhere.
- **Signaling:** Firestore `calls/{uidA_uidB}` (sorted UIDs), seq-numbered generations, status RINGING → CONNECTED → ENDED/DECLINED, trickled ICE arrays. Rules authorize from the room id.
- **Media:** one `WebRTCClient` per call attempt; shared EglBase owned by the ViewModel; teardown order pc → tracks → factory.
- **Wakeup:** Cloud Function `onCallRoomWritten` → data-only FCM to `users/{calleeUid}.fcmToken` → `CallForegroundService` (foreground-first, validates against the paired room, single ringer).
- **Pairing:** grown-ups gate → both phones show + scan QR `{v, uid, nonce}` → peer stored only after `pairings/{peerUid}` names us with the same session nonce.
- **Consent (ADR-017):** fail-closed per-scope grants (`CALL`/`PTT`/`TEXT`/`PHOTO`) live in the pair room; absence DENIES, and revocation is an append-only seq range. Checked at the point of use **and** on every feature's Firestore listener. **vc10 — the build that sat on both phones until 2026-10-02 — enforced none of it on calling or the walkie talkie. vc11 is now built, installed to both phones, and still unwitnessed. No version of this app has ever demonstrably enforced a kill switch.**
