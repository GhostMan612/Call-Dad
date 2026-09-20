// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// fcm/CallMessagingService.kt — Phase 5: killed-app wakeup receiver
// Location: app/src/main/java/com/calldad/fcm/CallMessagingService.kt
package com.calldad.fcm

import com.calldad.webrtc.WebRtcLog
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CallMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"]
        val callId = data["callId"]

        // Never log the callId or the token. State transitions only.
        WebRtcLog.transition("FCM received: type=$type")

        if (type != "incoming_call" || callId.isNullOrBlank()) {
            WebRtcLog.transition("FCM ignored: not an incoming call")
            return
        }

        try {
            CallForegroundService.startIncomingCall(
                context = applicationContext,
                callId = callId
            )
            WebRtcLog.transition("Foreground service start requested")
        } catch (t: Throwable) {
            // Android 14 can reject FGS starts even with high-priority
            // FCM. Do not crash — the call can still be answered if the
            // user opens the app manually.
            WebRtcLog.transition("Foreground service start rejected")
        }
    }

    override fun onNewToken(token: String) {
        // TODO(Phase 6): write this to users/{uid}.fcmToken via
        // SignalingClient. For Phase 5, log only.
        WebRtcLog.transition("FCM token refreshed")
    }
}
