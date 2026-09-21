// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/SignalingModels.kt
// Location: app/src/main/java/com/calldad/data/signaling/SignalingModels.kt
package com.calldad.data.signaling

/** Offers older than this are abandoned rings, never answered. */
const val OFFER_STALE_MS = 60_000L

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

/** The value carried by the single call-room document. */
data class SessionDescription(
    val type: SdpType,
    val sdp: String,
    /** Writer's clock at publish; null = pre-timestamp room (treated as stale). */
    val createdAtMillis: Long? = null
) {
    fun isStale(nowMillis: Long = System.currentTimeMillis()): Boolean =
        createdAtMillis == null || nowMillis - createdAtMillis > OFFER_STALE_MS
}

/** SDP paired with the static room's monotonic sequence (Phase 11). */
data class SequencedDescription(
    val description: SessionDescription,
    val seq: Int
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

/** Framework-agnostic error surfaced to the ViewModel / UI. */
enum class SignalingErrorKind {
    OFFLINE,
    TIMEOUT,
    NOT_FOUND,
    PERMISSION_DENIED,
    MALFORMED,
    UNKNOWN
}

data class SignalingError(
    val kind: SignalingErrorKind,
    val userMessage: String,
    val cause: Throwable? = null
)
