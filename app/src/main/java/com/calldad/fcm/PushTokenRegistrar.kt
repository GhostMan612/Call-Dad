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

    @Suppress("DEPRECATION")
    fun refresh() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> if (!token.isNullOrBlank()) save(token) }
            .addOnFailureListener { WebRtcLog.transition("FCM token fetch failed") }
    }

    fun save(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
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
