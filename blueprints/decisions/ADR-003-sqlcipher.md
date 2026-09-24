# ADR-003 — Room at rest: SQLCipher now vs later

> **MOOT (2026-09-24):** the app has no Room/SQL database (peer store is DataStore, signaling is Firestore). Revisit only if local message storage lands.

- Status: PROPOSED (owner: DeepSeek).
- Context: donor uses SQLCipher 4.5.4 (passphrase wrapped by Keystore) so Room stays readable in background without operator unlock. Adds NDK/binary weight + migration complexity.
- Options: A) SQLCipher from BP-01 (donor-faithful, heavier). B) Plain Room v0.1, SQLCipher in BP-05 (lighter start, migration cost later).
- Recommendation (executor): A if team accepts weight (kid device holds sensitive thread/photo refs); else B with explicit migration ticket.
- Decision: PENDING.
