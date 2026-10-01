---
id: ADR-018
title: No Room — the call log is DataStore, and the chat thread is pair-scoped Firestore
status: ACCEPTED (2026-10-01)
deciders: operator
---

# ADR-018 — Where the call log and the message thread actually live

## Context

`SPEC_SHEET.md` §2.5 says: "Call log + message store: local Room, call history
(missed/answered), message persistence. No cloud sync v0.1."

The toolchain has no Room, no KSP, and no SQLCipher, and `AGENTS.md` lists all
three under "Not in the build" (ADR-004 is the source of truth). So the spec asks
for a database the build deliberately does not have.

BP-05 §3 already anticipated this for encryption: ADR-003 was closed as "deferred
with justification" once it turned out there is no local database to encrypt.
The same question arrives here for the database itself, and deserves the same
honest answer rather than a silent divergence.

## Decision

**The call log is DataStore preferences. The chat thread is pair-scoped
Firestore. There is no local message store, and Room is not adopted.**

## Why not Room

A call log is a bounded, append-mostly list of tiny records —
`{id, startedAt, durationMs, outcome, wasOutgoing}` — capped at 100 rows
(`CallLog.MAX_ROWS`). There are no joins, no ad-hoc queries, and no relational
integrity to protect, and 100 such rows is a few kilobytes. Room would add a
compiler plugin, a schema, a migration story, and an encryption question to store
a log that a child will never have more than a few dozen of.

The one thing the log must do is survive the app being killed, and DataStore does
that natively with the same dependency the app already uses for the peer UID
(`SecurePeerStore`).

The trade is explicit: a 100-row cap. `CallLog.add` drops the oldest, and
`CallLogTest` pins both the cap and the drop order. If the log ever needs
searching, pagination, or more than a screenful of history, that is the signal to
revisit — and the revisit will be measured, not vibes.

## Why the chat thread is NOT local

This is the part that would be wrong to assume is a shortcut.

`SPEC_SHEET.md` §4 requires: "Chat/voice/photo stay on-device; any future hub sync
redacts them by default." The voice clips already break the on-device half of
that — they live in Firestore, under the pair room, because a message the child
was told was sent must still be there when the phone comes back online
(ADR-016). Making text local-only would be a split-brain: voice in one place,
text in another, two retention policies, and a child who cannot tell which
message is the real one.

So text follows voice, into `calls/{room}/chat/`, bounded to 200 messages and
pruned **only for read history, never for unread**. "It disappeared" is a worse
failure than a long thread.

What this does *not* do is claim the privacy property is satisfied. The honest
statement is that the pair room is the boundary — two UIDs in the path, a stranger
cannot read it, and the emulator suite proves it — and the stricter "stays on the
device" wording in §4 is **not met** for chat or voice. It is recorded as unmet
rather than quietly reinterpreted.

## Consequences

- `SQLCipher` (ADR-003) stays closed, with a sharpened condition: it re-opens the
  moment something sensitive is stored locally, which is a *real* local message
  or photo cache and not yet a thing this app has.
- The call log is device-local and is lost on uninstall. That is the right
  default for a child's call history; it is also not "message persistence", and
  the spec line stays open on that point.
- The emulator rules suite is the security gate for chat. It is now wired into
  the `gate` tool — see the lesson in `LESSONS_LEARNED.md` about that gate
  silently omitting the suite.
