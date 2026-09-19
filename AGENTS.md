# AGENTS.md — Call-Dad

> Cold-start ramp. **Canonical law is `RULES.md` — it wins every conflict.**
> Then `SESSION_HANDOFF.md` (live state). Then the blueprint section for the task.

## What this is

Kid-safe native Android app: 6-year-old calls/texts/video-chats/shares photos with Dad only (v0.1 allowlist = Dad).
Core promise: **one giant Call Dad button that always works on LAN; remote works via rendezvous; nothing else reachable.**

Donor stack (read-only, copy out + adapt): `C:\sovereign_mantle\android_node` comms — see `docs/sovereign-comms-reuse-map.md`.

## Project structure (see `blueprints/ARCHITECTURE.md`)

```
C:\Call-Dad\
├── SPEC_SHEET.md / SPEC_SHEET.json
├── AGENTS.md / RULES.md / SESSION_HANDOFF.md / CLAUDE.md
├── blueprints/                 # MASTER (frozen v0.1) + ROADMAP + CURRENT_STATE + CHECKLIST + CHECKPOINTS + ARCHITECTURE + blueprint-sections/BP-*.md + decisions/
├── .opencode/agents|commands/  # native-dev / comms-porter / kid-ux-guardian + verify/probe/smoke
├── docs/                       # setup, devices, firebase-plan, kid-safe-ux, comms-reuse-map
├── app/                        # native Kotlin app: com.calldad (planned), single-Activity + Compose + Hilt; created by human in Android Studio
├── tools/verify_project.py     # scaffold gate (stdlib only, uses hub python as-is)
├── fixtures/                   # synthetic fixtures only
└── assets/                     # synthetic placeholder art
```

## Key commands

### Native app (human builds/installs in Android Studio — NEVER here)

```powershell
cd app
.\gradlew testDebugUnitTest --no-daemon 2>&1 | Select-Object -Last 5
.\gradlew lintDebug --no-daemon 2>&1 | Select-Object -Last 5
# NEVER: assemble*|install*|connected*|build apk|run — human does that in Studio
```

### Scaffold gate (this lane may run)

```powershell
C:\venv-hub\venv\Scripts\python.exe tools\verify_project.py
```

### Read-only device checks (allowed, no installs)

```powershell
C:\android\sdk\platform-tools\adb.exe devices
C:\android\sdk\platform-tools\adb.exe shell getprop ro.build.version.sdk
```

### Environment (verified 2026-09-19)

- Python: `C:\venv-hub\venv\Scripts\python.exe` (3.14.6) — use as-is. Isolated lane: `C:\venv-hub\call-dad\` (you may create venvs/install there; never touch shared `venv/`, `server.py`, `WakeHub.bat`).
- Android SDK: `C:\android\sdk` (platforms 24/31/33/34/35/36/36.1/37.0; build-tools up to 37.0.0; NDK 27.0.12077973 + others; cmake 3.22.1/4.1.2; platform-tools adb 37.0.1; licenses accepted).
- Studio: `C:\android\Android Studio` (AI-261.26222.65.2614.16379836, JBR 25). `JAVA_HOME=C:\android\Android Studio\jbr`. Gradle/AGP wants Java 17/21 toolchain — see `docs/setup-android-studio.md`.
- Toolchain pins (donor-proven, frozen until ADR): AGP 8.13.2 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29 / Gradle 8.13 / JVM 17 / Compose BOM 2024.12.01 / Room 2.6.1 / OkHttp 4.12.0 / CBOR 1.7.3 / Concentus-Opus 1.0.2 / CameraX 1.3.4 / ZXing 3.5.3. App: compileSdk 35 / target 35 / minSdk 30 (ADR-001 revisits 26 for old kid tablet).
- Devices: Moto G 2025 (`device profiles/Moto_G_2025`, target android-36, primary truth) + BLU View 5. Profiles/scripts in `C:\android\device_profiles\`.
- Keystores: only `C:\android\keystores\tacplan-debug.keystore` exists. No `call-dad` keystore yet — debug only until operator provisions (never commit).
- Firebase: paid account available; **no `google-services.json` anywhere yet — intentional** (Phase 4 fallback, see `docs/firebase-firestore-plan.md`).
- GitHub CLI / Firebase CLI: not on PATH. Git only by explicit path.

## Critical rules (from RULES.md — abridged, not a substitute)

1. **External dirs READ-ONLY** — six Sovereign-family paths + Godot/Unity/VS installs. Copy out, edit inside.
2. **NEVER build/install** — lane ends at `testDebugUnitTest` + `lintDebug` + `verify_project.py`. Human builds in Studio.
3. **Git explicit paths only** — never `git add .` / `-A`. Never claim build success. No push unless told.
4. **Synthetic data only** — no real child names/photos/numbers/locations in code/tests/fixtures.
5. **Genesis header** on every new `.kt`/`.py` file.
6. **Kid-safe + parent-gate** — allowlist-only (Dad v0.1), Direct route only (no open broadcast), contact-add needs parent approval, no accounts/analytics/ads.

## Session workflow

1. Read `SESSION_HANDOFF.md` → `RULES.md` → `blueprints/CURRENT_STATE.md`
2. Work from relevant `blueprints/blueprint-sections/BP-*.md`
3. Smallest coherent unit + tests/fixtures with it
4. Gates green → handoff + checklist + current-state updated → commit by explicit path → **no push unless told**
5. Slash commands in `.opencode/commands/`: `/verify`, `/probe`, `/smoke`

## Architecture notes (see `blueprints/ARCHITECTURE.md` for full)

- **UI:** single-Activity + Compose Navigation, one ViewModel per screen, `StateFlow<UiState>` + `SharedFlow<Event>` one-shots (pathfinder KOTLIN_PORT_SPEC §1 pattern).
- **Comms:** port `CallSignalingManager` (Invite→Accept/Decline→End) + `SovereignCommsEngine` (chat/voice-memo + receipts) + `LiveCallSession` (:8789 UDP voice) + `AudioFrameCipher` (AES-GCM per-frame) + `SovereignImageEngine` (chunked photo) + `RendezvousClient` + relay `main.go` (:8792/udp). See reuse map.
- **Data:** Room (contacts allowlist, call log, message store) + SQLCipher at rest (passphrase wrapped by Keystore). No cloud in v0.1.
- **Consent:** Dad-grants-Kid scope cert `[call,text,photo]` + expiry (modeled on mantle `consent.py`); revocation = kill switch.
