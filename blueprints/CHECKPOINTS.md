# CHECKPOINTS.md — Call-Dad gates

> No checklist tick without its gate passing. Evidence before status.
>
> **Every gate below is either PASSING today, PARTIAL with the remainder named, or
> explicitly VOID with a named supersession.** That property did not hold before
> 2026-10-01: G2 demanded a
> cipher round-trip and an Opus smoke test for code that was never in the build
> (no `AudioFrameCipher`, no Concentus — `AGENTS.md` "Not in the build"), and G3
> demanded a rendezvous-relay proof for a relay ADR-015 deleted. A gate nobody can
> pass is worse than no gate, because it trains a reader to ignore the ones that
> matter. Those are now marked VOID with the decision that replaced them.

## Standing gate (this lane may run it)

- **G-ALL host:** `tools/verify_project.py` + `:app:testParentDebugUnitTest :app:testChildDebugUnitTest` + `:app:lintParentDebug :app:lintChildDebug` + `node --test functions/*.test.js` + the Firestore rules emulator, via the **`gate` tool**.
  - STATUS: **GREEN 2026-10-01 against `4a5c555` (= origin/main), re-run with `--rerun-tasks`** — verify PASS (10 dirs + 102 files, including the control-byte ban); unit **518 tests / 0 failures** across both flavors; lint **0 errors** both flavors; functions **13/13**; **rules emulator 44/44**. Supersedes the G-C11 row in `CHECKLIST.md`, which was recorded against `8f47512` — a tree that did not compile.
  - **What this row does NOT prove: that the app builds.** No suite in this repo compiles the app. `8f47512` was pushed to `origin/main` with a duplicated brace and this gate printed GREEN on it. `assembleParentDebug` is the only thing that has ever caught that class, and `tools/prove_gates_bite.py` now asserts the gate goes red on one via the Kotlin compiler.
  - The `gate` tool is the sanctioned entry point (RULES §1.4a). It names a SKIPPED suite in the verdict rather than folding it into a green, and it runs the rules emulator with the Studio JBR on PATH so a security gate cannot vanish. Evidence: the verdict line plus the per-gate blocks.

## Historical phases

- **G0 scaffold:** verify_project.py exits 0 (tree + required docs + no secrets + no real-child-data strings). Evidence: pasted tail. **GREEN.**
- **G1 skeleton:** flavored unit tests PASS + flavored lint 0 errors. Evidence: the gate's own summary block. **GREEN** (superseded in practice by G-ALL).
- **G2 voice (host):** signaling state-machine tests + replay/echo guards. **GREEN as narrowed.**
  - **VOID (2026-10-01):** the cipher round-trip and Opus encode/decode clauses. There is no `AudioFrameCipher` in the build and no Concentus; WebRTC supplies DTLS-SRTP and Opus internally (ADR-005). Nothing to test, and a gate that names absent code sends a reader looking for it.
- **G2-device (human):** ring, intelligible voice ≥30s, hangup + redial on Moto G. **GREEN** — device-proven 2026-09-30.
- **G3 chat + remote:** receipt transitions proven, duplicate ingest → single row, remote reachability. **GREEN on the host half.**
  - Receipt transitions and duplicate-ingest single-row: `ChatThreadTest`.
  - **VOID:** "remote signaling via relay". The rendezvous relay is superseded by pair-scoped Firestore rooms (ADR-015) plus an ICE relay for media (K8, device-proven). The SPEC_SHEET §2.3 "remote works via rendezvous" is met by a different mechanism; the clause is retired rather than left satisfiable only by resurrecting a retired design.
  - REMAINING (human): a message survives an app restart on both phones.
- **G4 photo + video:** photo chunk→reassemble byte-identical on a host fixture + device E2E; video decision ADR. **Host half GREEN.**
  - Byte-proof: `PhotoTransferTest`, synthetic fixtures only. Verdict: a flipped byte, a missing chunk, and reordered chunks are each caught.
  - Device E2E: **REMAINING (human)** — no real photo has ever been sent.
  - Video: ADR-005 DECIDED WebRTC; both-way video device-proven 2026-09-30. **GREEN.**
  - **The donor `SovereignImageEngine` port is NOT required** — the encode/chunk/verify/assemble chain in `PhotoTransfer` + `PhotoClient` is a fresh implementation that meets the same contract, and the byte-proof is the evidence.
- **G5 hardening:** parent gate, consent enforcement, kid-UX sheet, no-escape audit. **Host half GREEN; device half is the last open v0.1 item.**
  - Parent gate: `ParentGate` + `ConsentScreenSafetyTest` (parent-only AND findable).
  - Consent: `ConsentGateTest` (expiry, revocation, scope, party, prospectivity) + `firestore.rules` consents/revocations stanzas.
  - **VOID (conditional clause):** "SQLCipher open/close (if ADR-003 yes)". ADR-003 is a deferral-with-justification and ADR-018 sharpens when it re-opens (the first LOCAL message/photo store). A conditional gate on a deferred decision is a gate that can never close; it is now a named re-open condition instead.
  - Kid-UX: `docs/kid-safe-ux.md` 12 criteria with a machine/HUMAN split; `KidUxAuditTest` + `ChatKidSafetyTest` + `PhotoSafetyTest`.
  - No-escape: no `ACTION_VIEW`/`BROWSABLE`/`SEND`/`WEB_SEARCH`/`market://` in `ui/`; system back pops to Home; the only `startActivity` is internal.
  - REMAINING (human): the whole v0.1 walkthrough on both phones.
- **G-C8 device (human, ADR-015):** ring from every screen, answer, hangup each side, no-answer, glare, mid-call kill, game sync, pairing gate. **PARTIAL** — items 1–4 and the mobile-data call are PROVEN; killed-app ring, force-stop, doze, no-answer timeout, lost-peer end and game sync mid-call remain unwitnessed. This is CURRENT_STATE K11.
- **G-C11 host (2026-10-01):** the reconnect fix. `IceRestartTest` now pins the CALLER side explicitly — the half that shipped missing — plus the round monotonicity in `firestore.rules` (4 emulator cases). **GREEN**, and the reason is written up in ADR-010 (7).
- **G-C12 host (2026-10-01):** the CONSENT ENFORCEMENT pass. `ConsentEnforcementRegressionTest` (11 tests) pins: calling gated in both directions; an incoming ring refused *before* the phone rings, with ENDED published so the caller's phone stops too; an unknown decision denied rather than permitted; revocation ending an in-progress call; the walkie talkie gated both ways including the engine's own send path; the parent's sequence read from the grants it AUTHORED, refreshed on a cert tick rather than a scope change; and **every feature's Firestore listener gated, not just its screen**. **GREEN.**
- **G-C13 gate integrity (2026-10-01):** the gates are now *proven* able to fail. `tools/prove_gates_bite.py` injects a **duplicated brace** into a `.kt` file — the exact shape of the `8f47512` bug — and asserts the gate goes red **via the Kotlin compiler**, then that the tree is restored byte-for-byte and green again. Verified: baseline PASS → `compileParentDebugKotlin FAILED` → restored PASS. (Its earlier version injected a literal control byte, which proved only that `verify_project.py` reads bytes and never exercised the compiler.) That earlier version found a real `0x1F` in a comment in `CallLogStore.kt` and a `0x00` + `0x1F` in `ChatText.kt`, invisible to grep, diff and review because git treats a file with a control byte as binary. **This gate exists because the `gate` tool once printed GREEN from a failing compile, and then did so again on a pushed commit.**
- **G-C14 (2026-10-01, post-`4a5c555`):** re-ran all five suites with `--rerun-tasks` against the fixed tree. verify PASS (10 dirs + 102 files); unit **518 tests / 0 failures** both flavors; lint **0 errors** both flavors; functions **13/13**; rules emulator **44/44**. **GREEN** — and note what it certifies: the host gates, not a build.

## Rules that gate the rules

The emulator suite is a **security** gate, and a security gate that skips must never read as green. Two clauses in `firestore.rules` were nearly unmeetable and cost real debugging time; both are recorded where they now live so they are not rediscovered:

1. A nested `match` block cannot reliably resolve a wildcard bound in an **ancestor** match block — it throws an *evaluation error* instead of denying, and an evaluation error is indistinguishable from a permission problem in a log line. Everything is one level below the room.
2. A Firestore path must have an **odd** number of segments to be a collection. A two-segment-below-room collection reference is a document, and the client SDK rejects the call outright.

Run the suite with the JVM on PATH, or it exits non-zero and looks like a pass-by-skip:
`cd tools\rules-test; $env:JAVA_HOME="C:\android\Android Studio\jbr"; $env:Path="$env:JAVA_HOME\bin;$env:Path"; npx firebase emulators:exec --only firestore --project demo-calldad "node --test"`
