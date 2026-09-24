// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// fcm/CallMessagingService.kt — token-targeted ring receiver (ADR-015)
// Location: app/src/main/java/com/calldad/fcm/CallMessagingService.kt
package com.calldad.fcm

import com.calldad.webrtc.WebRtcLog
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CallMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]
        WebRtcLog.transition("FCM received: type=$type")
        if (type != "incoming_call") return

        if (AppVisibility.isForeground) {
            WebRtcLog.transition("FCM ring skipped: app on screen, room listener rings")
            return
        }

        val callId = message.data["callId"].orEmpty()
        val seq = message.data["seq"]?.toIntOrNull() ?: -1
        if (callId.isEmpty()) return

        try {
            CallForegroundService.startIncomingCall(applicationContext, callId, seq)
        } catch (t: Throwable) {
            WebRtcLog.transition("Foreground service start rejected")
        }
    }

    @Deprecated("FCM registration tokens stay until the FID-based Admin SDK migration.")
    override fun onNewToken(token: String) {
        WebRtcLog.transition("FCM token refreshed")
        PushTokenRegistrar.save(token)
    }
}
