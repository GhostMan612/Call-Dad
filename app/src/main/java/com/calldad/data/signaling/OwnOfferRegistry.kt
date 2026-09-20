// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/OwnOfferRegistry.kt
// Location: app/src/main/java/com/calldad/data/signaling/OwnOfferRegistry.kt
package com.calldad.data.signaling

/**
 * Process-scoped record of OFFER SDPs published by THIS device.
 *
 * WHY: the room is shared and the Home listener + answer path cannot tell
 * our own ringback from Dad's call. Without this, hanging up and calling
 * again makes the phone hear its own OFFER and "call itself" (device-proven),
 * and answering can fetch our own stale offer into a ghost InCall.
 *
 * Full SDP strings (not hashes): a handful per session, collision-free.
 * Process-scoped (not persisted): a stale self-offer surviving an app kill
 * could ring once on next launch — wiped by the next caller's pre-publish
 * teardown, so the window is negligible. No context, no storage dep.
 */
object OwnOfferRegistry {
    private val published = mutableSetOf<String>()

    @Synchronized
    fun markPublished(sdp: String) {
        published.add(sdp)
        if (published.size > 8) published.clear()
    }

    @Synchronized
    fun isOwn(sdp: String): Boolean = published.contains(sdp)
}
