const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { logger } = require("firebase-functions");

initializeApp();
const db = getFirestore();

/**
 * Fires when a new call document is created.
 * Sends a DATA-ONLY FCM message to the callee.
 *
 * DATA-ONLY is mandatory: any `notification` key would route the
 * message to the system tray instead of onMessageReceived(), which
 * defeats the killed-app wakeup path.
 */
exports.onCallCreated = onDocumentCreated("calls/{callId}", async (event) => {
  const snap = event.data;
  if (!snap) return;

  const call = snap.data();
  const callId = event.params.callId;
  const calleeUid = call.calleeUid;

  if (!calleeUid) {
    logger.warn("Call has no calleeUid; skipping");
    return;
  }

  // Look up the callee's FCM token.
  const userSnap = await db.collection("users").doc(calleeUid).get();
  const token = userSnap.get("fcmToken");
  if (!token) {
    logger.warn("Callee has no FCM token registered");
    return;
  }

  try {
    await getMessaging().send({
      token: token,
      data: { callId: callId, type: "incoming_call" },
      android: {
        priority: "high",
        ttl: 30 * 1000,          // 30s: stale calls are useless
        // NO `notification` key. Data-only.
      },
    });
    logger.info("Incoming call push sent", { callId });
  } catch (err) {
    logger.error("Push send failed", err);
  }
});
