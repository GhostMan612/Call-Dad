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
| 4 | `blueprints/ROADMAP.md` | Phase tracker (0–7) with gates | when planning/phases |
| 5 | `blueprints/CALL_DAD_MASTER_BLUEPRINT.md` | Frozen product spec (v0.1 target) | before novel features |
| 6 | `blueprints/ARCHITECTURE.md` | UI/comms/data split, storage flow | structural changes |
| 7 | `blueprints/blueprint-sections/BP-*.md` | Executable task slices per phase | task work |
| 8 | `blueprints/CHECKPOINTS.md` | Every gate, each PASSING or explicitly VOID | before claiming a gate |
| 9 | `blueprints/CHECKLIST.md` | Ticks, each with its evidence | session close |
| 10 | `blueprints/decisions/ADR-*.md` | Why a decision was made, and what it displaced | before reversing anything |
| — | `SPEC_SHEET.md/.json` | v0.1 scope contract (JSON carries the drift corrections) | scope questions |

Conflict law: RULES.md > other docs; executable files (`*.gradle.kts`, `AndroidManifest.xml`) > prose.

## Where we are (2026-10-01, v0.1 feature completion)

**Read this section first; the rest of the file is history.**

### State

The v0.1 contract in `SPEC_SHEET.md` §2 is **complete in source**, and Source
is at **`versionCode 13` / `0.3.3`** — the NAMES pass.

**THE FLEET WAS RE-ASSIGNED 2026-10-03.** Moto G 2025 = **PARENT**, Q8K tablet =
**CHILD** (replacing the retired BLU View 5). Both are on **vc12 / 0.3.2**:
`0.3.2-parent` on the Moto (16:39:00), `0.3.2-child` on the Q8K (16:38:44). One
flavor per device, no crossed install.

**vc13 is one commit ahead of both devices, and that gap is the point: vc12 says
"Mama is calling".** Three peer-naming defects, all found by the operator looking
at a screen with every host suite green: the child app announced "Mama is
calling"; the walkie talkie hardcoded "Dad will hear it right away" on BOTH
flavors, so the grown-up's own phone told them Dad would hear their message; and
the game's loser label was hardcoded "Dad wins!" where `role === "caller"` IS the
parent, so when the child won the PARENT's screen congratulated Dad for a game Dad
had lost. Underneath it all, the resources were named `child_peer_name` /
`parent_peer_name` with values INVERTED relative to their names, and two different
mappings existed in the tree. Fixed by naming the PERSON (`name_of_grown_up` =
Dad, `name_of_child` = Your kid) and using one uniform mapping everywhere, pinned
by `KidNamesRegressionTest` — which then found two more "phone" literals in
`ConsentScreen`, because the child device is a 600×1024 **tablet**.

Device reference for the tablet, which is **on loan and gone after 2026-10-10**:
`docs/q8k-tablet-reference.md`.

**NOT PAIRED YET.** The Q8K is a fresh install: no anonymous account, no peer
UID, so it generates a NEW uid and the pair room becomes a new
`calls/{Q8K_MOTO}`. The Moto's peer store still holds the retired BLU's uid.
**The operator must scan the QR from parent to child.** Until then nothing pairs,
and a stale BLU room will simply never connect.

Gates, re-run against the audit tree: verify PASS · unit **580 tests (290 per
flavor) / 0 failures** both flavors · lint **0 errors** both flavors ·
functions **13/13** (both files) · **rules emulator 49/49** · `prove_gates_bite`
green → red → green (it proves a non-compiling tree fails the gate, and that the
file is restored byte-for-byte) · **0 Kotlin warnings** · clean assemble
**77/77 tasks executed, 0 from cache**.

**BLOCKING, OPERATOR: `firebase deploy --only firestore:rules,functions`.** The
live ruleset cannot prove a query, so both consent listeners are denied — which
means **the app is inert on BOTH devices right now, on vc12 included, until this
deploys.** If nothing works and the shield icon leads nowhere, that is the cause,
not a bad install.

**Why the lane could not do it:** `opencode.json` denied `firebase*deploy*`. The
operator authorised a narrow allow and it was added on 2026-10-03 for exactly
this one command — but **permission sets are read at session start**, so the
running session still refuses it. A new session (or the operator running it
directly) is required. This is the third time that fact has cost a deploy; it is
recorded in `flash.md` too.

**What the gates prove: the host suites and the compiler. They prove nothing
about behaviour.** Zero features have been exercised by a human on any build
after vc7, and the parental kill switch is still unwitnessed — which is the whole
point of the next section.

Nothing in §2 has been exercised on hardware. Not one call, text, photo, consent
grant or ICE restart has run against any build. Every feature row is
**built, deployed, installed, and untested** — the gap is witness, not code.

**The build that was installed had a parental kill switch that enforced nothing.**
vc10's "Turn everything off" closed Messages and Pictures while calling, the walkie
talkie (both directions, including clips playing aloud), and photo/chat downloads
continued unaffected; and the parent's own grant sequence was unreadable on the
parent's phone, so the button could not act there at all. Calling and the walkie
talkie are the two OLDEST features in the app — they predate the consent model,
so nobody went back. Fixed in vc11, pinned by
`ConsentEnforcementRegressionTest`, and **installed on both phones as of
2026-10-02 — still unwitnessed.** No consent behaviour on a device means anything
until a human checks it.

### `origin/main` shipped a tree that did not compile — and the gate said GREEN

**`8f47512` is a broken commit that was pushed to `origin/main`.** A duplicated
`viewModelScope.launch {` in `ChatViewModel.init` made the app uncompilable, and
the full five-suite gate printed **GATES GREEN** on it. Six further tests were
failing underneath, all invisible (see `LESSONS_LEARNED.md`). Anyone who cloned
`8f47512` got an app that would not build.

**Fixed by `4a5c555`** (the brace fix; documentation alignment and this file's
own corrections followed in later commits on top of it), with all five suites re-run
`--rerun-tasks`: verify PASS, unit 520 (260 per flavor) / 0 failures, lint 0 errors,
functions 13/13, rules emulator 44/44. **Superseded as the current count by the
538 / 269-per-flavor row at the top of this file;** 520 was true at `4a5c555`.

The lesson is not "a gate lied once". It is that **no suite in this repo compiles
the app**, so "five gates green" has never meant "it builds".
`assembleParentDebug` is the only thing that has ever caught this, and it is an
`ask` task that was refused earlier in the session — which is exactly how it
survived a whole round trip. `tools/prove_gates_bite.py` now injects a duplicated
brace and asserts the gate goes red **via the Kotlin compiler**, so this class
cannot pass silently again.

### The one bug worth reading this handoff for

**ICE-restart reconnect shipped broken and was documented as working.** Not a
missing feature — a *fully implemented on one side and not the other* one:
`maybeApplyRenegotiation` opened `if (amCaller) return`, so the side that
published the restart offer never applied the answer, and the guard that should
have caught it was keyed on `seq`, which the caller had already consumed for the
original answer. Every source test and every doc claimed reconnect worked. It
could not have recovered a call, ever. Fixed with a monotonic `negotiationRound`
carried through the document and enforced in the rules; `IceRestartTest` now pins
the caller side explicitly. Written up as ADR-010 §(7).

The general form of it, because it will happen again: **"we send an offer" is
not "a reconnect".** A reconnect is offer + answer + apply-it, and only the last
clause was absent while the first two were present and reviewed.

### Second thing worth reading

**The `gate` tool reported GREEN from a tree where `compileParentDebugKotlin` was
failing.** Two independent causes, both in the tool: it had **no `rules` gate at
all** (so an entire security suite never ran, silently), and `r.out || r.err`
discarded stderr — which is where Kotlin's `e:` diagnostics live — so failures
printed with no reason in them. Both fixed: a real emulator gate with the Studio
JBR on PATH, concatenated output, a 200-line tail, and a verdict that now reads
`GATES GREEN BUT n SKIPPED` when anything skipped. **Lesson: a green gate
certifies the tree, never the behaviour of whoever is running it.**

### Next actions, in order — all operator-side

1. ~~`firebase deploy --only firestore:rules`~~ **DONE 2026-10-01.** Compiled cleanly
   and released. `chat`, `photos`, `consents`, `revocations` and `negotiationRound`
   are live. Until this ran every one of them was denied on a real device, so no
   amount of correct code could have made the new features work.
2. ~~**Flash vc10 to both phones.**~~ **DONE 2026-10-01 08:19.** `dumpsys`
   confirms Moto G 2025 (parent) vc10 / 0.3.0-parent and BLU View 5 (child)
   vc10 / 0.3.0-child, one flavor per device, installed `-r` so the anonymous
   account, peer UID, pairing and the child's consent grant all survived. **No
   re-pair needed.**
3. ~~**Flash vc11 / 0.3.1 to both phones.**~~ **DONE 2026-10-02.** Built clean
   (`77 tasks, 77 executed, 0 from cache` under `--no-build-cache`, so the Kotlin
   compiler genuinely ran) and installed `-r`: `dumpsys` confirms Moto G 2025
   (parent) vc11 / 0.3.1-parent at 10:18:11 and BLU View 5 (child) vc11 /
   0.3.1-child at 10:17:56. One flavor per device, the other flavor NOT INSTALLED
   on each. `peer_store.preferences_pb` and the Firebase auth heartbeat survived
   on both — **no re-pair needed.** The install proves compilation and packaging
   only. Everything below is unwitnessed and stays that way until a human says so:
   - **First-run consent — check this before anything else, because everything
     else will look broken without it.** A fresh install is INERT: Messages and
     Pictures both read "turned off right now", and the call is gated too. A
     parent opens the shield icon (top-right, beside the gear) → grown-ups gate →
     "Allow everything". Absence of a grant denies, by design. Do not file this as
     a bug. The grown-up's own phone is not gated — see ADR-017's `isGrantor`,
     because the rules make a self-named grant impossible and a naive gate would
     lock the parent out of the app they are configuring. **Note:** the install was
     `-r`, not a fresh install, so the child's *existing* grant should still be
     there — if Messages and Pictures are "turned off" on the child's phone after
     this, that is the grant, not a fresh-install artefact, and it is the first
     thing to check.
   - The locked-phone checks (K12 quiet PTT notification, K21 keyguard takeover).
     Fixed in vc10, never verified, open since vc9.
   - A call, end to end. The existing E2E proof is from vc7.
   - A text message both ways, including a link-shaped one (must be refused).
   - A real photo sent, verified, rendered. No photo has ever been sent.
   - The missed-call callback card.
   - The reconnect: pull Wi-Fi mid-call, or turn on airplane mode, and confirm the
     call RECOVERS rather than ending. This is the first time the fix is exercised.
    - **THE KILL SWITCH, and it is the highest-value item in this file.** On the
      parent's phone: shield icon → "Turn everything off". On the CHILD's phone,
      confirm ALL of: the Call button refuses with "Calling is turned off right
      now"; an incoming call does not ring; the walkie talkie reads "TURNED OFF"
      and the button does nothing; Messages and Pictures show the "ask a grown-up"
      notice; and **the child's phone pulls no photo chunks** — turn mobile data
      off first so any download is visible as usage. Then "Allow everything" and
      confirm everything comes back. No version of this app has ever demonstrably
      enforced a kill switch, and this is the check that would prove one.
 4. **Adopt a release keystore** (`docs/release-signing.md`) if a release artifact
    is ever wanted. `proguard-rules.pro` is a stub; no release build has ever run.

### Note for the next agent: a green gate is not evidence, and neither is a fix

Two related traps, both hit hard this session.

**Green has to have been seen red.** The `gate` tool once printed GREEN from a tree
where `compileParentDebugKotlin` was failing, because it had no rules gate at all
and discarded the Kotlin diagnostics. `tools/prove_gates_bite.py` now injects a
known defect, asserts the gate FAILS, and asserts it recovers — run it after
changing any gate. If you have never watched a check go red, you have no evidence
it can.

**The newest safety file was the least reviewable file in the repo.** A literal
`0x1F` inside a *comment* in `CallLogStore.kt`, and a `0x00` + `0x1F` in
`ChatText.kt` — the latter in the comment explaining why control characters
matter. A raw control byte makes git treat the file as binary, so grep, diff and
review all silently skip it. Every gate passed for the life of both. It was found
by writing a scanner rather than by reading, and `verify_project.py` now fails on
it. When a file stops being greppable, that is a bug in the file.

**And the general one:** three of the worst bugs found this session were features
that were complete, documented, and green — because the tests checked each half
and nothing checked that the halves were *connected*. `ChatViewModel.scopes` was
never written. The kill switch read no scope for the two oldest features in the
app. `grep` for `ConsentScope` across `app/src/main` answered that second one in
one command; it had been sitting in a finished, reviewed, green-gated feature.
When you finish a feature, grep for what it should have touched and check the list.

### Note for the next agent: a fix I write may not be loaded

Three times in this session I edited something and then found the running process
still had the old version: the `gate` tool (a stale tool implementation silently
omitted the rules suite, and printed GREEN), and `opencode.json` (the permission
set is read at session start, so a new `allow` rule does not apply until the next
session). A fix that has not been loaded yet looks *exactly* like a fix that did
not work, and the tempting response is to escalate — different flags, `--force`,
retrying the deny. Don't. Say so, and hand back the command. **Third instance of
the same shape: verify the running process is actually using the thing you
changed before concluding the change was wrong.**

### Open decisions

- **Room / at-rest crypto (ADR-003, ADR-018).** Closed as a deferral with
  justification. Re-opens the moment there is a *local* message or photo store.
- **Consent signature (ADR-017).** Deferred: a signing-key hierarchy with no
  operator custody story is a trust root, not a feature. The pair-scoped rules
  stand in for it.
- **K8 caveat.** TURN proven for a normal NAT on mobile data, not for a
  symmetric-NAT carrier. Relay creds are APK-extractable — accepted for a
  two-person family app, not for real child media.
- **K20.** Client-triggered pruning means a fully dormant pair never prunes.
  Accepted; a Firestore TTL would need a scheduled function and a billed index.

---

## Historical log (2026-09-30 and earlier)

### Where we were (2026-09-30, self-audit of the agent workflow)

- **A privacy law I wrote myself was violated in six tracked files.** `RULES.md` §1.5a
  has always forbidden putting a real device serial in a committable file. Six files
  carried both serials — and `.opencode/tools/device-evidence.ts:51-52` hardcoded them
  as an "expected mapping" table, so **the tool whose job is device evidence was the
  source of the leak.** Serials are now redacted from all six.
- **That law is now machine-enforced, because prose alone demonstrably failed.**
  `tools/verify_project.py` fails the gate on a 14+ digit serial or an
  `adb-<SERIAL>-…` form in any tracked text file, naming the match. `fixtures/` text
  must carry a `synthetic-only` sentinel so the escape hatch stays honest.
- **The gate itself now has a test.** `VerifyProjectSelfTest` asserts the device-identity
  bans exist, are wired into the scan loop (not merely declared), and report the match.
  A verification gate nobody has watched fail is a gate nobody can trust — that gap is
  how the serial survived two sessions.
- **Green gates ≠ shipped, and that cost real work.** The K12 and K21 fixes were written
  and gated green; the commit was blocked by an over-broad `write*` deny in
  `opencode.json`; I reported the block, wrote a handoff note suggesting the operator
  run it, and moved on. The work then vanished from the working tree entirely. The only
  reason anyone found out was `QuietNotificationTest` failing on the missing code.
  `RULES.md` §1.4/§1.4a now say a blocked commit is an emergency: stop and escalate in
  the same turn. The K12/K21 fixes were re-applied and committed (`86f3627`).
- **`gate-runner`'s description was a per-edit router trigger.** It said "before
  declaring any change done" in the front matter while its body correctly said "once at
  the end of a phase". Descriptions are matched on to decide whether to invoke the agent,
  so the description won. Reworded, and `ToolUseDisciplineTest` now asserts the
  description and body agree — the old body-only assertion could never catch this.
- **`opencode.json` deny list corrected.** The over-broad `write*` deny (which blocked
  legitimate git and piped output) is gone; `sed`, `tee`, `python -c`/`-m`, `node -e`,
  nested shells, `Start-Process` and `Invoke-Expression` are now denied. Structural fix
  (narrow allowlist as the default) is proposed, not done.
- **Doc drift corrected.** `docs/setup-android-studio.md` and `BP-01-skeleton.md` still
  told the operator to create the project at Kotlin 2.1.0 / AGP 8.13.2 / minSdk 30 with
  Hilt, Room, SQLCipher, OkHttp, Concentus and KSP — none of which are in the build. Now
  points at `gradle/libs.versions.toml` as the only source of truth. `GEMINI_HANDOFF.md`
  is marked superseded. `CURRENT_STATE.md`'s device table said vc5 when the phones are on
  **vc7**, and said nothing about source being at vc9.
- **`LESSONS_LEARNED.md` split.** It was mostly platform trivia filed as lessons. Now
  PART 1 is mistakes-we-actually-made (each with root cause and the check that stops it),
  PART 2 is platform reference. Added `RULES.md` §1.5c: leave the device as you found it.

## Where we are (2026-09-30, tool-use correction — RULES §1.4a added)

- **Operator correction, second one this contract.** A three-day plan was being
  dominated by shell round-trips for things `read`/`grep`/`glob`/`edit` already do,
  to the point the operator had to sit at the laptop watching it happen every 5–10
  seconds. Root cause, and it was a *documentation* defect rather than a discipline
  one: **no document in this repo said how to READ or EDIT a file.** They all listed
  commands to RUN. An agent following the docs literally had no instruction that
  shelling for a file read was wrong — and `opencode.json` had `bash: {"*": "allow"}`,
  which permitted it by default. No rule, no signal, no chance.
- **Fixed in three layers, because a rule in only one of them drifts:**
  1. `RULES.md` §1.4a — hard rule with an explicit instead-of table (cat/type/
     `Get-Content` → `read`; `Select-String`/rg/`Get-ChildItem` → `grep`/`glob`;
     `Set-Content`/`Out-File` → `edit`/`write`), mandatory batching, and "gate ONCE at
     the END of a phase, not per edit".
  2. `opencode.json` — the read/search/edit commands are now `"deny"`, not merely
     absent. The wildcard `"*": "allow"` is kept as the default but the specific
     denies are what actually stop the habit. Existing build-boundary denies verified intact.
  3. Propagation — `AGENTS.md` (tool rule now appears BEFORE the command list, which is
     the order that matters on cold start), `CLAUDE.md`, the `calldad-conventions` skill,
     `gate-runner` (told it is a phase-closing step, not a per-edit one), `/verify`,
     `/sweep`, `/flash`, `CHECKPOINTS.md`, and `doc-drift-auditor` (now audits for
     tool-use drift, so this is caught if it comes back).
- **Removed the `| Select-Object -Last 5` idiom** from `AGENTS.md` and `CHECKPOINTS.md`.
  That idiom is how the habit was taught: it tells an agent to reach for the shell to
  inspect output. Operator runbooks in `/flash` keep their PowerShell — a human runs those.
- **`ToolUseDisciplineTest`** (14 tests) pins both layers: the deny list, the presence and
  position of the law, the absence of the pipe idiom, the skill/gate-runner statements,
  and that the nine read-only auditors still hold `bash: deny`, and that every agent
  named in the docs is actually registered with `mode: subagent` (three of them were
  not, so they could not be invoked at all). A rule that is only
  written down is a rule that will drift; this makes it fail loudly instead.
- **Confirmed not the cause:** the read-only auditors already had `bash: deny`, so
  subagent fan-out was never the leak. `gate-runner` was the only fleet agent with shell
  access and is now constrained.

## Where we are (2026-09-30, Contract 11 close-out — K8/K9/K12/K16/K17/K18/K19 closed in source, K8 proven on device)

Supersedes the Contract 10 block below for current state.

- **Operator instruction was "close everything". Two of the nine could not be
  closed by writing code, and are recorded as open rather than ticked: K11 is a matrix of
  human-witnessed behaviours no host test can assert, and K8's relay could only be *proven*
  by a real call on mobile data.** Everything else is closed in source with host tests.
- **K17 — the real security find.** `pairings/{uid}` reads were `request.auth != null`, so
  any anonymous install could read any pairing doc by id and take a real UID, a real peer
  UID and a live `sessionNonce`. That nonce is the secret the mutual handshake is built on,
  so this was half of hijacking a pairing. Now owner-or-named-peer only — the single read
  the handshake actually needs. Also: a live handshake can no longer be deleted early (that
  stranded pairs between the two writes) while an expired one is clearable, so a phone that
  died mid-handshake no longer leaks it forever.
- **K18 — orphans.** `calls` delete was `if false` for every case, so after a reinstall the
  old room was written by an account that no longer existed on the phone and was deletable
  by nobody, forever. A member may now delete a TERMINAL room (ENDED/DECLINED). Live rooms
  stay undeletable by caller, callee **and** outsider — a deletable ringing room is the same
  bug class as the stale teardown this contract started on.
- **K12 — PTT now pushes.** A clip was invisible until the app happened to be open on the
  receiving phone, so "goodnight" into a locked phone looked like it had failed.
  `onPttClipWritten` sends `{type: ptt_clip, callId}` at **normal** priority: deliberately
  not a ring, because a voice message at 2am must not wake the house. No audio or content
  crosses FCM. Decision logic in `functions/clip.js` with 7 tests, mirroring `ring.js`.
- **K19 — clip cap** (operator decision, count + 24h grace). Prune only when the room holds
  >50 clips AND the oldest is >24h old, oldest-first. Played clips already delete
  immediately, so a live pair never accumulates and this never engages. Runs AFTER the send
  resolves; a prune failure never surfaces as a send failure. This is the one place the
  deliberate never-delete-a-sent-message rule is traded away, bounded on both count AND age.
- **K16 — dependency bump deployed.** firebase-functions 6.1.0 → 7.4.0, firebase-admin
  12.7.0 → 14.5.0, both MAJOR. `npm audit` 8 moderate → 2 (both in `glob`/`teeny-request`, a
  path this app never touches). Deployed clean; the CLI's "outdated firebase-functions"
  warning is gone.
- **K9 — ML Kit** recorded in RULES §1.7a as the single named exception to §1.7, with
  exactly what leaves the device (barcode model fetch + anonymous Play Services telemetry)
  and what does not (no image, camera frame, QR payload, UID, nonce, audio, video).
  Operator signed off on the reasoning: pairing must not fail for a grown-up.
- **K8 CLOSED AND DEVICE-PROVEN.** A call completed with mobile data on and Wi-Fi off.
  Open Relay is the default; `local.properties` TURN_* overrides for a private relay.
  Honest caveat, recorded in RULES §1.7a and `WebRtcConfig`: DTLS-SRTP means the relay
  carries ciphertext, but it IS a third party in the media path and the credentials are
  APK-extractable. Acceptable for a two-person family app, **not** for real child media.
  Proven for a normal NAT, not a symmetric one.
- **Backend fully current.** Rules released; `onCallRoomWritten` updated; `onPttClipWritten`
  created (v2, us-central1, nodejs22, 256MB) — confirmed with `firebase functions:list`, not
  taken from the deploy log. vc7/0.2.4 clean-built and installed to both phones
  (`dumpsys` 10:20:55 parent, 10:21:01 child, one flavor each).
- **Four failures hit in this pass and all four were mine**, recorded because the pattern
  matters more than the individual bugs: a `const val` typed `String` then used as a
  `List`; Firestore `.get()` resolving to the blocking overload so `.documents` was
  unresolved; a test that tried to CREATE an already-expired doc which the rules correctly
  refuse; and a "functions" gate that appeared to fail six tests when it had not — emulator
  output bleeding into the same stream. Each gate is now run isolated, and the parallel-PowerShell
  habit is why that last one was caught rather than reported to the operator as a failure.
- **Process note carried forward:** during the PTT verification the operator had to re-test
  twice, because I asked for logcat evidence that could not exist (the encoder drain is an
  audio-domain change; logs cannot show whether a syllable survived). Only the operator's
  ears could verify it, and they already had.

### Next actions (all human-witnessed; none can be closed by writing code)

1. **Build and flash vc9 first.** It is source-only. Two operator-reported defects are
   live on the child's phone until then: a voice message rings at full volume on a locked
   phone, and a call ring traps the grown-up on their own lock screen. Everything below
   is tested on vc9, so flashing is the prerequisite, not item one.
2. **Locked-phone check on vc9** — the one that matters:
   - send a clip with the receiving app closed and the phone locked: expect a quiet
     "A message is waiting", no ring, no vibration. On vc7 this rang loudly, because the
     notification was on the IMPORTANCE_HIGH call channel and the platform ignores
     per-notification priority from Android 8+. vc9 moves it to a dedicated IMPORTANCE_LOW
     channel.
   - have the grown-up's phone locked and ringing: the notification must show and the
     ring must be audible, but the app must **not** take over the lock screen and must not
     block unlocking.
3. Re-pair both phones under the tightened K17 read. If the client assumed any-signed-in
   could read, pairing fails — which is the correct, safe failure.
4. K11 remainder: killed-app ring, force-stop, doze, no-answer timeout, lost-peer end,
   game sync mid-call.
5. K20, accepted rather than closed: pruning is client-triggered, so a pair that never opens
   the app never prunes. A Firestore TTL would need a scheduled function and a billed index,
   and a dormant pair costs cents.

## Where we are (2026-09-30, Contract 10 — FIRST DEVICE-PROVEN CALL, both flavors installed)

This supersedes the 2026-09-24 block below. Read this first; the older blocks are
kept as dated history, not current state.

- **The "Calling Dad…" hang is dead, root cause and all.** It was never a code bug:
  the 2026-09-19 diagnosis at the bottom of this file was right that no Firestore
  database was provisioned. It was fixed as a SIDE EFFECT of the deploy — the CLI's
  `ensuring required API firestore.googleapis.com is enabled` created
  `databases/(default)`. No console action was needed. The operator did not have to
  do the thing three sessions of handoff text told him to do.
- **First proven end-to-end call** (operator-witnessed, 2026-09-30): both phones
  call and answer each other, video good, PTT works both ways.
- **Build/install evidence (`dumpsys` fingerprint):** clean
  `assembleParentDebug`+`assembleChildDebug` (33 tasks executed from scratch, not
  up-to-date reuse). Moto G 2025 = `com.calldad.parent` vc5;
  BLU View 5 = `com.calldad.child` vc5. One flavor per device.
- **All five gates GREEN**, including the two that were SKIPPED for the whole of
  Contract 9. `functions` 6/6 and rules emulator 17/17 now actually run: Node 22.23.2
  and the Android Studio JBR were already on the machine, just not on this lane's PATH.
- **Backend is LIVE:** `firestore.rules` released; `onCallRoomWritten` v2
  us-central1 nodejs22; `databases/(default)` STANDARD. `.firebaserc` added (it was
  missing, so `firebase deploy` had no target). The first deploy attempt failed on
  first-time Eventarc service-agent propagation; a plain retry succeeded. Not a bug.
- **Rules emulator logs 22 "evaluation error" lines despite 17/17 pass.** Investigated
  with throwaway probes: cosmetic. All six legitimate member operations return ALLOWED
  with no error, so no denial is masking a real allow. Recorded so the next auditor
  does not re-chase it.
- **PTT tail clipping (K14) — FIXED AND DEVICE-PROVEN, both directions.** Operator
  reported every clip cut the end of the last word on immediate release. Cause: `stopTransmitting()`
  called `MediaRecorder.stop()` the instant the finger lifted, and an AAC encoder holds a
  priming delay plus unsent frames that are discarded when the MPEG-4 container is finalised.
  Fix: keep recording a `ENCODER_DRAIN_MS` (700ms) silent tail so the encoder flushes. Shipped
  as **vc6 / 0.2.3** — deliberately NOT reusing 5, because 5 is the fingerprint that proves
  the bug. vc6 was clean-built (37 tasks executed) and installed to both phones; the operator
  re-verified **no clipping and no dropped syllables in both directions. K14 CLOSED.**
  - Worth recording: the operator had to re-test this TWICE because I asked for logcat evidence
    that could not have existed. The encoder drain is an audio-domain change; logcat can confirm
    the send and receive path is clean but has no visibility into whether a syllable survived.
    Only the operator's ears can. Trusting that result the first time would have cost one
    unnecessary test cycle.
- **Operator override on the build boundary:** the operator authorised `clean assemble*`
  and `adb install` from this lane for this session, over RULES §1.5. A deliberate,
  one-off override, recorded here so a later reader does not think §1.5 was amended.

### Next actions

1. Operator: finish the ADR-015 device matrix (K11): killed-app ring, force-stop, doze,
   no-answer timeout, lost-peer end, game sync during a call, pairing-gate bypass.
2. Operator: mobile-data call, to see how badly the missing TURN server (K8) actually bites.
3. Deliberate dependency contract: `firebase-functions@6.1.0` is behind (CLI warned),
   and 8 moderate advisories run through `firebase-admin` 12.x → deprecated `uuid@9/10`.
   `functions/package-lock.json` now pins them, so they are reproducible rather than
   drifting. Bump as its own commit with its own gate run.

## Where we are (2026-09-24, full-repo sweep + fix — ADR-015, executor lane, cloud session)

- **Sweep:** 4 parallel read-only reviews (call core, security/pairing, tests/features, docs drift). Headline findings re-verified against source by the executor before any fix.
- **Root causes fixed:**
  - Hangup SIGSEGV: the factory was disposed under a sink-attached remote track. Fixed by the teardown order pc → tracks → factory.
  - Try Again: it re-used a disposed WebRTCClient. Now a single-use client per attempt.
  - No ringtone: the ringer was never instantiated.
  - Games dead and rings missed off Home: `callViewModel()` was entry-scoped. It is now activity-scoped.
  - Stuck ringing: no-answer never wrote ENDED.
  - Black video: ICE candidates were lost on both sides. Now queued and buffered.
  - Foreground service: it read the network before calling `startForeground`.
- **Security fixed:**
  - `family_channel` was world-readable, squattable and bricked after a reinstall. Replaced by pair-scoped rooms whose rules authorize from the id; 15 emulator tests.
  - Rings went to a topic anyone could subscribe to. Now sent to the callee's token.
  - Pairing could silently replace Dad. Now behind a grown-ups gate, and the peer is stored only after a mutual handshake.
  - The BLU serial in this file is redacted.
- **Features the kid will notice:**
  - Ringtone plus caller ringback.
  - Rings from any screen.
  - Mute, flip camera, and a game door during a call.
  - Games work solo too.
  - "Missed call" / "Connection lost" labels.
  - A screen for when permissions are denied.
- **Gates (this lane):** 80 host tests PASS; lint 0 errors both flavors; Kotlin 0 warnings; functions 6/6; rules 15/15; verify PASS. Nothing built/installed; no device claims.
- **Doc alignment:**
  - Corrected: RULES §1.5 gate names (flavored tasks; the old names never existed) and §3 pin pointer, plus a new §1.7a recording the shipped architecture for operator ratification.
  - Rewritten: AGENTS.md and CURRENT_STATE.
  - Added: CHECKLIST Contract 8, the G-C8 checkpoints and ADR-015.

## Where we are (2026-09-22, night-firefight bundle pushed as b5c98a6 — first E2E call GREEN)

- **Breakthrough:** Moto→BLU signaling completed live (OFFER published → ANSWER published → ICE trickle flowing). Per-call rules rewrite (auth-only get + party updates) unblocked publishes; stale seq-1 room (dead UIDs) deleted.
- **Fixed in bundle:** Home incoming listener restored (callee was deaf), try/catch on all call launches (silent deaths → Error cards), ICE trickle both ways (media could never connect), setup timeouts (5s store/10s offer), instruments (publish/answer/ring/failure-kind logs), incoming caller-label ("Mama"/"Dad" inverted), stale-track observer crash guard (hangup FATAL).
- **Still open (hardening contract):** intermittent hangup crash (no post-fix crash in buffers — needs one watched hangup test to confirm), intermittent no-ring (FCM/killed-app path never tested; doze unknown), stuck-calling (NoAnswer path vs ICE/timeout tuning), retired tests (CallStateTest/CallDocumentTest target dead models), deprecation warnings (onNewToken, FCM token), TURN/mobile-data, real-tablet coverage.
- **Process debt:** CHECKLIST.md / CURRENT_STATE.md refresh + ADR-014 (datastore pairing) still owed; line-ending noise files remain uncommitted (SPEC_SHEET.json, README, Routes, GiantComponents, Type, RoutesTest).

## Where we are (2026-09-19, hangup SIGSEGV root-caused — fix committed, needs rebuild)

- **Tombstone proof (BLU, every hangup):** `VideoTrack.removeSink` → libjingle SIGSEGV from `VideoRenderer onDispose`. Race: endCall() disposed native tracks while composables still held sinks; navigation-pop disposal then touched freed memory. Fix: endCall no longer disposes — disposal only in onCleared (composition gone first). No FATALs since fix exists yet — rebuild + hangup test PENDING.
- **Lock-screen behavior (accepted, Phase 5 polish):** connection persists, camera pauses on lock, resumes after unlock. No action now.
- **Audio:** operator-confirmed both ways. Quality: near-zero lag, not choppy. Phase 4 NOT closed until hangup-clean rebuild passes.

## Where we are (2026-09-19, FULL E2E VIDEO CALL GREEN — both phones, lane-witnessed)

- **The call (21:40):** BLU caller → Moto QA-Answer → OFFER/ANSWER exchanged clean → **PeerConnectionState + ICE CONNECTED on BOTH**. No clobbering, no UNKNOWN, no crash.
- **Lane-witnessed screenshots (in `C:\venv-hub\call-dad\`, NEVER repo):** BLU shows Moto's feed full-screen + own PiP + 00:25 timer; Moto shows own PiP + BLU feed (aimed at ceiling) + 00:55 timer. Video flows both ways; controls correct on both.
- **Earlier FAILED session explained:** simultaneous callers clobbered the single room (stale SDP pair) — choreography + wipe/poll fixes resolved it. Single-room clobbering still must go before real use (Phase 5 per-call rooms).
- **Still unverified:** AUDIO both ways (operator ear-check needed); TURN/symmetric-NAT (K8); Firestore rules still dev-open; no call-history/ringtone.

## Where we are (2026-09-19, stale-offer immunity — executor lane, since committed)

- **Lane read the live room (REST):** it holds an OFFER right now — Moto's "still ringing" is a genuine live offer (or an abandoned one; indistinguishable without timestamps — hence this fix).
- **Fix:** offers carry `createdAt`; listener + fetch ignore anything >60s old or unstamped. Abandoned rings now die on their own instead of haunting the phones. Per-call rooms still the real answer (Phase 5).
- **Retest choreography (strict one-caller-at-a-time):** hang up BOTH phones first (clears room) → BLU calls and waits → Moto answers within a minute. Simultaneous calling still clobbers — don't.

## Where we are (2026-09-20, Phase 9 landed — executor lane, since committed)

- **Architect prompt executed with 6 recorded deviations (ADR-011):** TURN_URLS multi-URL + alias, audio-mode set/reset, single-ringer doctrine (overlay player removed), answer-stops-ring, shared Helper VM (same crash class as 3/6), keyword order fix. KeywordBot host-tested (6 tests).
- **Needs operator:** Studio sync (no new deps) → provision TURN_URLS or leave empty → §K matrix: ringtone loops post-call, vibration repeats, keyword jokes, airplane-mode STT (API 31+), multi-game sync, guardrail audit.

## Where we are (2026-09-20, Phase 10 landed — BLOCKED on console step, since committed)

- **Architect prompt executed (ADR-012):** parent/child flavors (blue/pink, APP_THEME-gated, dynamicColor never on), feature colors preserved, child keeps "Call of Daddy", full 3-game hub with c4 bounds guard, camera toggle verified present.
- **BLOCKER before ANY Phase 10 verification:** suffixed IDs match no Firebase client → register `com.calldad.parent` + `com.calldad.child` in console, replace gitignored `google-services.json` with merged download. Builds fail until then (not a code bug).
- **Also recorded:** Phase 9's game-hub replacement never landed (executor miss, superseded — no recovery needed).

## Where we are (2026-09-20, Phase 11 landed — executor lane, since committed)

- **Architect prompt executed with 6 recorded deviations (ADR-013):** static room + seq + status machine + structured rules + topic FCM (no callId) + FGS foreground-first. REJECTED twice, loudly: flavor role gates (would brick both directions — no differentiated UI exists) and child-only subscription (product direction is child→parent). Callee observes docs (prompt left it blind). Stale-snapshot guard restored.
- **Needs operator:** Studio sync (no new deps) → `firebase deploy --only firestore:rules,functions` (rules REPLACED — old per-call paths deny by default) → §G matrix: rules proofs, 3× calls with seq check, restart recovery, clean logcat. NOTE: old `ring/dad` doc and per-call rooms orphaned in Firestore (dead data, nobody reads them).

## Where we are (2026-09-20, Phase 8 landed — executor lane, since committed)

- **Architect prompt executed with 6 recorded deviations (ADR-010):** debounce machine + `restartIce` (IceRestart constraint), `updateOffer` + callee offer-watcher (prompt's Phase 2 API is gone), role derived from state, auto-reconnect trigger + banner (prompt expects the logs, never wires the cause), callee `listenCall` (was blind post-answer), game.html + PiP card + media-overlay fix.
- **Needs operator:** Studio sync (no new deps) → build → §H matrix: two-device game both directions, simultaneous-tap race, PiP-over-WebView, Wi-Fi toggle recovery, logcat guardrail audit.

## Where we are (2026-09-20, Phase 7 landed — executor lane, since committed)

- **Architect prompt executed with 5 recorded deviations (ADR-009):** no-override fix (wouldn't compile), shared activity-scoped CallViewModel for the bridge, WebViewAssetLoader hardening + nav-lock, 1KB cap + buffer-copy, game.html TAP shell, `webrtcClientOrNull()` accessor. No new host tests possible (native + JS engines) — device matrix per prompt §G is the gate.
- **Needs operator:** Studio sync (androidx.webkit) → build → TWO-device game sync (TAP → "Game state TX" + remote title "Remote taps: N") → quote/backslash escaping test → logcat guardrail audit (no JSON/SDP/ICE).

## Where we are (2026-09-19, Phase 6 landed — executor lane, since committed)

- **Architect prompt executed with 5 recorded deviations (ADR-008):** ptt/ package (interface, simulated default, reflective adapter, focus+haptics, VM+factory+shared accessor), PttScreen replacement (tryAwaitRelease, 3 color states), CallScreen interlock, manifest mic-audio perms. Proprietary boundary holds: zero com.sovereign imports (reflection only), no Gradle dep, simulated default.
- **Prompt bugs fixed:** shared activity-scoped PTT VM (prompt's sharing claim was wrong twice — crash + silent non-sharing); no fake receiving pulse (template confirms Idle-forever accepted); host-test infra (returnDefaultValues + coroutines-test) + 4 engine tests.
- **Needs operator:** Studio sync (no new deps — pure code) → §K matrix (press/release logcat, drag-off release, in-call interlock, boundary grep).

## Where we are (2026-09-20, Phase 5 console triage — rules live, auth failing on BLU)

- **Lane-proven:** new strict rules ARE deployed (`ring/dad` listen → PERMISSION_DENIED for unauthenticated — correct). But BLU logs `Anonymous auth: FAILED`, so every Firestore call is denied and nothing works. Moto side unknown (wireless adb timed out from lane).
- **Two suspects, operator checks in order:** (1) Anonymous provider not enabled in console (Auth → Sign-in method) — most likely; (2) BLU Play Services broken (GMS broker SecurityException + Phenotype errors in same window) → update Play Services, reboot.
- **Needed back:** console Anonymous status; BLU retest (`Anonymous auth: signed in`?); Moto auth line (run locally — lane wireless timed out).

## Where we are (2026-09-20, both phones authed — Phase 5 call unblocked)

- **Moto auth:** `already signed in` (fresh-buffer proof). Both phones authenticated; strict rules passable from both sides.
- **Next:** CALLEE_UID swap-build confirm → first per-call-room call (green card → auto-popup → CONNECTED) → §L remainder (killed-app, rules proofs, TURN check).

## Where we are (2026-09-20, PTT durable on Moto — 8+ clean cycles, auth TBD)

- **Moto (PID 28103, one long session):** SIX more press/release cycles, all textbook (granted→fallback→started→stopped→abandoned), zero errors, zero FATALs. Fallback line absent after the first swap (persistent engine instance — correct). Interlock/drag-off still untested.
- **Moto auth:** still no `Anonymous auth` line in any pasted window (buffer reaches 09-19, so absence is notable but not conclusive — line may predate rotation). One direct grep needed: `Select-String "Anonymous auth"` alone.
- **Still open:** Moto auth confirm; CALLEE_UID swap-build status; first Phase 5 addressed call; full §L matrix.

## Where we are (2026-09-20, auth healed + PTT press/release GREEN on device)

- **Auth:** `Anonymous auth: signed in` + `already signed in` on device 1 (console toggle or Play Services healed — operator-side fix worked). Moto auth line not yet seen.
- **PTT §K.2 (Moto, 2 cycles):** focus granted → fallback to simulated → TX started → TX stopped → focus abandoned, twice, zero errors. Proprietary boundary holds at runtime too (no Mantle). Remaining: drag-off release, in-call interlock, camera toggle already seen working.
- **No crashes** anywhere in either dump (only historical 09-19 tombstones).
- **Still open:** Moto auth line; CALLEE_UID swap-build status; any actual Phase 5 call (no OFFER/ANSWER in these windows); full §L matrix (killed-app, rules proofs, TURN check).

## Where we are (2026-09-19, Phase 5 landed — executor lane, since committed)

- **Architect prompt executed with 7 recorded deviations (ADR-007):** no-KTX, BOM-managed versions, ring-pointer bridge (`ring/dad`, presence-only, strict-ish rules) so app-to-app stays testable pre-Phase-6, CALLEE_UID per-phone provisioning, POST_NOTIFICATIONS runtime ask, status-machine VM (45s ring timeout, 15s media watchdog, cancel-safe), FCM armed-but-untargeted (no tokens until Phase 6).
- **New files:** CallDadApplication, CallDocument, OwnCallRegistry (replaces OwnOfferRegistry), fcm/×2, ic_call, functions/×3, firebase.json, firestore.rules. Deleted: OwnOfferRegistry.kt.
- **NOT yet proven:** everything needs a Studio sync (new deps: auth/messaging/play-services-auth) + `firebase deploy --only functions,firestore:rules` + CALLEE_UID provisioning + killed-app test. Handoff §M boxes filled from operator pastes only — nothing fabricated.
- **Operator console steps (in order):** enable Anonymous sign-in (Auth → Sign-in method) → `firebase deploy --only functions,firestore:rules` → read both phones' uids (Auth → Users) → CALLEE_UID swap-builds → test matrix in prompt §L.

## Where we are (2026-09-20, rules mismatch proven — server runs stale rules)

- **Operator pasted deployed rules:** old dev-open `calls/dad_channel` only. My `firestore.rules` (per-call rooms + ring bridge) was NEVER deployed → every Phase 5 write default-denies (`OFFER publish started` → 0.4s → PERMISSION_DENIED, both phones). All prior theories (crossed/stale UIDs) were wrong; apologize for the runaround.
- **Fix (no rebuild):** console → Firestore → Rules → replace ALL text with `C:\Call-Dad\firestore.rules` content → Publish. Retest call immediately.
- **Process correction (operator's call, accepted):** stop whack-a-mole from the lane; route open research through Gemini/DeepSeek, lane verifies against device evidence. Verification-first questions before theories.

## Where we are (2026-09-20, rules live — writes pass, popup path unproven)

- **Operator runs (both phones, caller-only):** `Call room created` + `OFFER published` + clean `ENDED` teardown on both — Phase 5 write path FULLY GREEN under strict rules. No ANSWER/popup lines anywhere: either choreography (callee never parked on Home) or the ring pointer never lands/listens.
- **Executor closed the evidence gap:** ring-write failures now log (`Ring pointer write failed`); ring receipt logs (`Ring observed`). Both literals, guardrail-clean. If the next test shows publish WITHOUT observed on the parked phone, the fault is isolated to ring-write/rules; if observed WITHOUT popup, it's navigation.
- **Retest (strict):** rebuild both → park Moto on the 4-card screen untouched → BLU calls once, waits → expect `Ring observed` on Moto + auto-popup.

## Where we are (2026-09-20, back-button ghost path closed — executor lane, since committed)

- **Lane read of both tails:** rings observed BOTH ways (`Ring observed` BLU ×2, Moto ×1), publishes clean, teardowns clean — popup path fully proven. But zero Answer taps anywhere: every overlay exit was silent (no DECLINED line) → system back button escaping without room cleanup → ghost rooms + the lingering both-caller chaos. Fix: BackHandler = Hang Up (Decline on overlay), same awaited path.
- **Retest that matters:** rebuild both → BLU calls → Moto overlay → tap ANSWER (green, the one untested button) → expect CONNECTED both sides.

## Where we are (2026-09-20, PHASE 5 CALL GREEN — video both ends, pipeline takes over)

- **Operator report:** connects, video both directions. The candidate-flush fix closed it. No more midnight hunting — open research goes to Gemini per operator order.
- **CRITICAL device intel (new):** BLU is a TEST MULE only. The daughter's real device is a tablet at home, currently inaccessible. Implications: minSdk 26 (ADR-001-B) must hold; tablet model + Android version + camera/mic behavior NEEDED before sign-off (operator to supply); emulator + BLU coverage does not equal tablet coverage.
- **Still unproven (Gemini/Phase 6 territory):** killed-app FCM wakeup, TURN/mobile-data, token plumbing, rules proofs (§L), call history, tablet run.

## Where we are (2026-09-20, no-remote-video root-caused — executor lane, since committed)

- **Operator report:** call connects UI-wise, local PiP only, drops ~14s (watchdog firing = peer never CONNECTED). Root cause: ICE gathering starts at createPeerConnection but the room only exists after the Firestore round-trip — early (host, LAN-critical) candidates were silently dropped while `currentCallId == null`. Fix: stash + flush on room creation; reset clears stash.
- **Retest:** rebuild both → call → expect CONNECTED + remote video (not just PiP), no 15s drop.

## Where we are (2026-09-19, stuck-overlay validated out — executor lane, since committed)

- **Operator report:** Incoming overlay hangs until another call+hangup cycle; both phones on v3 (lane-verified dumpsys) so NOT a stale build. Root cause: overlay opens on a possibly-stale snapshot and only watches for FUTURE deletions — an already-gone room strands it (nothing will ever fire). Fix: `watchIncomingRoom` validates entry (fetchOffer answerable? else bounce home at once) then watches. Covers stale-snapshot, own-ringback, and pre-hangup races.
- **Retest:** rebuild both → BLU calls, hangs up BEFORE Moto answers → Moto's overlay should vanish by itself (or never wrongly appear).

## Where we are (2026-09-19, self-ring + ghost-InCall — executor lane, since committed)

- **Operator bugs (both real, shared root):** (1) double-call + hangup → phone rings ITSELF (own OFFER heard by own Home listener); (2) answering own stale offer → InCall showing local video with no peer ("video without connecting" = local PiP renders immediately, remote black — by design). Fix: `OwnOfferRegistry` suppresses self-offers in listener + fetch; versionCode 3 fingerprints builds (dumpsys-checkable from lane, ends "which build is installed" confusion).
- **Open question for retest:** whether the ghost-InCall persisted past 15s (watchdog build installed?) — new build settles it either way.

## Where we are (2026-09-19, zombie-call guards — executor lane, since committed)

- **Operator bug (confirmed design gap):** caller quick-hangups → callee answers a deleted room → sits InCall with a ghost forever. Guards: (1) 15s media watchdog on every InCall entry (no CONNECTED → silent Idle → auto-home, no scary card); (2) incoming overlay watches the room — vanishes pre-Answer → home. Peer-connected flag resets per call.
- **Test:** BLU calls → hang up within 2s → Moto answers (or sits on overlay) → Moto should be home within ~15s, noRetry card, no crash. Then normal call to confirm the watchdog doesn't bite healthy calls.

## Where we are (2026-09-19, one-sided hangup root-caused — fix committed, needs rebuild)

- **Operator report:** hanging up one side strands the other (must hang up both). Root cause: `endCall` fired teardown into `viewModelScope` then navigated instantly — the pop clears the VM, cancels the scope, and the room delete usually dies with it, so the peer's room-deleted listener never fires. Fix: `endCallAndAwait()` (3s cap, offline-safe) awaited BEFORE navigation on local hangup/decline; fire-and-forget `endCall()` kept for the remote-triggered path.
- **Test:** rebuild both → call → hang up ONE side → other should glide home ~1s later.

## Where we are (2026-09-19, remote hangup + QA retired — executor lane, since committed)

- **Operator asked, executor built:** peer hangup/decline now mirrors home on both sides (`observeRoomDeleted` → `endCall`, auto-home on Idle-after-activity). Grey QA button REMOVED (auto-popup proven; route + `simulateIncomingCall` kept as Phase 5 FCM entry).
- **Real-phone question answered:** yes — background/killed-app incoming is exactly Phase 5 (FCM wakeup). App-open popup is done; nothing more can ring a dead app without push.

## Where we are (2026-09-19, auto-popup incoming call — executor lane, since committed)

- **Operator asked, executor built:** Home listens for new OFFERs and jumps to the overlay itself (ringtone + buzz, silenced on leave). Grey QA button stays as fallback. Killed-app wakeup = Phase 5 FCM. Rebuild + test: BLU calls while Moto sits on the 4-card screen → overlay should pop WITH sound, no taps on Moto.
- **Answer to the question:** yes, it should pop up — the grey button was scaffolding that overstayed. This fixes the app-open case now; FCM fixes the killed-app case later.

## Where we are (2026-09-19, OPERATOR VINDICATED — grid squeezed to zero by my QA card)

- **Device screenshot proved it:** Home shows greeting + one full-screen grey QA card, zero grid. Cause: `GiantActionCard`'s inner `fillMaxSize` Column is safe only inside weighted rows; my unweighted QA card claimed the whole Column and squeezed both grid rows to zero height. Fix: fixed `.height(140.dp)` on the QA card (+ missing `height` import).
- **Lesson recorded:** never trust "works" without a screenshot; the operator's report was precise and I argued instead of looking. Look first from now on.

## ARCHIVE — superseded, kept for history

Everything below this line predates the current state. Do not act on it. Current
state is `blueprints/CURRENT_STATE.md`; the short version is at the top of this file.

---

## Where we are (2026-09-19, operator UX confusion — "no home", grid unseen)

- **Operator report:** only ever sees grey QA card → overlay → Answer → 15s → NOT_FOUND error → Retry ("Ready") / Hang-up (back). Never mentions the 4 colored Home cards — UNCONFIRMED whether the 2x2 grid renders on their build. "Home" jargon retired; describing screens by visible text from now on.
- **Lane observation (screenshots):** BLU showed only the pulled-down Quick Settings shade (airplane mode ON, WiFi on Dayton House — network OK); then BLU left USB. Moto G present on wireless adb (mdns transport), screen ASLEEP (black capture). App Home screen never visually confirmed.
- **Standing question for operator:** on the app's first screen, are there 4 colored cards above the grey one? If no green "Call Dad" card is visible, that's a layout bug on the executor — say so and it gets fixed, no choreography will work until it exists.
- **Choreography (once green card confirmed):** phone A taps GREEN card and waits; phone B taps GREY card → Answer within ~15s.

- **Operator run (both phones, QA overlay):** `Call failed: NOT_FOUND` on both = CORRECT behavior — QA overlay is Firestore-blind; both sides opened it with no live OFFER in the room (caller must ring FIRST and stay on-screen; any hangup/decline teardown-deletes the room). Not a bug.
- **Executor robustness fixes (committed next):** (1) caller wipes the room before publishing (stale ANSWER/candidates from prior QA runs or crashes no longer poison new calls — callee side never wipes); (2) callee polls `fetchOffer` ~15s on NOT_FOUND only (absorbs two-human tap timing; other failures still throw immediately).
- **Correct choreography:** BLU taps Call Dad and WAITS on "Calling Dad…" → Moto taps gray QA button → overlay → Answer within ~15s → ANSWER published → both CONNECTED. Decline/hangup either side kills the room — start over if Retry appears.

## Where we are (2026-09-19, Phase 4 landed — executor lane, since committed)

- **Architect prompt executed (1 created, 5 modified, package `com.calldad`):** `CallState.Incoming`, `VideoRenderer` (composable-owned init/release, client EGL only, null = black placeholder), `WebRtcConfig.iceServers` (STUN + REPLACE_ME-gated TURN), WebRTCClient (ctor iceServers, `eglContext`/`localVideoTrack` accessors, `buildRtcConfig()`, attach*/detach* + fields DELETED), VM (EGL/track flows, `simulateIncomingCall()`, cancel-safe), CallScreen (Incoming overlay + Answer/No ≥160dp, InCall video Box with remote/PiP/timer/controls, `mode` param), nav-arg `call?mode={mode}`, DEBUG-only Home QA button (`buildConfig=true`), `CallStateTest`.
- **Executor scope call (ADR-006):** QA hook drives the REAL `answerCall()` (not a fake overlay) so two-device E2E is actually testable; production route untouched; decline-wipes-room accepted; per-call rooms → Phase 5.
- **Gates:** verify re-run next. Operator: rebuild, install both phones, BLU calls → Moto opens QA button → Answer → expect ANSWER published → both CONNECTED with video. Watch for EGL/black-screen issues (report exactly).

## Where we are (2026-09-19, two-device run: BOTH sides called, nobody answered — by design)

- **Operator run (Moto + BLU):** both logs show caller leg only (`OFFER published` in <2s both — Firestore healthy), then hangup. No ANSWER anywhere: no incoming-call UI exists yet (carried Phase 4 gap), so `answerCall()` was never invoked on either side. Nothing failed; the callee path is simply unreachable from the UI.
- **OFFER-overwrites-OFFER note:** single hardcoded room `dad_channel` + merge writes mean the second caller's OFFER clobbers the first — fine for one-channel testing, must go before multi-call (Phase 4: per-call rooms).
- **Decision needed (operator):** (A) executor builds minimal incoming-call overlay now (OFFER listener → Answer/Decline → answerCall()); (B) aiortc desktop-callee experiment to prove media without app changes; (C) wait for Gemini Phase 4 instructions (already requested).

## Where we are (2026-09-19, Phase 3 caller leg GREEN on device — both fixes proven)

- **Operator run (BLU, new build PID 17206, two sessions):** `OFFER publish started` → **`OFFER published`** (~3s, Firestore enabled) in BOTH sessions. No `Call failed: UNKNOWN`. No crash.
- **Executor lane check:** zero `FATAL/AndroidRuntime` for PID 17206 across two hangups; last crash remains 20:17 PID 16209 (old build). Double-dispose fix + cancel-rethrow both device-proven.
- **G3-device status:** caller leg GREEN (publish + clean teardown). Callee leg (ANSWER/CONNECTED) BLOCKED on second device — Moto G gaming. Renderers + TURN + incoming-call UI remain Phase 4.

## Where we are (2026-09-19, ROOT CAUSES PROVEN from device — both fixed in code / console)

- **Hang cause (Firestore, device-proven):** logcat shows `PERMISSION_DENIED: Cloud Firestore API has not been used in project calldad-508d7 before or it is disabled` — project exists and json matches, but **no Firestore database provisioned**. Writes pend in offline mode forever → "Calling Dad…" hang. Fix = console-side (operator): enable Firestore + create database + dev rules below. NOT a code bug.
- **Crash cause (double dispose, device-proven ×3):** `WebRTCClient.dispose:278` (`eglBase.release()`) from `onCleared` — endCall() disposes, hangup pops nav → VM cleared → onCleared disposes again → throw. Fixed: idempotent `disposed` guard. Plus `CancellationException` rethrow in startCall/answerCall (the stray `Call failed: UNKNOWN` was scope-cancel at clear, not an error).
- **Fix commit pending:** dispose guard + cancel-rethrow above. Rebuild + retest after console step.

## Where we are (2026-09-19, first device run AUDITED — Firestore write never completed)

- **Operator run (BLU, single device):** Home → Call Dad; logcat showed Factory→PC→capture→HAVE_LOCAL_OFFER→Local OFFER→GATHERING, then 26s silence, then user hangup (CLOSED cascade + dispose). **`OFFER published` NEVER appeared → per prompt §J, fault is in SignalingClient/Firestore, not WebRTC.**
- **Executor probes (authorized, read-only adb):** BLU online (firestore.googleapis.com ping 0% loss); app installed, no crash, no Firebase exceptions in buffer (buffer rotated; `-s WebRTC:D` filter would have hidden non-WebRTC errors anyway).
- **Ranked hypotheses:** (1) write HUNG (offline at 19:55? wrong-project json?) vs (2) failed fast into Error state with ZERO logging (observability gap — now fixed: `OFFER/ANSWER publish started` markers + `Call failed: <KIND>` in reportError, guardrail-compliant). UI state during the 26s UNKNOWN — operator to confirm (Calling vs Retry card).
- **Single-device ceiling:** no callee exists (Moto G gaming) → ANSWER/CONNECTED impossible regardless; Firestore rules/DB provisioning still unverified.
- **Fix committed next (pending):** observability markers above. Re-test needs UNFILTERED logcat.

## Where we are (2026-09-19, host gates GREEN — operator run, executor recorded)

- **Evidence (operator pasted):** `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` → **BUILD SUCCESSFUL in 2m17s, 33 tasks (31 executed, 2 cached)**. 8/8 host tests pass (Routes 3 + SignalingModels 5); lint clean apart from K2 Kotlin-analysis-API warnings (toolchain noise, pre-existing). Wrapper generation itself also BUILD SUCCESSFUL.
- **Device seen from executor lane (read-only adb):** `<serial redacted> device` = **BLU View 5 (B160V, sdk 34)** — not the Moto G. Moto G remains truth device for sign-off.
- **Gates flipped:** G1, G2-host, G3-host GREEN. Still pending: `:app:assembleDebug` + install + `WebRTC:D` call sequence on device.
- **Answer to operator's question (standing orders):** read-only adb from this lane is YES and already proven above. Installs / `connected*` / instrumented runs stay behind an explicit per-order authorization per RULES §1.5 — and there is no `androidTest` source set in repo yet, so the only device work available is the manual `/smoke` walkthrough (operator taps, pastes observations).

## Where we are (2026-09-19, Phase 3 peer connection landed — executor lane, since committed)

- **Architect prompt executed (4 created, 5 modified, package `com.calldad`):** `webrtc/WebRtcConfig.kt` (Google STUN ×2, 640×480@24), `WebRtcLog.kt` guardrail (fixed-string/enum logging only — KDoc is a standing RULES-§2 exception per ADR-005), `WebRTCClient.kt` (trickle ICE, GATHER_CONTINUALLY, audio+front-camera tracks, Phase 4 renderer hooks), `ui/permissions/CallPermissions.kt`; catalog `webrtc 1.1.0`, module dep, Manifest mic/camera + `required=false` features.
- **Executor fixes (soundness):** (1) prompt's `CallViewModel(application)` + bare `viewModel()` would CRASH on navigation — added `callViewModel()` factory; (2) package rewritten from `com.calldad.app.*`; (3) H.2 Connecting branch verified pre-existing — no-op; no renderers added.
- **Architect's own flag confirmed fixed:** Phase 2 callee re-apply-OFFER bug gone (observation caller-scoped). STUN-only carried as K8 (Phase 4 TURN blocker).
- **Gates:** verify re-run next. Operator runs `:app:assembleDebug` + `testDebugUnitTest`/`lintDebug` + `adb logcat -s WebRTC:D` (expected state sequence in prompt §J; NEVER paste SDP/ICE payloads). `google-services.json` confirmed present on disk (gitignored).

## Where we are (2026-09-19, pipeline applied collaborator box — executor reconciled, since committed)

- **Operator applied collaborator catalog verbatim:** `libs.versions.toml` now camelCase single-source-of-truth (AGP 8.7.2 / Kotlin 2.0.21 / google-services 4.5.0 / BOM 34.19.0 / non-KTX firestore); root `build.gradle.kts` pure-alias; `app/build.gradle.kts` verbatim §3 with `com.calldad` correctly kept.
- **Executor reconciliations:** KTX fix applied (`getInstance()`, 4 dead imports removed incl. `FieldValue`/`QuerySnapshot`); junit restored (gates); versionCode held at 2 (avoids device downgrade-install failure); ADR-004 records the toolchain switch (operator-decided, DeepSeek retro-review invited); freeze + checklist updated.
- **Open risk:** `app/build/` was generated under AGP 8.13.2 — Studio must clean re-sync under 8.7.2; BOM 34.19.0 proven only by sync. Human pastes sync result.
- **Gates:** verify re-run next. Temp `Log.d` + `assembleDebug` from the box are OPERATOR-LOCAL ONLY (never committed by this lane).

## Where we are (2026-09-19, Phase 2 signaling landed — executor lane, since committed)

- **Operator Phase 2 WRITTEN under `com.calldad`:** `data/signaling/SignalingModels.kt` + `SignalingClient.kt` (Firestore `calls/dad_channel` OFFER/ANSWER + ICE trickle, `SignalingFailure` offline mapping), `ui/screens/CallState.kt` (Idle/Connecting/InCall/Error replaces `CallStatus` enum), `CallViewModel` rewire (startCall/answerCall/endCall + remoteDescription/remoteCandidates hand-off for Phase 3), `CallScreen` rewire (layout preserved + Error/Retry card), `SignalingModelsTest` (5 pure-JVM tests).
- **Build deltas:** Firebase BOM 33.5.1 + google-services 4.4.2 + coroutines-play-services; Manifest INTERNET + ACCESS_NETWORK_STATE (RECORD_AUDIO/CAMERA still commented); versionName 0.2.0. **Kept frozen AGP 8.13.2 / Kotlin 2.1.0 — the draft's 8.7.2/2.0.21 downgrade was rejected** (no ADR authorizes it; DeepSeek to confirm). Unused `FieldValue`/`QuerySnapshot` imports dropped for lint.
- **ADR-002 now DECIDED Firebase-for-signaling** (operator directive overrides P2P-first recommendation); sovereign P2P deferred to BP-04. `google-services.json` stays gitignored/verify-banned — operator must place it in `app/` before any device signaling test.
- **Gates:** verify re-run next. Human Studio run needed: `testDebugUnitTest` (Routes + SignalingModels) + `lintDebug` + `google-services.json` placement + device signaling proof. This lane ran no Gradle.

## Where we are (2026-09-19, Phase 1 scaffold landed — executor lane, since committed)

- **Operator Phase 1 scaffold WRITTEN to `app/`:** `com.calldad`, 15 `.kt` (MainActivity, Routes/AppNavHost, Color/Type/Theme, GiantComponents, Home+VM, Call+VM, Ptt+VM, Game, Helper+VM) with Genesis headers prepended per RULES §2, Manifest (portrait, no perms — Phase 2 uncomment block kept), `themes.xml`/`colors.xml` (Manifest `@style/Theme.CallDad` satisfied), `RoutesTest` (pure-JVM), module + root Gradle + `libs.versions.toml` + wrapper props (no `gradlew` binaries — Studio generates on sync).
- **Deltas vs frozen spec (executable truth wins, DeepSeek/Gemini to rule):** package `com.calldad` (was `com.calldad` — SPEC amended); routes Home/Call/**Ptt/Game/Helper** (was Chat/Photo/Log — deferred to BP-03/04); minSdk **26** (ADR-001 DECIDED B); BOM **2024.10.01** (drift from frozen 2024.12.01 — flagged); no Hilt/Room/Hilt yet (BP-02+); placeholders: 1.5s fake connect, `cannedReply()`, PTT mic hooks, WebView hook.
- **Gates:** G0 GREEN (re-run next). G1 PENDING human Studio run (`testDebugUnitTest` + `lintDebug` + Moto G install proof). This lane ran no Gradle (build boundary).
- **GitHub:** repo `https://github.com/GhostMan612/Call-Dad` recorded. Local git NOT yet init (next step this session: init + remote, commit explicit paths, NO push).

## Where we were (2026-09-19, scaffold session — executor lane)

- **Scaffold COMPLETE (uncommitted):** root workflow (`AGENTS/RULES/SESSION_HANDOFF/CLAUDE/README/SPEC_SHEET`), `blueprints/` (MASTER + ROADMAP + CURRENT_STATE + CHECKLIST + CHECKPOINTS + ARCHITECTURE + BP-01..05 + ADR-001..003), `docs/` (5 guides), `.opencode/` agents + commands, `tools/verify_project.py`, `fixtures/`, `assets/`, `app/` placeholder, `.gitignore`, `local.properties.template`, isolated lane `C:\venv-hub\call-dad\`.
- **Env surveyed (read-only, nothing modified outside):** `C:\android` SDK/platforms/build-tools/NDK/cmake/adb/licenses + Studio build + JBR 25 + Moto_G_2025/BLU_View_5 AVDs; `C:\venv-hub` python 3.14.6; six Sovereign-family trees surveyed for workflow + mantle comms donor map (`docs/sovereign-comms-reuse-map.md`).
- **Gates:** `tools/verify_project.py` GREEN (2026-09-19: VERIFY PASS 10 dirs + 28 files). No app code — no unit/lint/device gates yet.
- **Team alignment PENDING:** operator setting up Gemini (R&D) + DeepSeek (architect). Awaiting further instructions after scaffold.

## Next actions (for operator + Gemini + DeepSeek)

1. **Operator: deploy the backend first.** `firebase deploy --only firestore:rules,functions`. Old builds use `calls/family_channel`, which the new rules deny: install BOTH new flavors on BOTH phones together.
2. **Operator:** Studio sync (no new runtime deps) → install parent + child → open Pairing on both (gear → grown-ups question) → scan each other's code both ways.
3. **Operator:** run the G-C8 device matrix (blueprints/CHECKPOINTS.md) and paste logcat `-s WebRTC:D` for any failure.
4. **DeepSeek:** retro-review ADR-015 (pair-scoped rooms, token push, activity-scoped session, glare rule).
5. **Gemini:** TURN provider + short-lived credential issuer options (K8), and a ZXing-only decode path if ML Kit is dropped (K9).

## Open decisions

- **D6:** Ratify RULES §1.7a (Firebase/WebRTC/anonymous auth as shipped), or order a return to the LAN plan. Owner: operator.
- **D7:** ML Kit keep vs ZXing-only (K9). Owner: operator.
- **D8:** TURN provider + credential issuing (K8). Owner: operator + Gemini.
- **D9:** The comments law (RULES §2.2) vs the codebase's rationale comments (about 850 lines, all Genesis headers present): loosen the law to allow short why-comments, or schedule a strip pass. Owner: operator.
- **Closed:** D1 (app name "Call of Daddy"), D2 (ADR-001), D3 (ADR-002), D4 (ADR-005), D5 (repo on GitHub).

## Toolchain notes (2026-09-19, verified read-only)

- SDK `C:\android\sdk`: platforms 24/31/33/34/35/36/36.1/37.0; build-tools 34–37; NDK 27.0.12077973 (donor pin) + 28/30; cmake 3.22.1 (donor pin); adb 37.0.1; licenses accepted.
- Studio `C:\android\Android Studio` AI-261.26222.65.2614.16379836; JBR 25; `JAVA_HOME=C:\android\Android Studio\jbr`.
- Pins (frozen until ADR): AGP 8.13.2 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29 / Gradle 8.13 / JVM 17 / Compose BOM 2024.12.01 / Room 2.6.1 / OkHttp 4.12.0 / CBOR 1.7.3 / Concentus 1.0.2 / CameraX 1.3.4 / ZXing 3.5.3.
- Keystores: only `tacplan-debug.keystore`; no `call-dad` keystore (debug only for now).
- Firebase: paid, zero `google-services.json` (intentional — Phase 4 fallback).
- `gh` / Firebase CLI: not on PATH.
