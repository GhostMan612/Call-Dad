const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { logger } = require("firebase-functions");
const { ringTarget } = require("./ring");
const { clipTarget } = require("./clip");

initializeApp();

/**
 * Sends a DATA-ONLY high-priority message to the callee's own device
 * token (users/{calleeUid}.fcmToken). No broadcast topic: nobody else can
 * subscribe and learn when this family calls. The payload is only the
 * room id and generation; the app validates both against its pairing.
 */
exports.onCallRoomWritten = onDocumentWritten("calls/{callId}", async (event) => {
  const callId = event.params.callId;
  const before = event.data?.before?.exists ? event.data.before.data() : null;
  const after = event.data?.after?.exists ? event.data.after.data() : null;
  const target = ringTarget(callId, before, after);
  if (!target) return;
  await pushToCallee(target.calleeUid, "incoming_call", {
    callId,
    seq: String(target.seq),
  });
});

/**
 * K12: a PTT clip used to be visible only when the app happened to be open on
 * the receiving phone. The clip itself was safe and durable in Firestore, but a
 * parent who sent "goodnight" into a locked phone got no indication at all,
 * which is indistinguishable from the message having failed.
 *
 * A NORMAL-priority, data-only push, deliberately NOT a ring: a voice message
 * arriving at 2am must not wake the house. It carries no audio and no content,
 * only the fact that something is waiting, so nothing about the message is
 * exposed to FCM or to a lock screen.
 */
exports.onPttClipWritten = onDocumentWritten(
  "calls/{callId}/ptt/{clipId}",
  async (event) => {
    const callId = event.params.callId;
    const before = event.data?.before?.exists ? event.data.before.data() : null;
    const after = event.data?.after?.exists ? event.data.after.data() : null;
    const target = clipTarget(callId, before, after);
    if (!target) return;
    await pushToCallee(target.peerUid, "ptt_clip", { callId });
  },
);

/**
 * Sends a data-only message to one device's own token
 * (users/{uid}.fcmToken). No broadcast topic: nobody else can subscribe and
 * learn when this family calls. A stale token is deleted rather than retried
 * forever, so one uninstalled phone cannot block the pair's wakeups.
 */
async function pushToCallee(calleeUid, type, extra) {
  const db = getFirestore();
  const userRef = db.collection("users").doc(calleeUid);
  const user = await userRef.get();
  const token = user.exists ? user.get("fcmToken") : null;
  if (!token) {
    logger.info("Push skipped: no registered device");
    return;
  }

  // A voice message is a notification, not an alarm: normal priority, so it
  // never becomes a wakeup the phone must grant.
  //
  // THE TTL IS PER-TYPE, and it was not. Both messages got `30 * 1000`, so a
  // RING was discarded after 30 seconds: a parent pressing Call Dad while the
  // child's phone sat in a 31-second dead zone got nothing at all, and the call
  // rang out to "No answer yet" — indistinguishable from the kid ignoring it. A
  // ring is an alarm-grade event; it gets an hour to land. A PTT nudge keeps 30s
  // because the clip sits in Firestore indefinitely and a late heads-up would
  // be noise about a message that is no longer new.
  const priority = type === "ptt_clip" ? "normal" : "high";
  const ttl = type === "ptt_clip" ? 30 * 1000 : 3600 * 1000;
  // collapse_key makes a retry replace the pending message for the same call
  // rather than stacking a second notification on the child's screen.
  const collapseKey = type === "incoming_call" ? extra.callId : undefined;
  try {
    await getMessaging().send({
      token,
      data: { type, ...extra },
      android: { priority, ttl, ...(collapseKey ? { collapseKey } : {}) },
    });
    logger.info("Push sent: " + type);
  } catch (err) {
    const code = err?.errorInfo?.code || err?.code;
    if (code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token") {
      // The ONLY failure that is genuinely permanent. Deleting the token is
      // correct here and only here.
      await userRef.update({ fcmToken: FieldValue.delete() });
      logger.info("Stale device token removed");
    } else {
      // Everything else — a quota blip, a transient 5xx, a partial outage — is
      // RETRIED by re-throwing. This used to be swallowed, so the trigger's
      // promise resolved, Firestore considered the event handled, and a ring was
      // lost permanently with no dead-letter and no retry. A missed ring is a
      // child whose parent tried to call them and nothing happened.
      logger.error("Push failed, retrying", code);
      throw err;
    }
  }
}
