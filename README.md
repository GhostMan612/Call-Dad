# Call of Daddy (repo: Call-Dad)

Simple, kid-safe Android app for a 6-year-old girl to reach her dad (operator):
**one giant Call button for a video call, a walkie-talkie, games to play together, text messages, photo sharing, and an offline voice helper.** Chat and photo sharing are built in source (`vc11`) and are **untested on hardware** — see Status.

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
├── .opencode/agents|commands/        # auditors + verify/probe/smoke/sweep/flash/evidence
└── local.properties.template         # copy to local.properties (gitignored)
```

## Team

- Chief operator: dad (human, builds/installs in Android Studio, owns devices + Firebase + GitHub)
- Chief executor: opencode agent (this lane — scaffolding, code, gates; never builds/installs)
- Chief R&D: Google Gemini (research lane)
- Chief architect: DeepSeek (architecture lane)

Handoff files (`SESSION_HANDOFF.md`, `blueprints/CURRENT_STATE.md`) must stay Gemini/DeepSeek-readable: deltas + next actions + open decisions, no raw dumps.

## Status (2026-10-01)

- **Source** `vc11 / 0.3.1`. **Both phones** are on `vc10 / 0.3.0` (flashed 2026-10-01) — source is one pass ahead and **not installed**.
- **Backend is live.** `firestore.rules` released 2026-10-01 (chat, photos, consents, revocations, `negotiationRound`), plus `onCallRoomWritten` and `onPttClipWritten`.
- Host gates green against `4a5c555`: verify PASS · unit **518 / 0 failures** · lint **0 errors** · functions **13/13** · rules emulator **44/44**.
- **Two things that gate does not tell you:**
  1. **No `SPEC_SHEET` §2 feature has ever been exercised on hardware.** Not one call, text, photo, consent grant or ICE restart on any current build. The only proven E2E call is from vc7.
  2. **No version of this app has ever demonstrably enforced a parental kill switch.** vc10 — which is what is on both phones — presents "Turn everything off" while calling, the walkie talkie and photo/chat downloads continue unaffected. Fixed in vc11, in source only.
- Also: no suite in this repo compiles the app. `8f47512` was pushed to `origin/main` with a duplicated brace and the gate printed GREEN on it; `4a5c555` fixed it. `assembleParentDebug` is the only thing that has ever caught that class.
- Next: the operator flashes **vc11** and runs the device matrix — the last item of which is the kill switch end to end, which no one has ever watched work (`SESSION_HANDOFF.md`).
