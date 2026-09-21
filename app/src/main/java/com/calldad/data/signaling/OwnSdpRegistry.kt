// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/OwnSdpRegistry.kt — Phase 11: self-ring suppression
// (static-room edition; replaces callId-based OwnCallRegistry, deleted)
// Location: app/src/main/java/com/calldad/data/signaling/OwnSdpRegistry.kt
package com.calldad.data.signaling

/**
 * Process-scoped record of offer SDPs published by THIS device.
 *
 * The static room carries no sender identity, so a phone that just rang
 * would hear its own echo (double-call/hangup races — device-proven across
 * Phases 4–10). Both the Home ring listener and the incoming check skip
 * marked SDPs.
 *
 * Full SDP strings (not hashes): a handful per session, collision-free.
 * No context, no storage dep.
 */
object OwnSdpRegistry {
    private val published = mutableSetOf<String>()

    @Synchronized
    fun markPublished(sdp: String) {
        published.add(sdp)
        if (published.size > 8) published.clear()
    }

    @Synchronized
    fun isOwn(sdp: String): Boolean = published.contains(sdp)
}
