# Call-Dad (working title — undecided)

Simple, kid-safe Android app for a 6-year-old girl to reach her dad (operator):
**big-button voice call, video call, text + voice-memo chat, photo sharing.**

- 100% native Kotlin. Single-Activity + Compose Navigation + Hilt + ViewModel / StateFlow / SharedFlow.
- Comms basis: `sovereign_mantle` sovereign-comms (donor, read-only) — transport-agnostic call signaling, UDP voice, E2EE frames, rendezvous hole-punch for remote, DTN fallback. No accounts, no cloud by default.
- Firebase / Firestore (paid account available): **deferred to Phase 4** as optional push/signaling fallback. v0.1 is sovereign P2P-first for kid privacy. No `google-services.json` in repo yet — intentional.
- Targets: Moto G 2025 (primary truth device) + BLU View 5. Emulator ≠ device.

## Cold start (every session, in order)

1. `SESSION_HANDOFF.md` (live state)
2. `RULES.md` (**canonical** — wins all conflicts)
3. `blueprints/CURRENT_STATE.md` → `blueprints/CHECKLIST.md`
4. Work from `blueprints/blueprint-sections/BP-*.md`

See `AGENTS.md` for ramp, commands, env pins.

## Layout

```
C:\Call-Dad\
├── AGENTS.md / RULES.md / SESSION_HANDOFF.md / CLAUDE.md
├── SPEC_SHEET.md / SPEC_SHEET.json   # v0.1 scope contract
├── blueprints/                       # MASTER (frozen v0.1) + ROADMAP + CURRENT_STATE + CHECKLIST + CHECKPOINTS + ARCHITECTURE + blueprint-sections/BP-*.md + decisions/ADR-*.md
├── docs/                             # setup-android-studio, device-profiles, firebase-firestore-plan, kid-safe-ux, sovereign-comms-reuse-map
├── app/                              # NATIVE Kotlin app (created in Android Studio; see docs/setup-android-studio.md). Currently placeholder.
├── tools/verify_project.py           # scaffold gate (no-build proof)
├── fixtures/                         # synthetic only — never real child data
├── assets/                           # placeholder art (synthetic)
├── .opencode/agents|commands/        # native-dev / comms-porter / kid-ux-guardian + verify/probe/smoke
└── local.properties.template         # copy to local.properties (gitignored)
```

## Team

- Chief operator: dad (human, builds/installs in Android Studio, owns devices + Firebase + GitHub)
- Chief executor: opencode agent (this lane — scaffolding, code, gates; never builds/installs)
- Chief R&D: Google Gemini (research lane)
- Chief architect: DeepSeek (architecture lane)

Handoff files (`SESSION_HANDOFF.md`, `blueprints/CURRENT_STATE.md`) must stay Gemini/DeepSeek-readable: deltas + next actions + open decisions, no raw dumps.

## Status (2026-09-19, scaffold session)

- Phase 0 scaffold: this directory tree + workflow docs. No app code yet.
- Next (needs operator + architect + R&D alignment): lock ADR-001 (minSdk 30 vs 26), ADR-002 (P2P-first vs Firebase-first for v0.1 signaling), then BP-01 app skeleton authorization.
