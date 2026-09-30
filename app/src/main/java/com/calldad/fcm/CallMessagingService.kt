// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// fcm/CallMessagingService.kt — token-targeted ring receiver (ADR-015)
// Location: app/src/main/java/com/calldad/fcm/CallMessagingService.kt
package com.calldad.fcm

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.calldad.CallDadApplication
import com.calldad.MainActivity
import com.calldad.R
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CallMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "incoming_call") return

        if (AppVisibility.isForeground) {
            WebRtcLog.transition("FCM ring skipped: app on screen, room listener rings")
            return
        }

        val callId = message.data["callId"].orEmpty()
        val seq = message.data["seq"]?.toIntOrNull() ?: -1
        if (callId.isEmpty()) return

        // A high-priority FCM may still be DOWNGRADED to normal by FCM, and a
        // downgraded message legally cannot start a foreground service. That
        // used to be a silent permanent no-op: one log line, indistinguishable
        // from a permissions failure, and the giant Call Dad button simply did
        // nothing, forever.
        //
        // There is deliberately no explicit RemoteMessage.getPriority() check:
        // the accessor's type is not stable across the firebase-messaging
        // versions in this catalog, and the exception handlers below already
        // catch the downgrade observably. Firing a probe purely to read a
        // priority is not worth a version-fragile compile dependency.
        try {
            CallForegroundService.startIncomingCall(applicationContext, callId, seq)
        } catch (t: SecurityException) {
            WebRtcLog.transition("Foreground service start denied by platform")
            postHeadsUpFallback(callId, seq)
        } catch (t: IllegalStateException) {
            WebRtcLog.transition("Foreground service start not allowed right now")
            postHeadsUpFallback(callId, seq)
        } catch (t: Throwable) {
            WebRtcLog.transition("Foreground service start failed")
            postHeadsUpFallback(callId, seq)
        }
    }

    /**
     * A notification-payload-free data message cannot show its own
     * notification, so when the FGS path is unavailable we post a heads-up
     * ourselves. Ringing is lost in this path, but the kid gets a visible,
     * tappable "Dad is calling" instead of silence.
     */
    private fun postHeadsUpFallback(callId: String, seq: Int) {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = ACTION_RING_NOTIFICATION
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_SEQ, seq)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(
                this,
                REQUEST_CODE_FALLBACK,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            nm.notify(
                NOTIFICATION_ID_FALLBACK,
                NotificationCompat.Builder(this, CallDadApplication.CHANNEL_INCOMING_CALL)
                    .setSmallIcon(R.drawable.ic_call)
                    .setContentTitle("Incoming call")
                    .setContentText("Tap to see who's calling")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build()
            )
        }
    }

    @Deprecated("FCM registration tokens stay until the FID-based Admin SDK migration.")
    override fun onNewToken(token: String) {
        WebRtcLog.transition("FCM token refreshed")
        PushTokenRegistrar.save(token)
    }

    private companion object {
        const val ACTION_RING_NOTIFICATION = "com.calldad.RING_NOTIFICATION"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_SEQ = "seq"
        const val NOTIFICATION_ID_FALLBACK = 1002
        const val REQUEST_CODE_FALLBACK = 2002
    }
}
