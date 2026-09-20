// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// fcm/CallForegroundService.kt — Phase 5: ringing foreground service
// Location: app/src/main/java/com/calldad/fcm/CallForegroundService.kt
package com.calldad.fcm

import android.app.Notification
import android.app.NotificationManager
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
import com.calldad.webrtc.WebRtcLog

/**
 * Foreground service of type `phoneCall`.
 *
 * REQUIRES in the manifest:
 *   android:foregroundServiceType="phoneCall"
 *   FOREGROUND_SERVICE_PHONE_CALL
 *   MANAGE_OWN_CALLS          <- one of these two is mandatory
 *                                 on API 34+, or startForeground()
 *                                 throws SecurityException.
 *
 * The full-screen intent, not the notification body, is what wakes the
 * screen. `setFullScreenIntent(intent, true)` where the second arg is
 * `true` means "launch immediately even if the screen is locked".
 */
class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID)
        if (callId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildIncomingCallNotification(callId)

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
        }
        return START_NOT_STICKY
    }

    private fun buildIncomingCallNotification(callId: String): Notification {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_INCOMING_CALL
            putExtra(EXTRA_CALL_ID, callId)
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
        const val EXTRA_CALL_ID = "callId"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE_FSI = 2001

        fun startIncomingCall(context: Context, callId: String) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                putExtra(EXTRA_CALL_ID, callId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
