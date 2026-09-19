# BP-05 — Hardening: parent gate, consent, at-rest crypto, kid-UX audit (Phase 5)

Goal: v0.1 shippable to kid device.

1. Parent gate (adapt `DualKeyGate`): contact-add/settings require parent auth (biometric/PIN on Dad device or setup flow); kid flow provably cannot add/escalate (unit + manual audit).
2. Consent cert (`consent.py` model): Dad-grants-Kid `[call,text,photo]` + expiry; revocation blocks all comms + shows kind UI. Unit: expired/revoked → blocked.
3. At-rest crypto per ADR-003: SQLCipher (passphrase wrapped by Keystore) or deferred with justification.
4. Kid-UX audit (docs/kid-safe-ux.md sheet): ≥96dp primary target, contrast, one-action/screens, no-escape (no browser/store/settings), loud ring + auto-reconnect, missed-call callback card.
5. Release plan: operator provisions `call-dad` keystore later; document signing + versioning; no secrets in repo.

## Gate
G5: all above + human device proof on Moto G + BLU View 5 (call/chat/photo/video + airplane/matrix + no-escape walkthrough).
