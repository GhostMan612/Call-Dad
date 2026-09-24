/**
 * Pure decision: does this room write start a ring that needs a push?
 * Kept free of Firebase imports so functions/ring.test.js runs with plain node.
 *
 * Rings on a NEW generation entering RINGING: the room's first-ever
 * write (create), a seq bump (a new call attempt), or RINGING entered
 * from any other status. Metadata echoes within a ringing generation
 * (ICE trickle) never re-push.
 */
function ringTarget(callId, before, after) {
  if (!after || after.status !== "RINGING") return null;
  const members = String(callId).split("_");
  if (members.length !== 2) return null;
  const { callerUid, calleeUid, seq } = after;
  if (!members.includes(callerUid) || !members.includes(calleeUid)) return null;
  if (callerUid === calleeUid) return null;
  const newGeneration = !before ||
    before.seq !== seq ||
    before.status !== "RINGING";
  if (!newGeneration) return null;
  return { calleeUid, seq: Number.isInteger(seq) ? seq : -1 };
}

module.exports = { ringTarget };
