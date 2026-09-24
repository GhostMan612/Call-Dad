// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/SignalingModels.kt
// Location: app/src/main/java/com/calldad/data/signaling/SignalingModels.kt
package com.calldad.data.signaling

/** The two SDP exchange roles permitted by the Firestore schema. */
enum class SdpType {
    OFFER,
    ANSWER;

    companion object {
        /** Lenient parser — unknown / null wire values become `null`, never a crash. */
        fun fromWire(value: String?): SdpType? = when (value?.uppercase()) {
            OFFER.name -> OFFER
            ANSWER.name -> ANSWER
            else -> null
        }
    }
}

/** The value carried by the call-room document. */
data class SessionDescription(
    val type: SdpType,
    val sdp: String
)

/**
 * A single trickled ICE candidate.
 * `serverUrl` may be null for host candidates; `sdpMid` / `sdpMLineIndex`
 * are nullable because some non-standard candidates omit them.
 */
data class IceCandidate(
    val sdpCandidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val serverUrl: String? = null
)

/**
 * Pair-scoped call rooms (ADR-015). The room id is the two paired UIDs,
 * sorted and joined with '_', so both phones derive the same id with no
 * lookup and the Firestore rules can authorize by `callId.split('_')`.
 */
object CallRoom {

    /**
     * On first attach, a ring older than this is an abandoned attempt and
     * is never shown. Generous vs NO_ANSWER_MS: a live caller ends its own
     * ring at 45s, so only a crashed caller leaves one behind.
     */
    const val RING_FRESH_MS = 90_000L

    /** How long an outgoing ring waits before "No answer yet". */
    const val NO_ANSWER_MS = 45_000L

    fun idFor(uidA: String, uidB: String): String? {
        if (uidA.isBlank() || uidB.isBlank() || uidA == uidB) return null
        if ('_' in uidA || '_' in uidB) return null
        return listOf(uidA, uidB).sorted().joinToString("_")
    }

    fun members(roomId: String): List<String> = roomId.split('_')

    /**
     * True when a RINGING doc is recent enough to ring for. Unknown
     * timestamps are not fresh: a ring nobody can date is never shown.
     * Clock skew tolerance: a timestamp slightly in the future is fresh.
     */
    fun isFreshRing(updatedAtMs: Long?, nowMs: Long): Boolean {
        if (updatedAtMs == null) return false
        return nowMs - updatedAtMs <= RING_FRESH_MS
    }
}
