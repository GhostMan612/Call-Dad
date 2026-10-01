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

    companion object {
        const val CHANNEL_INCOMING_CALL = "incoming_call_v2"
        const val CHANNEL_PTT_MESSAGE = "ptt_message_v1"
        private const val LEGACY_CHANNEL_INCOMING_CALL = "incoming_call"
    }
}
