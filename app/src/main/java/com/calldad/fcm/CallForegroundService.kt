// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// fcm/CallForegroundService.kt — killed/background-app ringing service
// Location: app/src/main/java/com/calldad/fcm/CallForegroundService.kt
package com.calldad.fcm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.calldad.CallDadApplication
import com.calldad.MainActivity
import com.calldad.R
import com.calldad.audio.CallAudioManager
import com.calldad.data.signaling.CallRoom
import com.calldad.pairing.SecurePeerStore
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service of type `phoneCall`, started by [CallMessagingService]
 * when a push arrives while the app is not on screen.
 *
 * ORDER MATTERS: startForeground() runs FIRST, synchronously in
 * onStartCommand (the FGS-start deadline is strict and a network read
 * before it crashed the app). Only then is the ring validated against the
 * PAIRED room (allowlist: a push for any other room is dropped). The
 * service then watches the room and removes itself the moment the ring
 * stops being live: answered, declined, cancelled, or 60s timeout.
 */
class CallForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchJob: Job? = null
    private var registration: ListenerRegistration? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            shutDown()
            return START_NOT_STICKY
        }

        val promoted = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildIncomingCallNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL else 0
            )
        }.isSuccess
        if (!promoted) {
            WebRtcLog.transition("FGS startForeground rejected")
            stopSelf()
            return START_NOT_STICKY
        }
        WebRtcLog.transition("FGS started (phoneCall type)")

        val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val seq = intent?.getIntExtra(EXTRA_SEQ, -1) ?: -1
        watchJob?.cancel()
        watchJob = scope.launch { validateAndWatch(callId, seq) }
        return START_NOT_STICKY
    }

    /**
     * "We could not check" must not be treated as "this is not for you."
     * A 3s DataStore read on a cold process competes with FirebaseApp init and
     * auth restore in the same window, and the timeout logged identically to a
     * spoof — so one slow cold start silently dropped a real ring. We are
     * already a foreground service here; there is no reason to bail in 3s.
     */
    private suspend fun waitForAuthUid(): String? = withTimeoutOrNull(AUTH_WAIT_MS) {
        var uid = FirebaseAuth.getInstance().currentUser?.uid
        while (uid == null) {
            delay(200)
            uid = FirebaseAuth.getInstance().currentUser?.uid
        }
        uid
    }

    private suspend fun waitForPeerUid(): String? = withTimeoutOrNull(AUTH_WAIT_MS) {
        SecurePeerStore(applicationContext).observePeerUid().first { it != null }
    }

    private suspend fun validateAndWatch(callId: String, seq: Int) {
        // A 3s DataStore read on a cold process is not generous: it competes
        // with FirebaseApp init and auth restore in the same window, and a
        // timeout was indistinguishable from a spoof in the log — so a single
        // slow cold start silently dropped a real ring. We are already a
        // foreground service here; there is no reason to bail in 3 seconds.
        val ownUid = waitForAuthUid()
        val peerUid = waitForPeerUid()
        if (ownUid == null || peerUid == null) {
            WebRtcLog.transition("FGS ring dropped: identity unresolved")
            shutDown()
            return
        }
        val pairedRoom = CallRoom.idFor(ownUid, peerUid)
        if (pairedRoom == null || pairedRoom != callId) {
            // Now this IS a genuine mismatch: we know who we are and who we
            // are paired with, and the room is not ours.
            WebRtcLog.transition("FGS ring for unpaired room dropped")
            shutDown()
            return
        }

        CallAudioManager.startRinging(applicationContext, CallAudioManager.OWNER_SERVICE)

        registration?.remove()
        registration = FirebaseFirestore.getInstance()
            .collection("calls").document(callId)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val live = snap != null && snap.exists() &&
                    snap.getString("status") == "RINGING" &&
                    snap.getString("calleeUid") == ownUid &&
                    (seq < 0 || snap.getLong("seq")?.toInt() == seq)
                if (!live && snap?.metadata?.isFromCache == false) {
                    WebRtcLog.transition("FGS ring no longer live")
                    shutDown()
                }
            }

        delay(RING_TIMEOUT_MS)
        WebRtcLog.transition("FGS ring timed out")
        shutDown()
    }

    private fun shutDown() {
        CallAudioManager.stopRinging(CallAudioManager.OWNER_SERVICE)
        registration?.remove()
        registration = null
        watchJob?.cancel()
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        registration?.remove()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildIncomingCallNotification(): Notification {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_INCOMING_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, REQUEST_CODE_FSI, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CallDadApplication.CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(getString(R.string.incoming_call_title))
            .setContentText(getString(R.string.incoming_call_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .build()
    }

    companion object {
        const val ACTION_INCOMING_CALL = "com.calldad.INCOMING_CALL"
        private const val ACTION_DISMISS = "com.calldad.DISMISS_INCOMING_CALL"
        private const val EXTRA_CALL_ID = "callId"
        private const val EXTRA_SEQ = "seq"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE_FSI = 2001
        private const val RING_TIMEOUT_MS = 60_000L
        private const val AUTH_WAIT_MS = 15_000L

        fun startIncomingCall(context: Context, callId: String, seq: Int) {
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_SEQ, seq)
            context.startForegroundService(intent)
        }

        /** Removes the ring notification if it is up (answered/declined in-app). */
        fun dismiss(context: Context) {
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, CallForegroundService::class.java).setAction(ACTION_DISMISS)
                )
            }
        }

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutDown()
        super.onTaskRemoved(rootIntent)
    }
}
