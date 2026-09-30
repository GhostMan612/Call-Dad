---
description: Hand the operator an exact, fingerprinted build-flash-verify runbook and print the current expected version. Runs no build and installs nothing.
---

# /flash — operator build + flash runbook

You do not build and you do not install. You produce a precise, copy-pasteable runbook with the
version fingerprint resolved from source, and you state what evidence is still missing.

## 1. Resolve the fingerprint (read source, do not build)
Read `app/build.gradle.kts` for `versionCode` and `versionName`. Report them, e.g.
`versionCode 4 / versionName 0.2.1`, and the two flavor ids `0.2.1-parent` and `0.2.1-child`.
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

Device serials are deliberately NOT written into this repo. RULES.md forbids real
device identifiers in committed files; read them from `adb devices -l` at run time
or from the operator's message.

## 4. Post-install verification the operator must run
Re-run `device-evidence` `action=version` and require the on-device `versionCode` to equal the
source `versionCode`. Only then collect logs.

## 5. Console deploy (operator, never this lane)
```
firebase deploy --only firestore:rules,functions
```
Call out that until this lands, pairing writes to `pairings/{uid}` can be denied and surface to
the user as "The other device didn't respond."

## Output
A single fenced block of commands the operator can paste, the expected fingerprint, the APK
timestamp expectation, and a `MISSING EVIDENCE:` list. If the tree is dirty, bump
`versionCode` before the operator builds, or the install will be indistinguishable from the last one.
