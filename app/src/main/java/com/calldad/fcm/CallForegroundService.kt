// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// fcm/CallForegroundService.kt — killed/background-app ringing service
// Location: app/src/main/java/com/calldad/fcm/CallForegroundService.kt
package com.calldad.fcm

import android.app.KeyguardManager
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
import com.calldad.consent.ConsentStore
import com.calldad.data.signaling.CallRoom
import com.calldad.pairing.SecurePeerStore
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service of type `phoneCall`, started by [CallMessagingService]
 * when a push arrives while the app is not on screen.
 *
 * ORDER MATTERS, and this was the defect. startForeground() runs FIRST,
 * synchronously in onStartCommand (the FGS-start deadline is strict and a
 * network read before it crashed the app). Validation of the PAIRED room
 * follows, so a push for any other room is dropped. **Then the kill switch is
 * consulted**, then the room is confirmed live FROM THE SERVER, and only then
 * does anything make a sound.
 *
 * That order is the point: this service is the one consent consumer that runs
 * when the app is NOT running, so a gate placed after `startRinging` — or
 * omitted, which is what it was — is a parental control that does not control
 * anything on the path a phone actually takes when it is in a pocket. See
 * [validateAndWatch].
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
                // THE SILENT CHANNEL, ALWAYS. The FGS-start deadline is strict and
                // startForeground must run before any network read, so consent
                // CANNOT be checked first -- and the incoming-call channel is
                // IMPORTANCE_HIGH, which means a notification posted here can
                // sound and buzz. That was a kill-switch hole even with the gate
                // in place: a revoked child's phone lit up and vibrated before
                // `validateAndWatch` ever got to say no.
                //
                // So the promotion uses a channel that cannot make noise. If
                // consent allows, `startRinging()` below reposts the same id on
                // the high-importance channel and the phone behaves normally. If
                // it denies, the child sees nothing at all. No ring, no buzz, no
                // screen — which is what "calling is turned off" has to mean.
                buildIncomingCallNotification(
                    silent = true,
                    callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty(),
                    seq = intent?.getIntExtra(EXTRA_SEQ, -1) ?: -1
                ),                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL else 0
            )
        }.isSuccess
        if (!promoted) {
            // A rejected promote was TOTAL SILENCE: the ring notification could
            // not be posted by definition, and the heads-up fallback in
            // CallMessagingService only wraps `startForegroundService()`, which
            // does NOT throw for a later promote failure. So a child pressing
            // Call Dad would get nothing at all -- no ring, no notification, no
            // trace -- and the parent would conclude the phone was broken.
            //
            // Post the fallback here instead, so the child's phone at least shows
            // an incoming call that can be tapped. It cannot ring loudly (that
            // needs a foreground service), but visible beats silent.
            val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
            val seq = intent?.getIntExtra(EXTRA_SEQ, -1) ?: -1
            WebRtcLog.transition("FGS startForeground rejected; posting fallback")
            runCatching {
                CallMessagingService.postIncomingCallFallback(applicationContext, callId, seq)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        WebRtcLog.transition("FGS started (phoneCall type)")

        val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val seq = intent?.getIntExtra(EXTRA_SEQ, -1) ?: -1
        // Retire the previous ring's resources HERE, on the main thread, before a
        // new coroutine can race the assignment. Doing it inside the coroutine was
        // a leak: if two pushes overlapped, job B could finish its identity waits
        // before job A did, write L2, and then have A overwrite `registration`
        // with L1 -- orphaning L2 for the life of the process. A
        // ListenerRegistration is not owned by the coroutine that made it, so
        // cancelling the job never removed it.
        watchJob?.cancel()
        registration?.remove()
        registration = null
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

        // THE KILL SWITCH, ON THE WAKEUP PATH. This is the gate that was missing.
        //
        // Every other consumer reads a consent scope: CallViewModel at the button,
        // at answer, and on the room listener; ChatViewModel and PhotoViewModel on
        // their downloads; PttViewModel before the mic. This service was the one
        // exception, and it is the exception that matters most, because it is the
        // only path that runs when the app is NOT running. A parent who pressed
        // "Turn everything off" would still have heard their child's phone ring at
        // full volume, with the screen lighting up, for up to 45 seconds.
        //
        // It comes BEFORE startRinging and it fails CLOSED: a probe that errors,
        // times out, or reads from cache DENIES. A wrong deny means the call does
        // not ring and a parent tries again; a wrong allow means the control they
        // believe is on is not.
        if (!ConsentStore().decisionNow(callId, ownUid, peerUid).isGranted) {
            // Publish ENDED so the CALLER is not left ringing out to nobody for 45s
            // either. They are the grantor and may always call, but a call their
            // child cannot answer is a lie to them too.
            runCatching { finishDeniedRing(callId, ownUid, seq) }
            WebRtcLog.transition("FGS ring suppressed: consent denied")
            shutDown()
            return
        }

        // Attach the liveness listener BEFORE ringing, and await a SERVER snapshot
        // rather than assuming the push was honest. A push is only a claim: this
        // device may have received it seconds after the call ended, or may not be
        // able to reach Firestore at all (captive portal, VPN, DNS). Ringing first
        // and checking later is how a phone ends up ringing at full volume in a
        // quiet house for a call that is already over.
        registration?.remove()
        registration = null
        val firstLive = CompletableDeferred<Boolean>()
        registration = FirebaseFirestore.getInstance()
            .collection("calls").document(callId)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val live = snap != null && snap.exists() &&
                    snap.getString("status") == "RINGING" &&
                    snap.getString("calleeUid") == ownUid &&
                    (seq < 0 || snap.getLong("seq")?.toInt() == seq)
                // A cached-only snapshot is not evidence the room exists.
                if (snap?.metadata?.isFromCache == false) {
                    if (live) firstLive.complete(true)
                    else {
                        firstLive.complete(false)
                        WebRtcLog.transition("FGS ring no longer live")
                        shutDown()
                    }
                }
            }

        val confirmed = withTimeoutOrNull(LIVE_CONFIRM_MS) { firstLive.await() } ?: false
        if (!confirmed) {
            WebRtcLog.transition("FGS ring unconfirmed by server; staying silent")
            shutDown()
            return
        }

        // Consent allowed AND the room is confirmed live. NOW the notification
        // becomes a real incoming-call notification, on the high-importance
        // channel, with the full-screen intent — because up to this point the
        // child has seen nothing audible. Same notification id, so this is an
        // update rather than a second notification.
        promoteNotification(callId, seq)
        CallAudioManager.startRinging(applicationContext, CallAudioManager.OWNER_SERVICE)

        delay(RING_TIMEOUT_MS)
        WebRtcLog.transition("FGS ring timed out")
        shutDown()
    }

    /**
     * Re-posts [NOTIFICATION_ID] on the loud call channel. Only reached after
     * consent and liveness have both passed.
     */
    private fun promoteNotification(callId: String, seq: Int) {
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)?.notify(
                NOTIFICATION_ID,
                buildIncomingCallNotification(
                    silent = false,
                    callId = callId,
                    seq = seq
                )
            )
        }
    }

    /**
     * Ends a ring this device refused to answer because consent denied it, so the
     * caller gets a prompt, honest stop instead of a 45-second silence. Generation
     * checked exactly like every other teardown write: a stale push must not be
     * able to cancel a NEWER call.
     */
    private suspend fun finishDeniedRing(callId: String, ownUid: String, seq: Int) {
        val firestore = FirebaseFirestore.getInstance()
        val ref = firestore.collection("calls").document(callId)
        firestore.runTransaction { txn ->
            val snap = txn.get(ref)
            val live = snap.exists() &&
                snap.getString("status") == "RINGING" &&
                snap.getString("calleeUid") == ownUid &&
                (seq < 0 || snap.getLong("seq")?.toInt() == seq)
            if (live) {
                txn.update(ref, mapOf(
                    "status" to "ENDED",
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
            null
        }.await()
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

    /**
     * @param silent true for the mandatory FGS promotion, which MUST NOT make a
     *   sound: consent has not been checked yet at that point, and the call
     *   channel is IMPORTANCE_HIGH. Carries no title, no full-screen intent and
     *   no category, so a revoked child's phone stays dark and silent until
     *   `promoteNotification` says otherwise. false is the real thing, posted
     *   only after consent AND a server-confirmed live room.
     */
    private fun buildIncomingCallNotification(
        silent: Boolean,
        callId: String,
        seq: Int
    ): Notification {
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_INCOMING_CALL
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_SEQ, seq)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPi = PendingIntent.getActivity(
            this, REQUEST_CODE_FSI, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // K21, operator-found: an unconditional full-screen intent is what
        // launches the activity straight over the keyguard, which trapped the
        // grown-up on their own lock screen. Only take the screen over when it
        // is already usable; on a locked phone the notification's CATEGORY_CALL
        // and PRIORITY_MAX still give a full ringscreen entry, and CallAudioManager
        // is still the ringer either way.
        val keyguard = getSystemService(KeyguardManager::class.java)
        val takeOverScreen = keyguard?.isKeyguardLocked != true

        if (silent) {
            return NotificationCompat.Builder(this, CallDadApplication.CHANNEL_RING_PENDING_CONSENT)
                .setSmallIcon(R.drawable.ic_call)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .setContentTitle("")
                .setContentText("")
                .build()
        }

        return NotificationCompat.Builder(this, CallDadApplication.CHANNEL_INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(getString(R.string.incoming_call_title))
            .setContentText(getString(R.string.incoming_call_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, takeOverScreen)
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

        /**
         * How long to wait for a SERVER snapshot confirming the room is really
         * RINGING before giving up on ringing at all.
         *
         * Failing closed here means a missed ring, and that is the correct
         * trade: a ring the child's phone cannot explain is worse than one the
         * parent has to try again. It also covers the captive-portal case, where
         * a push can be delivered over a network that cannot reach Firestore —
         * there the snapshot would only ever come from cache, and a cached
         * snapshot is not evidence the call is still happening.
         */
        private const val LIVE_CONFIRM_MS = 6_000L

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
