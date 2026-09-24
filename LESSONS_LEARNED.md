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

## Build / Gradle
- Product flavors rename every variant task: there is no `testDebugUnitTest` or `lintDebug`,
  only `testParentDebugUnitTest`, `lintChildDebug`, etc. Gate commands must name them.

## WebRTC (org.webrtc)
- Dispose order is load-bearing: `peerConnection.dispose()` first (it disposes the
  transceivers and the remote track's Java wrapper, detaching renderer sinks), then local
  tracks/sources/capturer, then the factory. Freeing the factory under a still-attached
  remote track is the hangup SIGSEGV.
- A disposed client is not reusable. Build a new one per call attempt.
- `addIceCandidate` before `setRemoteDescription` is rejected. Buffer remote candidates.
  Queue local ones until your own SDP is published, or they land on the previous call.

## Compose / ViewModels
- `viewModel()` without an explicit `viewModelStoreOwner` is scoped to the nav back-stack
  entry, whatever the accessor's comment claims. Pass the activity for app-wide sessions.

## Firestore rules
- Authorize from the document id when the id can carry the ACL (`callId.split('_')`):
  no `resource.data` read, so transactions can pre-read missing docs safely.
- A fixed, well-known document id is readable by every signed-in anonymous user;
  "unguessable" needs an id that is actually unguessable or rule-bound.

## Foreground services
- `startForeground()` must run synchronously in `onStartCommand`, before any I/O.
  Validate afterwards and `stopForeground(REMOVE)` if the reason evaporated.
