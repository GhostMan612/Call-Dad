# Lessons Learned

Two kinds of knowledge, deliberately kept apart:

- **PART 1 — MISTAKES.** Things this repo got wrong, why, and the check that now
  stops it recurring. Every entry is a mistake this project actually made.
- **PART 2 — PLATFORM REFERENCE.** Facts about Android / Firestore / WebRTC that
  cost time to discover. True, but not *our* mistakes; nothing here implies we
  erred. Kept because they are expensive to rediscover.

Anything that is not a mistake we made does not belong in Part 1.

---

# PART 1 — MISTAKES

## A law that was never enforced (privacy, §1.5a)
**Mistake.** `RULES.md` forbade writing a real device serial into a committable
file. Six tracked files did exactly that for two sessions — and
`.opencode/tools/device-evidence.ts`, the tool whose job is device evidence,
*hardcoded both serials* as an "expected mapping" table. The enforcement tool was
the source of the leak. I wrote the law and then broke it in the file I wrote to
enforce it.
**Root cause.** Prose-only invariants drift silently; nothing fails when ignored.
**Check.** `tools/verify_project.py` now bans a 14+ digit serial and an
`adb-<SERIAL>-…` form in any tracked text file, and
`VerifyProjectSelfTest` asserts those patterns are *applied*, not merely declared.
`device-evidence` maps roles from the `model` field of `adb devices -l` at run
time instead of a baked-in table.

## A gate nobody had watched fail (`verify_project.py` self-test)
**Mistake.** The repo had a verification gate for the whole of Contract 11 and no
test proving the gate fails on a bad tree. A gate never observed going red is a
gate nobody can trust — the serial leak passed it twice.
**Root cause.** Only the happy path gets exercised; the ban list accumulated
patterns nobody had ever seen fire.
**Check.** `VerifyProjectSelfTest` asserts the ban patterns exist, are wired into
the scan loop, and report the matched text. A future added pattern must come with
a reason, which is the cheapest possible prompt to think about whether it fires.

## The safety control that controlled nothing (vc10's kill switch)
**Mistake.** The parental kill switch shipped complete, documented, ADR'd,
emulator-proven at 44/44 — and enforced **nothing** on calling or the walkie
talkie, in either direction, while continuing to download photos and messages
after revocation. Calling and the walkie talkie are the two OLDEST features in
the app; they predate the consent model, and `ConsentScope`'s own KDoc said so
("PTT — ALREADY SHIPPED without a cert"). Nobody went back. Separately, the
parent's grant sequence was read from a query that can never return anything on
the parent's own phone, so the switch's button was inoperable on the one device
that owns it — and `revoke()` refused every press with a message that reads like
a network problem.
**Root cause.** Every gate I had checked the *halves* of consent — the domain
gate, the Firestore rules, the emulator suite, the parent-facing screen's source —
and none checked that a *consumer* read the answer. `grep ConsentScope` across
`app/src/main` answered the whole question in one command: the scope appeared
once, inside the store, computing a decision nobody read. Same shape as the
`ChatViewModel.scopes` field nobody wrote. Both were "complete features, green
gates, unusable", and both were caught only by looking for what a finished
feature *should have touched*.
**Check.** `ConsentEnforcementRegressionTest` (11 tests) pins each direction of
each feature, the listener as well as the screen, and the unknown-decision case.
The general rule: **when a feature is finished, grep for what it should have
touched, then read the list.** A feature with no reader of its own gate is a
decorative feature.

## The newest safety file was the least reviewable file in the repo
**Mistake.** `ChatText.kt` — the file that decides what a six-year-old may send —
contained a literal `0x00` and `0x1F` inside a *comment*, on the very line
explaining why control characters matter. `CallLogStore.kt` had a literal `0x1F`
in a comment too. A raw control byte makes git treat the file as binary, so
`grep`, `git diff`, and human review all silently skip it. Every gate in the repo
had passed for the entire life of both files. `git grep` reported them as
"Binary file matches".
**Root cause.** I wrote the escape text `\x00-\x1F` in a KDoc and it survived as
raw bytes; nothing in the toolchain complains, and the damage is to *review*
rather than to compilation, so the compiler is no help at all. The irony is
exact: the affected line was the one documenting this hazard.
**Check.** `tools/check_control_bytes.py` scans tracked text files, and
`verify_project.py` now fails the gate on a NUL or any C0 control / DEL, reading
the file as **bytes** rather than `errors="replace"` — the decoding that had been
hiding it. A file that stops being greppable is a bug in the file, not a quirk
of git.

## Six tests that could only fail, sitting in a tree the gate called green
**Mistake.** While fixing a compile error I ran the full five-suite gate and it printed GATES GREEN — on a tree that did not compile. Running `assembleParentDebug` is what found it. Then, underneath, **six unit tests were failing too**, all from the same pass:

- `PhotoSafetyTest` (2) read `app/src/main/AndroidManifest.xml` while the test working directory is `app/`, so both threw `FileNotFoundException`. The gallery-permission kid-safety check had been asserting *nothing at all* while sitting in the suite looking enforced.
- One of those two was **wrong on its own terms**: it demanded the app hold no CAMERA permission, but the video call legitimately needs one. It only "passed" because the exception fired before the assertion.
- `RoutesTest` (2) searched for a literal `composable(Routes.CALL)` that no longer exists, because the destination is `route = CALL_ROUTE` where the alias interpolates `"${Routes.CALL}?mode={mode}"`.
- `WiringRegressionTest` (2) sliced source with `substringAfter(x).substringBefore(y)`. **`substringBefore` returns the entire remaining file when its delimiter is absent**, so moving a marker silently widened the slice — and both tests then found their forbidden token *inside the comment explaining its absence*.

**Root cause.** Every one of these is a check that cannot distinguish "the property holds" from "I am looking at the wrong text". A test that throws is easy to read; a test that greps and misses is not. And three of the six were *introduced by the fix for the first*, so the tree got worse in the act of repairing itself while still reporting green.
**Check.** Slice source by counting braces, not by guessing at a second marker, and strip comments before asserting a token is absent. Add a `requireNotNull`-style check that a parse found something, so an empty parse fails loudly instead of producing a plausible wrong answer. `prove_gates_bite.py` now injects a **duplicated brace** and asserts red *via the Kotlin compiler* — the earlier control-byte probe proved only that a byte scanner works, and the compiler is the hole this fell through.

## A gate that had never been seen red, again, and worse
**Mistake.** The `gate` tool printed GREEN from a tree where
`compileParentDebugKotlin` was failing: it had no `rules` gate at all, and
`r.out || r.err` discarded the Kotlin diagnostics that would have said so. Then,
separately, I twice declared v0.1 "complete" against gates that were green and
features that had never run.
**Root cause.** Only the happy path gets exercised, and a gate that is *trusted*
gets trusted harder precisely because it is trusted. A check nobody has watched
fail is a check of unknown strength.
**Check.** `tools/prove_gates_bite.py` injects a known defect, asserts the gate
exits non-zero **for the expected reason**, asserts the tree is restored
byte-for-byte, and asserts green returns. Run it after changing any gate. This is
the operational form of `VerifyProjectSelfTest` below, and it caught a real
control byte in the process.

**The commit reached `origin/main`.** `8f47512` was pushed with a duplicated
brace in `ChatViewModel.init`; `4a5c555` fixed it. The general form, and the one
worth keeping: **this repo has no suite that compiles the app**, so "five gates
green" has never meant "it builds" — a gate that is not looking at the property
you care about is the recurring failure here, and a broken tree on `main` is what
that costs when nobody notices. `assembleParentDebug` is the only thing that has
ever caught this class, and it is an `ask` task.

## Green gates ≠ shipped (blocked commit lost real work)
**Mistake.** The K12 (loud voice message) and K21 (lock-screen trap) fixes were
written, gated green, and a commit message drafted. The commit was blocked by an
over-broad `write*` deny in `opencode.json`. I reported the block, wrote a
handoff note recommending the operator run it, and moved on. The work was never
committed and later disappeared from the working tree entirely. The only reason
anyone found out was `QuietNotificationTest` failing on the missing code.
**Root cause.** I treated a green gate as delivery when it certifies only the
working directory, which is one revert away from nothing. Escalating a block in
the same turn is the whole point of escalating.
**Check.** `RULES.md` §1.4 / §1.4a: a blocked commit is an emergency — stop,
report, get the exact command run or the block lifted. Never end a session
carrying uncommitted work and describe it as delivered. `git status` clean is the
finish line.

## A subagent description is a router, not a note (per-edit gate runs)
**Mistake.** `.opencode/agents/gate-runner.md` said "use before declaring any
change done" in its `description`, while its body correctly said "invoke once at
the end of a phase". Descriptions are matched on to decide whether to invoke the
agent, so the description won and the phase-closing body never got a chance to
matter. The existing test only read the body, so it could never catch this.
**Root cause.** Assuming prose inside a file is read the same way regardless of
where in the file it lives. Front matter is the dispatch surface; it is read
first, alone, and by something that is not me.
**Check.** `ToolUseDisciplineTest.gateRunnerDescriptionMatchesItsOwnPhaseClosingBody`
parses the front matter and fails on per-change phrasing.

## A source-scan test that matched the fix's own comment
**Mistake.** Three early versions of `QuietNotificationTest` produced false
failures against correct code. Every one came from scanning raw text: slices
anchored on indentation-sensitive strings missed, and the fix's own comment names
`CHANNEL_INCOMING_CALL` and `PRIORITY_DEFAULT` to *describe the bug*, so any raw
scan matched the prose.
**Root cause.** Treating source text as a value stream instead of a code stream.
**Check.** Strip comments first, then anchor on a unique function signature, never
on whitespace or a string that appears in an explanation.

## A per-notification priority check cannot catch a channel bug (K12)
**Mistake.** A `PRIORITY_LOW` assertion on the waiting-voice-message notification
passed the whole time the phone rang at full volume. The bug was never the
priority — the notification shipped on the `IMPORTANCE_HIGH` call channel, and on
Android 8+ the *channel* importance wins and the per-notification priority is
ignored. A call that fails to ring is a worse failure than a message that rings,
so quieting the call channel was never the fix.
**Root cause.** Asserting on the field I was editing rather than the field the
platform obeys.
**Check.** `QuietNotificationTest` asserts on the *channel*: a dedicated
`CHANNEL_PTT_MESSAGE` at `IMPORTANCE_LOW`, no sound, no vibration, and ids that
must differ from the ring channel.

## Assumed my edit persisted; it had not
**Mistake.** Twice this session I described an edit as done without re-reading
the file, and twice the change was not there. One cost a wrong claim about
versionCode 9. The other cost a full round of wrong theory about a compile error.
**Root cause.** Treating a successful tool return as proof of on-disk state.
**Check.** Re-read the line before building a story on it. A subagent's claim is
not evidence either — same rule, different speaker.

## A test that pins the deny list while the behaviour is broken
**Mistake.** After fixing the ICE-restart and consent work, I ran
`:app:testParentDebugUnitTest` **six separate times**, once per failing test, and
cost about fifteen minutes of build time. The operator called it out by name.
Root cause, and it is the uncomfortable one: the reason I reached for gradle
directly was to filter output, so I tried `Select-String`, **my own rule denied
it**, and I then ran the full task instead of picking up the `gate` tool that had
been sitting there the whole time. The same run that denied me the pipe should
have been the run that told me which tool to use.

**Why the tests did not catch it.** `ToolUseDisciplineTest` asserts the deny list
in `opencode.json` and the law's text in the docs. Both are true, and the gate
went green, while the actual behaviour was exactly what §1.4a forbids. This is
the same shape as the serial leak and the reconnect string: **a proxy was
pinned instead of the property.** The deny list is a proxy for "does not shell to
read a file"; the text is a proxy for "does not gate per edit." Neither can fail
when the behaviour regresses.

**Check (the real one, added 2026-09-30).** `ToolUseDisciplineTest` now also
asserts the `gate` TOOL is the only sanctioned way to run a Gradle test/lint task
— that the gate docs, `RULES.md` §1.4a, and the skill all name `gate`, that no
document shows a bare `:app:testParentDebugUnitTest` invocation as the thing to
type, and that `gradlew*test*` / `gradlew*lint*` are not reachable as a raw
suggestion. This still cannot observe what I actually did — no test can, from
inside the process. It removes the ambiguity that caused it: the sanctioned
command is named in the same place the rule is stated.

**The general lesson, which is the one worth keeping.** A green gate certifies
the tree, never the behaviour of the agent running the gate. Every law in this
repo has now been "pinned" in some way, and twice the pin was the wrong shape.
Before writing the test, ask what the *behaviour* is and whether anything can
actually go red when the behaviour regresses.

## The gate tool reported GREEN while the compiler was failing
**Mistake.** Twice on 2026-10-01, `gate` returned "GATES GREEN" from a tree where
`compileParentDebugKotlin` was FAILED. Two independent causes, both in the tool.

1. **There was no `rules` gate at all.** The Firestore emulator suite was simply
   not in `gate.ts`, so an entire security gate never ran and nothing said so.
   Worse, the emulator needs a JVM and exits non-zero without one, so even when
   invoked by hand it "skipped" silently. I spent a phase believing the new rules
   stanzas were verified when they had never been executed once.
2. **`r.out || r.err` discarded the diagnostics.** Kotlin writes `e:` lines to
   stderr and Gradle writes task lines to stdout, so `||` kept the task lines and
   threw away the only output that said what was wrong. The gate printed a FAIL
   with no reason in it, which is why I resorted to shelling out for the error —
   the very thing §1.4a forbids.

**The shape of it, again.** A gate that runs the tests is a *proxy* for "the
tests passed". Neither the presence of the rules suite nor the FAIL label says
anything about whether the thing being reported is true. The properties that
actually mattered were "every suite is named in the verdict" and "a failure shows
its reason", and neither had a test.

**Fixed in `gate.ts`:** a real `rules` gate with `JAVA_HOME` pointed at the
documented Studio JBR so it runs instead of skipping; `out + err` concatenated;
a 200-line tail, because a short one is a lid, not a tail; and the verdict now
reads `GATES GREEN BUT n SKIPPED` when anything was skipped, so a security gate
can never hide inside a green. A skipped *security* gate also prints the exact
command the operator should run.

**Check.** After this, "all five gates green" in this repo means five gates were
*named in the output*, and the verdict text distinguishes green from
green-but-skipped. A claim about gate coverage should be checkable by reading the
verdict line, not by trusting the habit of saying it.

---

# PART 2 — PLATFORM REFERENCE

Facts about the platforms, not mistakes we made. Nothing here is a claim that
Call-Dad got something wrong.

## Android 14 Constraints
- `phoneCall` FGS requires `MANAGE_OWN_CALLS`. VoIP apps must use microphone FGS.
- FSI demotes to Heads-Up notifications on unlocked Android 14+ devices.
- `ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED` throws `NoSuchMethodError`
  below API 34.
- FGS while-in-use constraints require deferring background FGS starts to
  `onResume()`.
- On Android 8+ the notification **channel** importance is what the user
  perceives; `setPriority` on the notification is largely ignored. A quiet
  notification on a loud channel is still loud.
- `setShowWhenLocked(true)` / `setTurnScreenOn(true)` opt the activity in to
  drawing *over* the keyguard once launched. Both are API 27+ against this repo's
  minSdk 26, so the call must be guarded or it crashes on Android 8.0.
- `isKeyguardLocked` lives on `KeyguardManager`, not on `Service` or `Context`.
  It is a property you must fetch, not a method you can call bare.

## Firestore Constraints
- `serverTimestamp()` fires the listener twice: once with `hasPendingWrites=true`
  and a null timestamp, then again with the server value.
- Mobile SDKs use optimistic concurrency. Transactions retry on contention.
- Firestore TTL policies delete within 24 hours. Explicit cleanup is required for
  time-sensitive documents.
- Client-side timestamps are vulnerable to clock skew. Bound the maximum future
  value in rules.
- Batch writes are atomic across documents but persisted offline: a batch write
  succeeds locally with the network down, and the writes fire when connectivity
  returns.
- CRITICAL: Firestore rules are NOT filters. A read rejected by rules fails the
  entire transaction with `PERMISSION_DENIED`. Do not protect reads with
  conditions the client needs to evaluate.
- `batch.update()` fails with `NOT_FOUND` if the document does not exist. Use
  `set(..., SetOptions.merge())` when the document may not exist.

## Doze Mode Constraints
- Doze rate-limits background work to roughly one wake per 9 minutes. Heartbeats
  shorter than 10 minutes are throttled.
- Doze suspends network access even with a foreground service.
- The 20-minute stale threshold gives a 2.2x margin against the 9-minute cadence.
- Force-stop from Settings blocks all notifications until the app is reopened.
  This is Android's anti-abuse behaviour and cannot be worked around; if the app
  depends on waking a force-stopped app, that dependency is broken by design.

## Build / Gradle
- Product flavors rename every variant task: there is no `testDebugUnitTest` or
  `lintDebug`, only `testParentDebugUnitTest`, `lintChildDebug`, etc. Gate
  commands must name them.

## WebRTC (org.webrtc)
- Dispose order is load-bearing: `peerConnection.dispose()` first (it disposes
  the transceivers and the remote track's Java wrapper, detaching renderer
  sinks), then local tracks/sources/capturer, then the factory. Freeing the
  factory under a still-attached remote track is the hangup SIGSEGV.
- A disposed client is not reusable. Build a new one per call attempt.
- `addIceCandidate` before `setRemoteDescription` is rejected. Buffer remote
  candidates. Queue local ones until your own SDP is published, or they land on
  the previous call.

## Compose / ViewModels
- `viewModel()` without an explicit `viewModelStoreOwner` is scoped to the nav
  back-stack entry, whatever the accessor's comment claims. Pass the activity for
  app-wide sessions.

## Firestore rules
- Authorize from the document id when the id can carry the ACL
  (`callId.split('_')`): no `resource.data` read, so transactions can pre-read
  missing docs safely.
- A fixed, well-known document id is readable by every signed-in anonymous user;
  "unguessable" needs an id that is actually unguessable or rule-bound.

## Foreground services
- `startForeground()` must run synchronously in `onStartCommand`, before any I/O.
  Validate afterwards and `stopForeground(REMOVE)` if the reason evaporated.
