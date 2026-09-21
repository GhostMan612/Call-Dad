// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// CallDadApplication.kt — Phase 5: auth + notification channel
// Location: app/src/main/java/com/calldad/CallDadApplication.kt
package com.calldad

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

class CallDadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        createIncomingCallChannel()
        signInAnonymously()
        subscribeToRingTopic()
    }

    /**
     * Silent anonymous auth. Runs on every cold start; Firebase
     * short-circuits if the cached anonymous user is still valid.
     * This must complete before any Firestore write or the security
     * rules will reject the request.
     *
     * Non-KTX factory by tree law (KTX artifacts were removed in Phase 2).
     */
    private fun signInAnonymously() {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            WebRtcLog.transition("Anonymous auth: already signed in")
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener {
                WebRtcLog.transition("Anonymous auth: signed in")
            }
            .addOnFailureListener {
                WebRtcLog.transition("Anonymous auth: FAILED")
            }
    }

    /**
     * Notification channel for the incoming-call full-screen intent.
     * IMPORTANCE_HIGH is required for the heads-up to fire.
     * The channel MUST exist before the first notification is posted.
     */
    private fun createIncomingCallChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_INCOMING_CALL,
            "Incoming Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming Call Dad calls"
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_INCOMING_CALL = "incoming_call"
        const val RING_TOPIC = "incoming_calls"
    }

    /**
     * Topic wakeup subscription — BOTH flavors (executor deviation from the
     * prompt, ADR-013). The prompt subscribes the child only, but our
     * product direction is child→parent: Dad's phone must wake when the kid
     * rings. The topic carries no routing (by design), so over-delivery is
     * harmless: a phone that isn't being rung bounces home on room check.
     * Killed-app wakeup still needs a launched-once app (FCM limitation).
     */
    private fun subscribeToRingTopic() {
        FirebaseMessaging.getInstance()
            .subscribeToTopic(RING_TOPIC)
            .addOnSuccessListener {
                WebRtcLog.transition("FCM topic subscribed")
            }
            .addOnFailureListener {
                WebRtcLog.transition("FCM topic subscription failed")
            }
    }
}
