// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/OwnCallRegistry.kt — Phase 5: self-ring suppression
// Location: app/src/main/java/com/calldad/data/signaling/OwnCallRegistry.kt
package com.calldad.data.signaling

/**
 * Process-scoped record of callIds published by THIS device.
 *
 * Replaces OwnOfferRegistry (deleted): per-call rooms carry UUIDs, so the
 * Home ring listener and any re-entry skip our own rings by id, not by SDP.
 * Same caveats: process-scoped (an app kill with a live ring can re-ring
 * once), negligible window, no context, no storage dep. FCM targeting
 * (Phase 6 token plumbing) supersedes this.
 */
object OwnCallRegistry {
    private val published = mutableSetOf<String>()

    @Synchronized
    fun markPublished(callId: String) {
        published.add(callId)
        if (published.size > 8) published.clear()
    }

    @Synchronized
    fun isOwn(callId: String): Boolean = published.contains(callId)
}
