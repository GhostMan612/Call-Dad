// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// fcm/CallMessagingService.kt — Phase 11: topic receiver (no routing in payload)
// Location: app/src/main/java/com/calldad/fcm/CallMessagingService.kt
package com.calldad.fcm

import com.calldad.webrtc.WebRtcLog
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CallMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]

        // Never log payload values beyond the type. No callId exists.
        WebRtcLog.transition("FCM received: type=$type")

        if (type != "incoming_call") {
            WebRtcLog.transition("FCM ignored: not an incoming call")
            return
        }

        try {
            CallForegroundService.startIncomingCall(applicationContext)
            WebRtcLog.transition("Foreground service start requested")
        } catch (t: Throwable) {
            // Android 14 can reject FGS starts even with high-priority
            // FCM. Do not crash — the call can still be answered if the
            // user opens the app manually.
            WebRtcLog.transition("Foreground service start rejected")
        }
    }

    @Suppress("DEPRECATION")
    // TODO: Defer to FID-based Admin SDK migration. Requires
    // synchronized Cloud Function rewrite and Firestore schema
    // migration (fcmToken -> fid). Do NOT partial-migrate.
    override fun onNewToken(token: String) {
        // TODO: persist when per-device targeting returns (topic needs no
        // token today). For now, log only — never the token itself.
        WebRtcLog.transition("FCM token refreshed")
    }
}
