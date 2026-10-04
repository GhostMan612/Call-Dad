---
description: Hand the operator an exact, fingerprinted build-flash-verify runbook and print the current expected version. Runs no build and installs nothing.
---

# /flash — operator build + flash runbook

You do not build and you do not install. You produce a precise, copy-pasteable runbook with the
version fingerprint resolved from source, and you state what evidence is still missing.

## 0. Roles (re-assigned 2026-10-03)

**Moto G 2025 = PARENT (`com.calldad.parent`). Q8K tablet = CHILD
(`com.calldad.child`).** The BLU View 5 is retired and must not be installed to.

The Q8K is **SDK 30** — the only device below API 31, so its Ask Helper tile is
withheld by design. The Moto is wireless-adb and its mDNS port moves per session;
the Q8K is on USB and plugging it in can drop a USB sibling off the list.

## 1. Resolve the fingerprint (read source, do not build)
Read `app/build.gradle.kts` for `versionCode` and `versionName`. Report them, e.g.
`versionCode 12 / versionName 0.3.2`, and the two flavor ids `0.3.2-parent` and `0.3.2-child`.
Never quote a version from memory or from a doc — read the file every time. **Note the
header comment on line 2 of that file has been wrong before; read the `versionCode =`
assignment, not the header.**
Note whether the build is dirty (`git status --porcelain`) — a dirty tree invalidates the
fingerprint, because the operator will build uncommitted bytes.

## 2. On-device check first (read-only, allowed here)
```
C:\android\sdk\platform-tools\adb.exe devices
```
For each device, read the installed fingerprint with the `device-evidence` tool
(`action=version`). If the installed `versionCode` differs from source, say so loudly: every
logcat and every crash line from that device is evidence about a *different* binary. This single
check has repeatedly been the thing that explained an "impossible" crash.

## 3. Hand back the operator commands
```powershell
cd C:\Call-Dad
.\gradlew.bat clean assembleParentDebug assembleChildDebug --no-daemon --console=plain
C:\android\sdk\platform-tools\adb.exe -s <PARENT_SERIAL> install -r .\app\build\outputs\apk\parent\debug\app-parent-debug.apk
C:\android\sdk\platform-tools\adb.exe -s <CHILD_SERIAL>  install -r .\app\build\outputs\apk\child\debug\app-child-debug.apk
```
(That block is for the OPERATOR to run. The agent does not execute it — RULES §1.5.
The agent's own verification is `dumpsys` via the `device-evidence` tool.)

PARENT = Moto G 2025 (ask the operator for its serial; it is a wireless ADB
pairing and will drop). CHILD = BLU View 5 (ask the operator for its serial).

Device serials are deliberately NOT written into this repo, and `tools/verify_project.py`
now FAILS the gate if one appears in a tracked file (RULES §1.5a). Read them from
`adb devices -l` at run time or from the operator's message. Map roles from the model
field: **Moto G 2025 = parent, Q8K tablet = child** (re-assigned 2026-10-03; the
BLU View 5 is retired).

## 4. Post-install verification the operator must run
Re-run `device-evidence` `action=version` and require the on-device `versionCode` to equal the
source `versionCode`. Only then collect logs.

## 5. Console deploy

```
firebase deploy --only firestore:rules,functions
```

**Run from THIS LANE, not the operator's** — `opencode.json` carries a narrow
`ask` allow for exactly this command, added 2026-10-03. It worked on the first
attempt. Two corrections to what this section used to say, both of which were
wrong and cost real time:

- ~~"operator, never this lane"~~ — the lane CAN deploy, and did on 2026-10-04.
- ~~"permission sets are read at session start, so a config change never affects
  the session that made it"~~ — **false for this harness.** It re-reads the
  file. Do not write that down as law without testing it.

Current live state: rules released to `calldad-508d7` **2026-10-04**, with the
consent-read fix, the chunk ceiling and the per-generation `negotiationRound`
reset. `onCallRoomWritten` / `onPttClipWritten` are v2, us-central1, 256MB,
nodejs22, with the ring TTL 1h + retry + `collapse_key`.

Re-run the deploy after ANY edit to `firestore.rules` or `functions/` — it is the
only thing that makes them live. If a write is denied in the field, the deployed
ruleset is older than this tree; check the deploy date rather than assuming a
stanza is missing.

**Remove the narrow allow once the deploy is done.** It has one use and
overstaying is a hole in the law.

## Output
A single fenced block of commands the operator can paste, the expected fingerprint, the APK
timestamp expectation, and a `MISSING EVIDENCE:` list.

**Do NOT reflexively bump `versionCode` on a dirty tree.** This repo assigns one per
*behavioural* change, deliberately, with a recorded reason for why the number must not be
reused (`app/build.gradle.kts`, and the version rows in `blueprints/CHECKLIST.md`). A
reflexive bump produces a number that means nothing, and it is how a version gets burned
on a broken commit. Report the fingerprint you read, why a bump is needed, and why the new
number must not be reused — then ask before assigning one.

**And say plainly that no suite in this repo compiles the app.** `8f47512` was pushed with
a duplicated brace and the standing gate printed GREEN on it; `assembleParentDebug` is the
only thing that has ever caught that class. If the runbook does not include an assemble
step, the operator is about to flash an unverified tree.
