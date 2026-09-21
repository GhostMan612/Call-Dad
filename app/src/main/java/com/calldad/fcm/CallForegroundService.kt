// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// fcm/CallForegroundService.kt — Phase 11: topic-driven ringing service
// Location: app/src/main/java/com/calldad/fcm/CallForegroundService.kt
package com.calldad.fcm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.calldad.CallDadApplication
import com.calldad.MainActivity
import com.calldad.R
import com.calldad.data.signaling.CallStatus
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Foreground service of type `phoneCall`, started by the topic receiver.
 *
 * ORDER MATTERS: startForeground() runs FIRST with a static notification
 * (the FGS-start timeout is strict — no network on that path), and only
 * then is the static room checked. Not RINGING (or unreadable) →
 * stopSelf(): a stale push evaporates instead of stranding a phantom
 * ring. The full-screen intent carries the ACTION only — MainActivity
 * routes to the overlay, which validates the room itself.
 *
 * REQUIRES in the manifest:
 *   android:foregroundServiceType="phoneCall"
 *   FOREGROUND_SERVICE_PHONE_CALL
 *   MANAGE_OWN_CALLS
 */
class CallForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildIncomingCallNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            WebRtcLog.transition("FGS started (phoneCall type)")
        } catch (t: Throwable) {
            WebRtcLog.transition("FGS startForeground rejected")
            stopSelf()
            return START_NOT_STICKY
        }

        // Async room check: stale push (caller hung up already) → stop.
        scope.launch {
            val ringing = try {
                val snap = FirebaseFirestore.getInstance()
                    .collection("calls")
                    .document("family_channel")
                    .get()
                    .await()
                snap.exists() &&
                    CallStatus.fromWire(snap.getString("status")) == CallStatus.RINGING
            } catch (t: Throwable) {
                false
            }
            if (!ringing) {
                WebRtcLog.transition("Stale push — no live ring")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildIncomingCallNotification(): Notification {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_INCOMING_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, REQUEST_CODE_FSI, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CallDadApplication.CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Dad is calling")
            .setContentText("Tap to answer")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .build()
    }

    companion object {
        const val ACTION_INCOMING_CALL = "com.calldad.INCOMING_CALL"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE_FSI = 2001

        fun startIncomingCall(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
