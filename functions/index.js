const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");
const { logger } = require("firebase-functions");

initializeApp();

/**
 * Fires when calls/family_channel is updated.
 *
 * Sends a DATA-ONLY message to the topic. The payload contains NO
 * routing information. The static document ID IS the route.
 *
 * This function deliberately does NOT look up a per-user FCM token.
 * It broadcasts to a topic. Anyone who knows the topic name can
 * subscribe. The payload is therefore reduced to a single hint.
 */
exports.onFamilyChannelUpdated = onDocumentUpdated(
  "calls/family_channel",
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!before || !after) return;

    // Only fire when status transitions INTO "RINGING".
    if (before.status === "RINGING" || after.status !== "RINGING") {
      return;
    }

    try {
      await getMessaging().send({
        topic: "incoming_calls",
        data: { type: "incoming_call" },   // NO callId. NO seq.
        android: {
          priority: "high",
          ttl: 30 * 1000,          // 30s: stale calls are useless
          // NO notification key. Data-only.
        },
      });
      logger.info("Family channel wakeup sent");
    } catch (err) {
      logger.error("Family channel wakeup failed", err);
    }
  }
);
