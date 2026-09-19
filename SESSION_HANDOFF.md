# SESSION_HANDOFF.md — Call-Dad (live state)

> Update every session per RULES.md §4.2. Cold-start entry point after RULES.md.
> Keep Gemini/DeepSeek-readable: deltas + next actions + open decisions. No raw dumps.

## DOCUMENT MAP — cold-start hooks (read top-to-bottom)

| # | File | Holds | When to read |
|---|------|-------|--------------|
| 0 | `AGENTS.md` (root) | Compact ramp: structure, commands, env, architecture | automatic |
| 1 | `RULES.md` | **CANONICAL** operating law | EVERY session, before any edit |
| 2 | THIS FILE | Latest deltas, next actions, open decisions, toolchain notes | EVERY session |
| 3 | `blueprints/CURRENT_STATE.md` | Verified per-file map, known-issue registry, toolchain freeze | before writing code |
| 4 | `blueprints/ROADMAP.md` | Phase tracker (0–5) with gates | when planning/phases |
| 5 | `blueprints/CALL_DAD_MASTER_BLUEPRINT.md` | Frozen product spec (v0.1 target) | before novel features |
| 6 | `blueprints/ARCHITECTURE.md` | UI/comms/data split, storage flow | structural changes |
| 7 | `blueprints/blueprint-sections/BP-*.md` | Executable task slices per phase | task work |
| — | `SPEC_SHEET.md/.json` | v0.1 scope contract | scope questions |

Conflict law: RULES.md > other docs; executable files (`*.gradle.kts`, `AndroidManifest.xml`) > prose.

## Where we are (2026-09-19, Phase 1 scaffold landed — executor lane, UNCOMMITTED)

- **Operator Phase 1 scaffold WRITTEN to `app/`:** `com.calldad.app`, 15 `.kt` (MainActivity, Routes/AppNavHost, Color/Type/Theme, GiantComponents, Home+VM, Call+VM, Ptt+VM, Game, Helper+VM) with Genesis headers prepended per RULES §2, Manifest (portrait, no perms — Phase 2 uncomment block kept), `themes.xml`/`colors.xml` (Manifest `@style/Theme.CallDad` satisfied), `RoutesTest` (pure-JVM), module + root Gradle + `libs.versions.toml` + wrapper props (no `gradlew` binaries — Studio generates on sync).
- **Deltas vs frozen spec (executable truth wins, DeepSeek/Gemini to rule):** package `com.calldad.app` (was `com.calldad` — SPEC amended); routes Home/Call/**Ptt/Game/Helper** (was Chat/Photo/Log — deferred to BP-03/04); minSdk **26** (ADR-001 DECIDED B); BOM **2024.10.01** (drift from frozen 2024.12.01 — flagged); no Hilt/Room/Hilt yet (BP-02+); placeholders: 1.5s fake connect, `cannedReply()`, PTT mic hooks, WebView hook.
- **Gates:** G0 GREEN (re-run next). G1 PENDING human Studio run (`testDebugUnitTest` + `lintDebug` + Moto G install proof). This lane ran no Gradle (build boundary).
- **GitHub:** repo `https://github.com/GhostMan612/Call-Dad` recorded. Local git NOT yet init (next step this session: init + remote, commit explicit paths, NO push).

## Where we were (2026-09-19, scaffold session — executor lane)

- **Scaffold COMPLETE (uncommitted):** root workflow (`AGENTS/RULES/SESSION_HANDOFF/CLAUDE/README/SPEC_SHEET`), `blueprints/` (MASTER + ROADMAP + CURRENT_STATE + CHECKLIST + CHECKPOINTS + ARCHITECTURE + BP-01..05 + ADR-001..003), `docs/` (5 guides), `.opencode/` agents + commands, `tools/verify_project.py`, `fixtures/`, `assets/`, `app/` placeholder, `.gitignore`, `local.properties.template`, isolated lane `C:\venv-hub\call-dad\`.
- **Env surveyed (read-only, nothing modified outside):** `C:\android` SDK/platforms/build-tools/NDK/cmake/adb/licenses + Studio build + JBR 25 + Moto_G_2025/BLU_View_5 AVDs; `C:\venv-hub` python 3.14.6; six Sovereign-family trees surveyed for workflow + mantle comms donor map (`docs/sovereign-comms-reuse-map.md`).
- **Gates:** `tools/verify_project.py` GREEN (2026-09-19: VERIFY PASS 10 dirs + 28 files). No app code — no unit/lint/device gates yet.
- **Team alignment PENDING:** operator setting up Gemini (R&D) + DeepSeek (architect). Awaiting further instructions after scaffold.

## Next actions (for operator + Gemini + DeepSeek)

1. Operator: review scaffold, confirm app name ("Call Dad" working title) + package (`com.calldad` proposed).
2. DeepSeek (architect): rule on ADR-001 (minSdk 30 vs 26 for old kid tablet), ADR-002 (P2P-first vs Firebase-first v0.1 signaling), ADR-003 (Room+SQLCipher vs plain Room v0.1).
3. Gemini (R&D): validate reuse map (mantle `CallSignalingManager`/`LiveCallSession`/`AudioFrameCipher`/`SovereignImageEngine`/`RendezvousClient+main.go`) + propose video-call approach (CameraX + custom UDP vs WebRTC — WebRTC NOT in donor; needs research).
4. Joint: authorize BP-01 (native app skeleton in Android Studio — human creates `app/` via wizard, executor wires packages/manifest + first unit test).
5. Human device step (later): Studio creates skeleton → installs debug on Moto G → pastes `adb devices` + launch proof → executor records in CURRENT_STATE (no build claims from this lane).

## Open decisions

- D1: App name + launcher label + icon (kid-friendly, big-type). Owner: operator.
- D2: minSdk 30 (donor default) vs 26 (old kid tablet reuse). Owner: DeepSeek (ADR-001).
- D3: v0.1 signaling: sovereign P2P+LAN+rendezvous only (recommended) vs Firebase/FCM assist. Owner: DeepSeek + Gemini (ADR-002). Firebase paid account ready but unused.
- D4: Video: extend `LiveCallSession` UDP pattern with CameraX frames (recommended spike) vs adopt WebRTC (new dep, needs ADR). Owner: Gemini research.
- D5: GitHub repo init + remote: when + who pushes (no push until told). Owner: operator.

## Toolchain notes (2026-09-19, verified read-only)

- SDK `C:\android\sdk`: platforms 24/31/33/34/35/36/36.1/37.0; build-tools 34–37; NDK 27.0.12077973 (donor pin) + 28/30; cmake 3.22.1 (donor pin); adb 37.0.1; licenses accepted.
- Studio `C:\android\Android Studio` AI-261.26222.65.2614.16379836; JBR 25; `JAVA_HOME=C:\android\Android Studio\jbr`.
- Pins (frozen until ADR): AGP 8.13.2 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29 / Gradle 8.13 / JVM 17 / Compose BOM 2024.12.01 / Room 2.6.1 / OkHttp 4.12.0 / CBOR 1.7.3 / Concentus 1.0.2 / CameraX 1.3.4 / ZXing 3.5.3.
- Keystores: only `tacplan-debug.keystore`; no `call-dad` keystore (debug only for now).
- Firebase: paid, zero `google-services.json` (intentional — Phase 4 fallback).
- `gh` / Firebase CLI: not on PATH.
