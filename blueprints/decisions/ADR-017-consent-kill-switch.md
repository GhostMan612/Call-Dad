---
id: ADR-017
title: Consent cert and the parental kill switch, enforced by pair-scoped rules
status: ACCEPTED (2026-09-30, revised 2026-10-01)
deciders: operator
---

# ADR-017 — Consent cert + kill switch

## Context

`SPEC_SHEET.md` §4 and `BP-05 §2` both require it and neither said where it
lived: "Dad-grants-Kid `[call,text,photo]` + expiry; revocation = kill switch",
and "`ARCHITECTURE.md:32` for a signed cert, prospective-by-default,
self-revocation-only".

Nothing existed. The allowlist was enforced by `firestore.rules` (ADR-015), which
is a real boundary but a coarse one: one contact, whole-app scope, no way to
turn one thing off without turning everything off.

## Decision

1. **Scope.** Grants are per-scope (`CALL`, `TEXT`, `PHOTO`, `PTT`), checked at
   the point of use, never once at launch.
2. **Absent denies.** Every entry point asks `ConsentGate` and an empty or
   missing cert denies. A consent check that fails open is not a consent check.
3. **The signature is DEFERRED, and the pair room is why.** `firestore.rules`
   requires `grantorUid == request.auth.uid != granteeUid` inside
   `calls/{room}`, so "a member of this pair wrote this" is already established
   by the room id. A signing-key hierarchy with no operator custody story is a
   trust root, not a feature, and is deferred rather than faked.
4. **A grant carries a monotonic `grantSeq`; a revocation names a RANGE.**
   See "What this replaced" — this is the load-bearing part.
5. **A grant is prospective**: `grantedAt == request.time` in the rules, so a
   client cannot back-date one to cover data it already sent.
6. **Bounded validity**: 30 days in `ConsentStore`, and the rules cap it at 90.

## What this replaced, and why

The first implementation marked a cert `revokedAtMs` and treated any revocation
as permanent. Two emulator failures showed that is broken in both directions:

- A flag **on the grant document** means the switch lives or dies on one mutable
  write. A grant `set` replaces that document, the flag goes with it, and the
  child is un-revoked with no trace. This is not hypothetical: the re-grant path
  dropped the revocation, and the rules test caught the revocation being denied
  for an unrelated reason at the same time.
- Making it truly un-overridable means one accidental tap **permanently bricks
  the child's phone**. A parent who revoked in a row and changed their mind has
  no way back, which is its own kind of harm.

So revocations became append-only documents in `calls/{room}/revocations/`
(`update` and `delete` are both `if false`) naming `revokesGrantSeq`, and a
grant survives only if `grantSeq > max(revokesGrantSeq)`. "Sticky" now means
**survives overwrites**, not *survives everything*.

The threat that actually matters — the child self-authorising — stays closed:
only the peer can write a grant, and only the parent-facing screen calls
`ConsentStore.grant`. So "a later grant resurrected it" can only mean "the
grown-up deliberately re-authorised", which is precisely when it should.

## Two Firestore limits learned the hard way

Both cost real debugging time and are the reason the layout is what it is:

1. **A nested `match` block cannot reliably resolve a wildcard bound in an
   ANCESTOR block.** `consents/{grantee}/revocations/{auto}` threw an
   *evaluation error* instead of denying. An evaluation error is
   indistinguishable from a permission problem in a log line, so a correct
   denial nearly got read as "the kill switch is broken". Revocations are
   therefore one level below the room — the shape `ptt` and `chat` already use.
2. **A path must have an odd number of segments to be a collection.** The
   first room-level attempt at `calls/{room}/revocations/{grantee}` was a
   *document*, and the JS SDK rejected the client call outright.

## Consequences

- `TEXT` and `PHOTO` grants are now meaningful, so the chat screen can be closed
  when a parent revokes text (BP-05 §2's "revocation blocks all comms").
- Absence denying means **the app is inert until a parent grants something**.
  That is the intended fail-closed behaviour and it will surprise anyone who
  flashes a build and taps around: a fresh install cannot call until the parent
  side grants. Operators should expect that on first run.
- Re-granting requires a strictly higher `grantSeq`; `ConsentStore.grant` takes
  it as a parameter and the rules enforce it, so a retried or reordered write
  cannot achieve it by accident.
