// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/CallDocument.kt — Phase 5: per-call room model
// Location: app/src/main/java/com/calldad/data/signaling/CallDocument.kt
package com.calldad.data.signaling

import com.google.firebase.firestore.DocumentSnapshot

enum class CallStatus {
    RINGING, CONNECTED, DECLINED, ENDED;

    companion object {
        fun fromWire(v: String?): CallStatus? =
            entries.firstOrNull { it.name == v?.uppercase() }
    }
}

data class CallDocument(
    val callId: String,
    val callerUid: String,
    val calleeUid: String,
    val status: CallStatus,
    val offer: String?,
    val answer: String?,
    /**
     * Server timestamp at creation; null while the write is still pending
     * locally. Null counts as FRESH (never strand a live ring on a clock).
     */
    val createdAtMillis: Long? = null
) {
    fun isStale(nowMillis: Long = System.currentTimeMillis()): Boolean =
        status == CallStatus.RINGING &&
            createdAtMillis != null && nowMillis - createdAtMillis > OFFER_STALE_MS
}

/**
 * Presence-only ring pointer (executor bridge, ADR-007): which call is
 * ringing, never any SDP. The Home listener uses this pre-Phase-6 token
 * plumbing; FCM carries the same callId for the killed-app path.
 */
data class RingAnnouncement(
    val callId: String,
    val callerUid: String,
    val createdAtMillis: Long
)

internal fun DocumentSnapshot.toCallDocumentOrNull(callId: String): CallDocument? {
    val status = CallStatus.fromWire(getString("status")) ?: return null
    return CallDocument(
        callId = callId,
        callerUid = getString("callerUid") ?: return null,
        calleeUid = getString("calleeUid") ?: return null,
        status = status,
        offer = getString("offer"),
        answer = getString("answer"),
        createdAtMillis = getTimestamp("createdAt")?.toDate()?.time
    )
}
