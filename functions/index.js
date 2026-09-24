const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { logger } = require("firebase-functions");
const { ringTarget } = require("./ring");

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

  const db = getFirestore();
  const userRef = db.collection("users").doc(target.calleeUid);
  const user = await userRef.get();
  const token = user.exists ? user.get("fcmToken") : null;
  if (!token) {
    logger.info("Ring skipped: callee has no registered device");
    return;
  }

  try {
    await getMessaging().send({
      token,
      data: { type: "incoming_call", callId, seq: String(target.seq) },
      android: { priority: "high", ttl: 30 * 1000 },
    });
    logger.info("Ring push sent");
  } catch (err) {
    const code = err?.errorInfo?.code || err?.code;
    if (code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token") {
      await userRef.update({ fcmToken: FieldValue.delete() });
      logger.info("Stale device token removed");
    } else {
      logger.error("Ring push failed", code);
    }
  }
});
