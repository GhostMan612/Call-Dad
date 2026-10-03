// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// fcm/PushTokenRegistrar.kt — this device's FCM token → users/{uid}
// Location: app/src/main/java/com/calldad/fcm/PushTokenRegistrar.kt
package com.calldad.fcm

import com.calldad.webrtc.WebRtcLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Stores this device's push token in `users/{uid}` (owner-only by rules).
 * The Cloud Function reads it with admin rights to ring exactly the
 * callee: no broadcast topic that any install could subscribe to.
 * The token itself is never logged.
 */
object PushTokenRegistrar {

    /**
     * A token that arrived before auth did, held until it can be written.
     *
     * `save()` used to be a bare `return` when `currentUser` was null. That is
     * not a neutral no-op: the token is then never written, so the Cloud Function
     * finds no `fcmToken` and skips the push with a log line the app can never
     * see. Recovery depended on the child happening to launch the app again.
     *
     * The sequence that lost rings was: a token rotation arrives while Firebase
     * auth is still restoring; the write is dropped; the OLD token is deleted
     * server-side as stale on the next ring; and from then on EVERY ring is
     * skipped with no registered device. A phone not opened for days stops being
     * callable, silently.
     *
     * So the token is remembered in memory and flushed as soon as auth resolves.
     * In-memory rather than DataStore on purpose: a token is not worth a disk
     * write and a migration, and the gap it covers is the process lifetime. If
     * the process dies first, the next `refresh()` at launch re-fetches anyway.
     */
    @Volatile
    private var pending: String? = null

    @Suppress("DEPRECATION")
    fun refresh() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> if (!token.isNullOrBlank()) save(token) }
            .addOnFailureListener { WebRtcLog.transition("FCM token fetch failed") }
    }

    fun save(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            // Logged distinctly from a Firestore failure, because the two need
            // opposite responses: this one is "not yet", that one is "denied".
            pending = token
            WebRtcLog.transition("FCM token held: auth not ready")
            // A single self-removing listener. It has to detach itself once auth
            // resolves, so the reference is held in a `lateinit var` rather than
            // a `val`: a val cannot name itself inside its own initializer.
            lateinit var authListener: FirebaseAuth.AuthStateListener
            authListener = FirebaseAuth.AuthStateListener { auth ->
                val ready = auth.currentUser?.uid
                if (ready != null) {
                    val held = pending
                    pending = null
                    // `removeAuthStateListener` takes the LISTENER, not the auth
                    // object, and only when the listener actually fired — a
                    // leaked auth listener on a process that outlives many rings
                    // is a wakeup cost for nothing.
                    FirebaseAuth.getInstance().removeAuthStateListener(authListener)
                    if (held != null) write(ready, held)
                }
            }
            FirebaseAuth.getInstance().addAuthStateListener(authListener)
            return
        }
        write(uid, token)
    }

    private fun write(uid: String, token: String) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(
                mapOf("fcmToken" to token, "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            .addOnSuccessListener { WebRtcLog.transition("FCM token registered") }
            .addOnFailureListener { WebRtcLog.transition("FCM token register failed") }
    }

    /** One-time cleanup: stop receiving the retired broadcast topic. */
    fun leaveLegacyTopic() {
        runCatching { FirebaseMessaging.getInstance().unsubscribeFromTopic(LEGACY_TOPIC) }
    }

    private const val LEGACY_TOPIC = "incoming_calls"
}

/** Whether an activity of this app is started (on screen). Main-thread writes. */
object AppVisibility {
    @Volatile
    var isForeground: Boolean = false
}
