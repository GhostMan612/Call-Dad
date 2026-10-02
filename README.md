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
├── tools/prove_gates_bite.py         # injects a duplicated brace, asserts the gate goes red VIA THE KOTLIN COMPILER and recovers
├── fixtures/                         # synthetic only — never real child data
├── assets/                           # placeholder art (synthetic); the game asset is app/src/main/assets/game.html
├── .opencode/agents|commands/        # auditors + verify/probe/smoke/sweep/flash/evidence
└── local.properties.template         # copy to local.properties (gitignored)
```

## Team

- Chief operator: dad (human, builds/installs in Android Studio, owns devices + Firebase + GitHub)
- Chief executor: opencode agent (this lane — scaffolding, code, gates; never builds/installs)
- Chief R&D: Google Gemini (research lane)
- Chief architect: DeepSeek (architecture lane)

Handoff files (`SESSION_HANDOFF.md`, `blueprints/CURRENT_STATE.md`) must stay Gemini/DeepSeek-readable: deltas + next actions + open decisions, no raw dumps.

## Status (2026-10-02)

- **Source and both phones are the same build: `vc11 / 0.3.1`.** Flashed 2026-10-02 (child 10:17:56, parent 10:18:11), one flavor per device, installed `-r` so pairing and the consent grant survived. **No re-pair needed.**
- **Backend is live.** `firestore.rules` released 2026-10-01 (chat, photos, consents, revocations, `negotiationRound`), plus `onCallRoomWritten` and `onPttClipWritten`.
- Host gates green: verify PASS · unit **538 (269 per flavor) / 0 failures** · lint **0 errors** · functions **13/13** · rules emulator **44/44**.
- **The build gate passed too, for the first time in this project's history:** `clean assembleParentDebug assembleChildDebug` BUILD SUCCESSFUL, 77 tasks, **77 executed, 0 from cache** (`--no-build-cache`, so the Kotlin compiler genuinely ran). `8f47512` was pushed to `origin/main` with a duplicated brace and the host gate printed GREEN on it; `e0cb047` is the first commit proven to compile by the compiler rather than merely green.
- **Two things none of that tells you:**
  1. **No `SPEC_SHEET` §2 feature has ever been exercised on hardware.** Not one call, text, photo, consent grant or ICE restart on vc11. The only proven E2E call is from vc7. An install is not a witness.
  2. **No version of this app has ever demonstrably enforced a parental kill switch.** vc10 — which was on both phones until 2026-10-02 — presented "Turn everything off" while calling, the walkie talkie and photo/chat downloads continued unaffected. Fixed in vc11, **installed, still unverified**.
- Next: the operator runs the witness matrix (`SESSION_HANDOFF.md`) — the last item of which is the kill switch end to end, which no one has ever watched work.
