// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/CallDocument.kt — Phase 11: static-room model
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
    val status: CallStatus,
    val offer: String?,
    val answer: String?,
    val seq: Int,
    /**
     * Server timestamp of last write; null while pending locally.
     * Null counts as FRESH (never strand a live write on a clock).
     */
    val updatedAtMillis: Long? = null
)

internal fun DocumentSnapshot.toCallDocumentOrNull(): CallDocument? {
    val status = CallStatus.fromWire(getString("status")) ?: return null
    return CallDocument(
        status = status,
        offer = getString("offer"),
        answer = getString("answer"),
        seq = getLong("seq")?.toInt() ?: 0,
        updatedAtMillis = getTimestamp("updatedAt")?.toDate()?.time
    )
}
