# ============================================================
# ADR-017 — consent certificate + kill switch (domain model)
# Status: DECIDED 2026-09-30 · Scope: v0.1 · Author: agent, operator to ratify
# ============================================================

- Context: BP-05 §2 specifies "Consent cert (`consent.py` model): Dad-grants-Kid
  `[call,text,photo]` + expiry; revocation blocks all comms + shows kind UI. Unit:
  expired/revoked → blocked." `ARCHITECTURE.md:32` adds "signed cert with
  prospective-by-default, self-revocation-only". Zero source existed for any of it.
- Decision:
  1. **Ship the domain model now; defer the signature to a later ADR.**
     The grant/revoke/expiry/scope logic is pure Kotlin and fully host-testable.
     A signed cert needs a signing-key hierarchy this app does not have, a new
     Firestore collection, and a rules stanza — a trust root, not a feature. The
     blueprint asks for both; doing only the domain half is strictly better than
     doing neither, and it is written so the signature is additive
     (`ConsentCert.signedBy` is already part of the model and may be null only for
     local/synthetic certs).
  2. **A missing cert is DENIED, not permitted.** An allowlist-only app whose
     consent check fails open is not allowlist-only. Absence of a grant must block,
     because the whole safety argument rests on the check being closed by default.
  3. **Prospective only.** A grant never reaches backwards: it takes effect from
     `grantedAtMs` forward and never authorises data that crossed the wire before
     it existed. Self-revocation is permitted; a grantor other than the issuing
     grantee is not (see 4).
  4. **Only the grantee may revoke.** Revoking someone else's consent is not a
     capability this app grants to any device.
  5. **Revocation is sticky.** Once revoked, a cert never un-revokes: a second
     grant is a new cert with a new id, never a resurrection of an old one. This
     is what makes the kill switch trustworthy — there is no ordering window where
     a stale grant outlives a revoke.
  6. **Scope is checked at the point of use**, not once at startup, so a grant for
     `call` cannot silently authorise `photo`.
- Consequences:
  - `com.calldad.consent.ConsentCert` + `ConsentScope` + `ConsentDecision` are pure
    domain types with no Firebase or Android imports, so every rule below is host-
    tested with synthetic ids only (`DAD_TEST` / `KID_TEST`).
  - `ConsentDecision` returns a kid-safe reason enum, never a raw string, so a
    denied state cannot leak a UID or a timestamp onto a 6-year-old's screen
    (same reasoning as `CallViewModel.fail`).
  - **NOT YET WIRED.** Nothing calls this yet. Until the store and rules land, the
    model is inert, and `RULES.md` §1.7's allowlist law continues to be enforced by
    the pair-scoped Firestore rules (ADR-015) — which is a real boundary, just a
    coarser one (one contact, whole-app scope). Recording that honestly rather than
    implying the kill switch ships today.
- Alternatives rejected:
  - *Fail open when no cert exists.* Rejected: inverts the allowlist posture and
    makes the check decorative in exactly the case it exists for.
  - *Sign from the start.* Rejected as sequencing, not merit — a new trust root
    with no operator custody story is worse than a documented, tested domain model.
  - *Reuse `pairings/{uid}` as the consent record.* Rejected: that doc is a
    liveness handshake, cleared on expiry, and mixing consent into it would make
    consent vanish whenever the handshake aged out.