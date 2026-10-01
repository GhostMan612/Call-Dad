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
