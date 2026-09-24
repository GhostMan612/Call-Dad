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

    companion object {
        const val CHANNEL_INCOMING_CALL = "incoming_call_v2"
        private const val LEGACY_CHANNEL_INCOMING_CALL = "incoming_call"
    }
}
