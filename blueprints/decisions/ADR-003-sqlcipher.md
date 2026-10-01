# ADR-003 — Room at rest: SQLCipher now vs later

- Status: **DEFERRED WITH JUSTIFICATION** (the option BP-05 §3 explicitly permits), 2026-09-30 · owner: operator to ratify
- Context: donor uses SQLCipher 4.5.4 (passphrase wrapped by Keystore) so Room stays readable in background without operator unlock. Adds NDK/binary weight + migration complexity.
- Options: A) SQLCipher from BP-01 (donor-faithful, heavier). B) Plain Room v0.1, SQLCipher in BP-05 (lighter start, migration cost later).
- Recommendation (executor): A if team accepts weight; else B with explicit migration ticket.
- Decision: **B — deferred, with justification below. Not PENDING.**

## The justification BP-05 §3 asks for

> "At-rest crypto per ADR-003: SQLCipher (passphrase wrapped by Keystore) **or deferred with justification**."

Encrypting at rest protects data that is stored. **This app stores nothing that
needs encrypting.**

| What is on disk | At rest as | Sensitive? |
|---|---|---|
| Paired peer UID, pairing timestamp | DataStore Preferences, `SecurePeerStore.kt` | One opaque Firebase UID, already pseudonymous and useless without a live Firebase project |
| PTT voice clips | **Firestore**, in the pair's room — never on disk beyond playback temp | Are the sensitive thing, and they are not local storage at all |
| Call state, SDP/ICE, candidates | **Firestore**, room-scoped | Ephemeral, cleaned up |
| Consent certs | Not stored yet (ADR-017 model is inert) | — |

So the mitigation actually in force is not a database cipher: it is
`android:allowBackup="false"`, which stops the peer store leaving the device in a
cloud backup or a device-to-device transfer. For the one record that exists, that
is the honest threat model, and SQLCipher would protect a value that is not the
sensitive one.

## The condition that re-opens this

**Re-opened by the first local store of real user content**, in this order:

1. Text chat (BP-03) — messages persisted locally.
2. Photo sharing (BP-04) — photo caches and downscales on disk.
3. A call log (SPEC_SHEET §20) that retains anything longer than the room.

Any of those lands, SQLCipher is decided on the spot **before** the first row is
written, not after — retrofitting a cipher over an existing plaintext store means
either a migration or a wipe, and for a child's device a wipe is the kinder
option.

## What is NOT being claimed

This is a **deferral**, not a security win. It does not satisfy a literal reading
of "SQLCipher or deferred" as "the risk was assessed and accepted" — the risk was
never there, because there is no database. It is recorded this way because
`ROADMAP.md:11` carried SQLCipher as a Phase 6 ❌ while `ADR-003:3` had already
declared it moot, and **a roadmap row that says ❌ forever trains a reader to
ignore ❌**. Closing it with the reason is the honest move; leaving it open is not.

## Also now recorded in the dep ceiling

`SQLCipher`, `Room`, `net.zetetic`, and `androidx.sqlite` are **not in the build**
and are not in `gradle/libs.versions.toml`. Adding any of them needs an ADR update
here first, per RULES §1.7's dependency-ceiling law — which is also the mechanism
that makes this deferral enforceable rather than aspirational.