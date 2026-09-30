---
description: Correlate read-only device evidence across both phones for one incident — version fingerprint, filtered logcats per serial, crash lines, and what is still unexplained. Installs and builds nothing.
---

# /evidence — two-device incident correlation

You diagnose from what the devices actually report. You do not build, install, or change code.
Use the `device-evidence` tool; do not hand-run adb unless the tool fails (then say so).

## Step 0 — refuse to explain a stale binary
`action=version` on both serials first. If either `versionCode` does not match
`app/build.gradle.kts`, say: "this evidence is about <version>, not <source version>" and treat
every subsequent observation accordingly. Do not diagnose a crash from a device running a
different build than the source you are reading.

## Step 1 — per-serial log tails
`action=logs` for the parent serial and the child serial, with the right package filter
(`com.calldad.parent` / `com.calldad.child`). Pull both. Do not infer "the other side did
nothing" from one device's silence — that exact inference is what produced a phantom
"no-answer" bug.

## Step 2 — crash extraction
`action=logs` with `package=crash`. For each FATAL, report: timestamp, pid, package, the exact
exception and message, the first project frame, and the trigger frame. Then open that file and
line in source. If the stack frame's line number no longer matches the current file, say
"stack is from an older build" rather than fixing code at that line number.

## Step 3 — build the timeline
One table, both devices interleaved, ordered by timestamp:

| time | serial | flavor | event |
|------|--------|--------|-------|

Use the app's own logs (`WebRTC`, `CallDad`, `CallForegroundService`, `Firestore` tags) as the
skeleton. Walk the expected sequence for this architecture and mark each step hit or missing:
ring received → foreground service → ringer started → answer tap → answer published → ICE
trickled both ways → connected → teardown → ringer stopped.

## Step 4 — separate fact from inference
Three explicit buckets:
- `OBSERVED` — literal log lines, quoted (trimmed).
- `INFERRED` — mechanism, with the line numbers it rests on.
- `UNEXPLAINED` — the gap. Do not paper over it. Name the next single test that would close it.

## Step 5 — the smallest next test
One test, one device pair, one observable outcome. If the answer needs an install or a build,
hand it to the operator via `/flash` instead of pretending to have run it.

## Output
`BINARY: <parent version> / <child version> (source: <version>)`, the timeline, the three
buckets, `NEXT TEST:` one line, and `STILL UNPROVEN:` for anything device-only.
Never report a device result you did not read yourself.
