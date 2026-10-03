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

## Amendment 2026-10-01 (vc11) — the shipped decision enforced nothing

**Decision 1 above was WRITTEN but not IMPLEMENTED for the two oldest features.**

vc10 shipped a "Turn everything off" that closed Messages and Pictures while
**calling, the walkie talkie (both directions, including inbound clips playing
aloud on any screen), and photo/chat downloads carried on regardless.** Calling
and PTT predate the consent model — `ConsentScope`'s own KDoc said so ("PTT —
ALREADY SHIPPED without a cert") — and nobody went back. Separately, the parent's
grant sequence was read from `granteeUid == ownUid`, a query that can never
return on the parent's own phone because the rules forbid a self-named grant, so
`highestSeq` was permanently 0 there: `revoke()` refused every press with a
message that reads like a network problem, and `grant()` computed seq 1 every
time, so the second "Allow everything" was `PERMISSION_DENIED`. **The kill
switch was inoperable on the one phone that owns it.**

Every gate had checked the *halves* of consent — the domain gate, the rules, the
44-case emulator suite, the parent screen's source text — and none checked that a
*consumer* read the answer. `grep ConsentScope` across `app/src/main` answered it
in one command: the scope appeared once, inside the store, computing a decision
nobody read.

Fixed in vc11/0.3.1 and pinned by `ConsentEnforcementRegressionTest` (14 tests):
both call directions, an incoming ring refused *before* the phone rings with
ENDED published so the caller's phone stops too, revocation ending a live call,
the walkie talkie gated in both directions including the engine's own send path,
the parent's sequence read from the grants it AUTHORED, and **every feature's
Firestore listener gated, not just its screen**.

**Still unwitnessed: no device has ever exercised this control.** A decision
record is not evidence of enforcement.

## Amendment 2026-10-02 (vc12) — the gate was still one consumer short

The vc11 amendment fixed every consumer **inside the app**. A six-auditor sweep
on 2026-10-02 found that was not the same as fixing every consumer:

1. **`CallForegroundService` had no consent gate at all.** It is the only
   consent consumer that runs when the process is dead, and it validated the
   PAIRING and then rang at full volume. So a parent's check with the child's app
   open passed and a check with the app force-stopped failed — a kill switch that
   depends on the child happening to have the app foreground. This is vc10's
   defect one layer deeper. Now gated by a one-shot, fail-closed
   `ConsentStore.decisionNow` **before** `startRinging`, publishing `ENDED` so
   the caller is not left ringing out to nobody. The structural test asserts the
   ORDER, because a gate after `startRinging` is a comment.
2. **`ConsentScope.PTT` was read by nothing.** The walkie talkie collected
   `decision` — a single headline that answers for `CALL`. A CALL-only grant is a
   legal document, so the mixup was latent, not theoretical. Added
   `ConsentStore.decisionFor(scope)`.
3. **The Helper microphone had no scope, and streamed off-device.** No
   `ConsentStore` in the file, no mic scope, tile unconditionally on the child's
   Home grid — and it fell back to the **network** recognizer, so on a budget
   device with no on-device model a child's speech went to the OEM's speech
   service. Added `ConsentScope.VOICE`, and the recognizer is now on-device-only
   with an honest refusal. `EXTRA_PREFER_OFFLINE` was never the guarantee it
   looked like: it is ignored by many OEMs from API 33.

**The rule these produced:** *a gate on a screen is not a gate.* The consumer
that runs when the app is not running is the one that has to hold it, and a
per-scope model is decorative until each feature reads **its own** scope.

**Known gap, not fixable here.** A member can write a cert naming the PEER. It
cannot be closed in the rules: the room id is two symmetric UIDs and nothing in
Firestore says which is the grown-up, so denying it denies the legitimate
direction too. It makes `isGrantor` true on the child's own phone, which
short-circuits `recompute()` and would make the kill switch a no-op there.
Closing it needs a **trust root** — an asymmetric role decided at pairing, or a
signed credential. The emulator asserts the insecure behaviour in a
banner-marked test so the day it is fixed, that failure is the signal.

## Consequences

- `TEXT` and `PHOTO` grants are now meaningful, so the chat screen can be closed
  when a parent revokes text (BP-05 §2's "revocation blocks all comms"). Since
  vc11 this is true of `CALL` and `PTT` as well — see the amendment above for why
  it was not true until then.
- Absence denying means **the app is inert until a parent grants something**.
  That is the intended fail-closed behaviour and it will surprise anyone who
  flashes a build and taps around: a fresh install cannot call until the parent
  side grants. Operators should expect that on first run.
- Re-granting requires a strictly higher `grantSeq`; `ConsentStore.grant` takes
  it as a parameter and the rules enforce it, so a retried or reordered write
  cannot achieve it by accident.
