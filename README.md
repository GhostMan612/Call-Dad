# Call of Daddy (repo: Call-Dad)

Simple, kid-safe Android app for a 6-year-old girl to reach her dad (operator):
**one giant Call button for a video call, a walkie-talkie, games to play together, and an offline voice helper.** Chat and photo sharing are still roadmap.

- 100% native Kotlin. Single Activity + Compose Navigation + ViewModel / StateFlow (hand-written factories, no Hilt).
- Two flavors: `parent` (blue) and `child` (pink), installable side by side.
- Calls: WebRTC video (stream-webrtc-android), Firestore signaling in a room only the two paired phones can touch, FCM push to wake the callee's phone (ADR-002/005/015). Anonymous Firebase auth; no analytics, no ads.
- Pairing: the grown-ups gate, then both phones scan each other's QR code.
- `app/google-services.json` is operator-placed and gitignored.
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
├── app/                              # native Kotlin app (com.calldad), parent/child flavors
├── functions/                        # Cloud Function: ring push (Node 22)
├── firestore.rules                   # pair-scoped rooms; tests in tools/rules-test/
├── tools/verify_project.py           # repo gate (no-build proof)
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

## Status (2026-09-24)

- Video calling, ringing (including a killed app), walkie-talkie, games and the voice helper are all in code, and the host gates are green (see `blueprints/CURRENT_STATE.md`).
- Next: the operator deploys rules + functions, installs both flavors, re-pairs the phones, and runs the device matrix (`blueprints/CHECKPOINTS.md` G-C8).
