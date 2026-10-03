// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// CallDadApplication.kt — auth + notification channel + push token
// Location: app/src/main/java/com/calldad/CallDadApplication.kt
package com.calldad

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import com.calldad.fcm.PushTokenRegistrar
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class CallDadApplication : Application() {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var authRetryMs = 5_000L

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        createIncomingCallChannel()
        createMessageChannel()
        createPendingRingChannel()
        PushTokenRegistrar.leaveLegacyTopic()
        signInAnonymously()
    }

    /**
     * Silent anonymous auth. Firebase short-circuits when the cached user
     * is still valid. Offline first launches retry with backoff instead of
     * leaving the app signed out (and unable to call) until a restart.
     */
    private fun signInAnonymously() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            WebRtcLog.transition("Anonymous auth: already signed in")
            PushTokenRegistrar.refresh()
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener {
                WebRtcLog.transition("Anonymous auth: signed in")
                PushTokenRegistrar.refresh()
            }
            .addOnFailureListener {
                WebRtcLog.transition("Anonymous auth: FAILED, retrying")
                mainHandler.postDelayed(::signInAnonymously, authRetryMs)
                authRetryMs = (authRetryMs * 2).coerceAtMost(60_000L)
            }
    }

    /**
     * Channel for the incoming-call full-screen notification. Silent by
     * design: CallAudioManager is the single ringer (looping ringtone +
     * vibration), so the channel must not add its own one-shot sound.
     */
    private fun createIncomingCallChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(LEGACY_CHANNEL_INCOMING_CALL)
        val channel = NotificationChannel(
            CHANNEL_INCOMING_CALL,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming calls from your family"
            setShowBadge(true)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Channel for PUSH-TO-TALK voice messages. These are NOT calls: a waiting
     * voice message must never ring, buzz, or take over the screen. It is
     * IMPORTANCE_LOW with no sound and no vibration, so on Android 8+ the
     * channel's importance is what the phone obeys and the notification's own
     * priority is ignored. The K12 bug was a voice message built on the
     * IMPORTANCE_HIGH call channel, which rang at full volume on a locked
     * phone. Playback is the child's explicit tap.
     */
    private fun createMessageChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_PTT_MESSAGE,
            "Voice Messages",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "A family member left you a voice message"
            setShowBadge(true)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * The channel the foreground service is promoted on BEFORE consent is known.
     *
     * The FGS-start deadline is strict — `startForeground` must run before any
     * network read, so the kill switch cannot be consulted first. Posting on the
     * IMPORTANCE_HIGH call channel at that moment meant a revoked child's phone
     * lit up and vibrated for a call their grown-up had switched off, regardless of
     * the gate that ran a few hundred milliseconds later.
     *
     * IMPORTANCE_MIN, no sound, no vibration, and nothing visible on the lock
     * screen. If consent allows, the same notification id is reposted on the call
     * channel and behaves normally; if it denies, the child never sees or hears
     * anything at all.
     *
     * Its own id, and that matters: the platform ignores importance and sound
     * changes to an EXISTING channel, so trying to reuse the call channel at a low
     * priority would silently keep it high-importance and defeat this entirely.
     */
    private fun createPendingRingChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RING_PENDING_CONSENT,
                "Ringing (checking permission)",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
        )
    }

    companion object {
        const val CHANNEL_INCOMING_CALL = "incoming_call_v2"
        const val CHANNEL_PTT_MESSAGE = "ptt_message_v1"
        const val CHANNEL_RING_PENDING_CONSENT = "ring_pending_consent_v1"
        private const val LEGACY_CHANNEL_INCOMING_CALL = "incoming_call"
    }
}
