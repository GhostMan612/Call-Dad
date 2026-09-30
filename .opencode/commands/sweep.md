---
description: Run a parallel read-only audit of the Call-Dad call core, media, Firestore rules, FCM wakeup, kid-UX, and doc drift, then consolidate into one prioritized fix list with no edits.
---

# /sweep — full-repo read-only audit

Fan out the read-only auditors **in parallel** (one Task call per auditor, in a single message),
then consolidate. Do not edit anything. Do not run gates — `/verify` does that.

Launch these subagents concurrently:
- `call-core-auditor` — call lifecycle, teardown, timers, generations
- `webrtc-media` — WebRTC, tracks, audio routing, camera, data channel
- `firestore-rules-auditor` — rules, pair scoping, function push, emulator coverage
- `fcm-wakeup-auditor` — token, ring service, doze/killed app, ringer discipline
- `kid-ux-guardian` — allowlist, parent gate, one-action screens (existing agent)
- `doc-drift-auditor` — docs vs tree

For each, give it the current `HEAD`/branch and the specific question you want answered, and
require `file:line` evidence. After all return:

1. **De-duplicate** — many findings will be the same root cause wearing different hats.
2. **Re-verify** — before you write anything down, spot-check at least the top 3 findings
   yourself in source. A subagent's claim is not evidence until you have seen the line.
3. **Rank** by blast radius, not by novelty: crash > data loss > kid-safety > stuck call >
   missing feature > smell.
4. **Order the fix list** so each step is the smallest coherent unit with a host test beside it.

## Output format
```
SWEEP <branch>@<sha>
DEPLOYED CANON: <what the operator must deploy, if anything>
FINDINGS
  1. [SEVERITY] one-line — file:line
  ...
ROOT CAUSES (merged)
NEXT UNIT OF WORK (smallest coherent + the test that comes with it)
NOTHING FOUND IN: <auditors that came back clean>
CLAIMS I COULD NOT VERIFY: <list>
```
Never say a fix is "done" or a device "works". Mark every finding `CODE-READ` unless a gate
or operator output backs it.
