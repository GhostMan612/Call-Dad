/**
 * Pure decision: does this clip write need a "something is waiting" push?
 * Kept free of Firebase imports so functions/clip.test.js runs with plain node.
 *
 * Same trust posture as ring.js: the notification is only ever addressed to the
 * OTHER member of the room, and a document that is not a well-formed clip from
 * that member produces no push at all. An outsider who somehow wrote into the
 * collection must not be able to make a phone buzz.
 *
 * Fires only on ADD, never on update or delete: a clip is immutable once
 * written (firestore.rules denies update), and re-pushing on delete would
 * announce a message that is no longer there.
 */
function clipTarget(callId, before, after) {
  if (!after) return null;
  if (before) return null; // an update or delete, not a new message
  const members = String(callId).split("_");
  if (members.length !== 2) return null;
  const { from, durationMs } = after;
  if (!members.includes(from)) return null;
  const peerUid = members.find((uid) => uid !== from);
  if (!peerUid) return null;
  // A malformed clip is not worth a notification that leads to nothing.
  if (typeof durationMs !== "number" || durationMs <= 0) return null;
  return { peerUid, durationMs };
}

module.exports = { clipTarget };
