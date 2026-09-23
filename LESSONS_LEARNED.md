# Lessons Learned

## Android 14 Constraints
- phoneCall FGS requires MANAGE_OWN_CALLS. VoIP apps must use
  microphone FGS.
- FSI demotes to Heads-Up notifications on unlocked Android 14+
  devices.
- ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED throws
  NoSuchMethodError below API 34.
- FGS while-in-use constraints require deferring background FGS
  starts to onResume().

## Firestore Constraints
- serverTimestamp() fires the listener twice: once with
  hasPendingWrites=true and a null timestamp, then again with the
  server value.
- Mobile SDKs use optimistic concurrency. Transactions retry on
  contention.
- Firestore TTL policies delete within 24 hours. Explicit cleanup
  is required for time-sensitive documents.
- Client-side timestamps are vulnerable to clock skew. Bound the
  maximum future value in rules.
- Batch writes are atomic across documents, but are persisted
  offline. A batch write succeeds locally even when the network is
  down. The writes queue and fire when connectivity returns.
- CRITICAL: Firestore rules are NOT filters. A read rejected by
  rules fails the entire transaction with PERMISSION_DENIED. Do not
  protect reads with conditions the client needs to evaluate.
- batch.update() fails with NOT_FOUND if the document does not
  exist. Use set(..., SetOptions.merge()) when the document may not
  exist.

## Doze Mode Constraints
- Doze rate-limits background work to roughly one wake per 9
  minutes. Heartbeats shorter than 10 minutes are throttled.
- Doze suspends network access even with a foreground service.
- The 20-minute stale threshold gives a 2.2x margin against the
  9-minute Doze cadence.
