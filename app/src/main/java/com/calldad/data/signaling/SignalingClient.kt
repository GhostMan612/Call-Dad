// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/SignalingClient.kt — pair-scoped rooms (ADR-015)
// Location: app/src/main/java/com/calldad/data/signaling/SignalingClient.kt
package com.calldad.data.signaling

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.TransactionOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class SignalingClient(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val calls = firestore.collection("calls")

    private val txOptions = TransactionOptions.Builder()
        .setMaxAttempts(3)
        .build()

    /**
     * Starts a new call generation: seq+1, RINGING, fresh offer, empty
     * candidate arrays. The room belongs to exactly two people, so there
     * is no "busy" state to guard: whatever generation was live is
     * superseded (the peer observes the seq bump as a re-ring).
     */
    suspend fun publishOffer(
        callId: String,
        offerSdp: String,
        callerUid: String,
        calleeUid: String
    ): Result<Int> = runCatching {
        firestore.runTransaction(txOptions) { txn ->
            val ref = calls.document(callId)
            val snap = txn.get(ref)
            val nextSeq = (snap.getLong("seq")?.toInt() ?: 0) + 1
            txn.set(ref, mapOf(
                "status" to "RINGING",
                "seq" to nextSeq,
                "callerUid" to callerUid,
                "calleeUid" to calleeUid,
                "offer" to mapOf("type" to "OFFER", "sdp" to offerSdp),
                "answer" to null,
                "callerCandidates" to emptyList<Map<String, Any>>(),
                "calleeCandidates" to emptyList<Map<String, Any>>(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            nextSeq
        }.await()
    }

    /** Answers generation [seq]. Fails with [CallNoLongerRingingException] if it moved on. */
    suspend fun publishAnswer(
        callId: String,
        seq: Int,
        answerSdp: String
    ): Result<Unit> = runCatching {
        firestore.runTransaction(txOptions) { txn ->
            val ref = calls.document(callId)
            val snap = txn.get(ref)
            if (!snap.exists() ||
                snap.getString("status") != "RINGING" ||
                snap.getLong("seq")?.toInt() != seq
            ) {
                throw CallNoLongerRingingException()
            }
            txn.update(ref, mapOf(
                "status" to "CONNECTED",
                "answer" to mapOf("type" to "ANSWER", "sdp" to answerSdp),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
        Unit
    }

    /**
     * Publishes an ICE-restart OFFER for a call that is ALREADY CONNECTED.
     *
     * Deliberately NOT [publishOffer]: that method starts a new generation
     * (seq+1, status RINGING, both candidate arrays cleared), which is exactly
     * wrong for a reconnect. It would re-ring the other phone mid-call, drop
     * every candidate already exchanged, and make the peer's in-call teardown
     * generation-checked teardown kill a call that is still live. Renegotiation
     * keeps [seq] and CONNECTED and only swaps the offer SDP, so both sides stay
     * on the same generation and a reconnect can never look like a new call.
     *
     * Fails with [CallNoLongerRingingException] if the generation moved on, so
     * a reconnect racing a hangup is discarded rather than resurrecting.
     */
    suspend fun publishRenegotiation(
        callId: String,
        seq: Int,
        offerSdp: String
    ): Result<Unit> = runCatching {
        firestore.runTransaction(txOptions) { txn ->
            val ref = calls.document(callId)
            val snap = txn.get(ref)
            if (!snap.exists() ||
                snap.getLong("seq")?.toInt() != seq ||
                snap.getString("status") !in LIVE_STATUSES
            ) {
                throw CallNoLongerRingingException()
            }
            txn.update(ref, mapOf(
                "offer" to mapOf("type" to "OFFER", "sdp" to offerSdp),
                "renegotiating" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
        Unit
    }

    /**
     * Answers an ICE-restart OFFER, staying in the same generation.
     *
     * The caller-side twin of [publishRenegotiation]. Like it, this never touches
     * [seq] and never returns the room to RINGING: a reconnect that re-rings the
     * phone would alarm the grown-up and hide the call they are already in.
     */
    suspend fun publishRenegotiationAnswer(
        callId: String,
        seq: Int,
        answerSdp: String
    ): Result<Unit> = runCatching {
        firestore.runTransaction(txOptions) { txn ->
            val ref = calls.document(callId)
            val snap = txn.get(ref)
            if (!snap.exists() ||
                snap.getLong("seq")?.toInt() != seq ||
                snap.getString("status") !in LIVE_STATUSES
            ) {
                throw CallNoLongerRingingException()
            }
            txn.update(ref, mapOf(
                "answer" to mapOf("type" to "ANSWER", "sdp" to answerSdp),
                "renegotiating" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
        Unit
    }

    /**
     * Moves generation [seq] to a terminal [status] (ENDED / DECLINED),
     * only if that generation is still live. A late teardown can never
     * kill a newer call.
     */
    suspend fun finishCall(
        callId: String,
        seq: Int,
        status: String
    ): Result<Unit> = runCatching {
        firestore.runTransaction(txOptions) { txn ->
            val ref = calls.document(callId)
            val snap = txn.get(ref)
            val live = snap.exists() &&
                snap.getLong("seq")?.toInt() == seq &&
                snap.getString("status") in LIVE_STATUSES
            if (live) {
                txn.update(ref, mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
        }.await()
        Unit
    }

    /**
     * Fire-and-forget terminal write for teardown paths that cannot suspend
     * (ViewModel cleared, process going away). Firestore queues the write
     * locally and delivers it once online.
     *
     * GENERATION-CHECKED. It used to take no `seq` and write ENDED
     * unconditionally, so a ViewModel clearing for generation n could
     * overwrite generation n+1's RINGING — cancelling a call the peer was
     * actively ringing, for a generation this device never owned. That is
     * the exact outcome ADR-015 says is impossible. Same transaction guard
     * as [finishCall], just not awaited.
     */
    fun finishCallDetached(callId: String, seq: Int, status: String) {
        runCatching {
            firestore.runTransaction(txOptions) { txn ->
                val ref = calls.document(callId)
                val snap = txn.get(ref)
                val live = snap.exists() &&
                    snap.getLong("seq")?.toInt() == seq &&
                    snap.getString("status") in LIVE_STATUSES
                if (live) {
                    txn.update(ref, mapOf(
                        "status" to status,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
            }
        }
    }

    /**
     * Trickles one locally-gathered candidate into the room arrays.
     * Caller writes callerCandidates, callee writes calleeCandidates;
     * arrayUnion makes concurrent trickle safe with no read-modify-write.
     */
    suspend fun addIceCandidate(
        callId: String,
        candidate: IceCandidate,
        byCaller: Boolean
    ): Result<Unit> = runCatching {
        val field = if (byCaller) "callerCandidates" else "calleeCandidates"
        calls.document(callId)
            .update(field, FieldValue.arrayUnion(candidate.toWireMap()))
            .await()
        Unit
    }

    fun observeCall(callId: String): Flow<CallDocument> = callbackFlow {
        val reg: ListenerRegistration = calls.document(callId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                if (!snap.exists()) {
                    trySend(CallDocument.empty(callId))
                    return@addSnapshotListener
                }
                snap.toCallDocumentOrNull()?.let { trySend(it) }
            }
        awaitClose { reg.remove() }
    }

    suspend fun fetchCall(callId: String): Result<CallDocument?> =
        runCatching {
            val snap = calls.document(callId).get().await()
            if (!snap.exists()) null else snap.toCallDocumentOrNull()
        }

    private fun com.google.firebase.firestore.DocumentSnapshot
        .toCallDocumentOrNull(): CallDocument? {
        // serverTimestamp() fires the listener twice: once optimistically
        // with hasPendingWrites=true and a null timestamp, then again
        // with the server value. Skip the optimistic snap.
        if (metadata.hasPendingWrites()) return null
        val status = getString("status") ?: return null

        val offerMap = get("offer") as? Map<*, *>
        val answerMap = get("answer") as? Map<*, *>
        return CallDocument(
            callId = id,
            status = status,
            seq = getLong("seq")?.toInt() ?: 0,
            callerUid = getString("callerUid").orEmpty(),
            calleeUid = getString("calleeUid").orEmpty(),
            offer = offerMap?.toSessionDescriptionOrNull(),
            answer = answerMap?.toSessionDescriptionOrNull(),
            callerCandidates = (get("callerCandidates") as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.toIceCandidateOrNull() },
            calleeCandidates = (get("calleeCandidates") as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.toIceCandidateOrNull() },
            renegotiating = getBoolean("renegotiating") ?: false,
            updatedAtMs = getTimestamp("updatedAt")?.toDate()?.time,
            isFromCache = metadata.isFromCache
        )
    }

    private fun Map<*, *>.toSessionDescriptionOrNull(): SessionDescription? {
        val type = SdpType.fromWire(this["type"] as? String) ?: return null
        val sdp = this["sdp"] as? String ?: return null
        return SessionDescription(type, sdp)
    }

    private fun Map<*, *>.toIceCandidateOrNull(): IceCandidate? {
        val payload = this["sdpCandidate"] as? String ?: return null
        return IceCandidate(
            sdpCandidate = payload,
            sdpMid = this["sdpMid"] as? String,
            sdpMLineIndex = (this["sdpMLineIndex"] as? Long)?.toInt(),
            serverUrl = this["serverUrl"] as? String
        )
    }

    private fun IceCandidate.toWireMap(): Map<String, Any?> = mapOf(
        "serverUrl" to serverUrl,
        "sdpMid" to sdpMid,
        "sdpMLineIndex" to sdpMLineIndex,
        "sdpCandidate" to sdpCandidate
    )

    private companion object {
        val LIVE_STATUSES = setOf("RINGING", "CONNECTED")
    }
}

class CallNoLongerRingingException : Exception("The call is no longer ringing.")

data class CallDocument(
    val callId: String,
    val status: String,
    val seq: Int,
    val callerUid: String,
    val calleeUid: String,
    val offer: SessionDescription?,
    val answer: SessionDescription?,
    val callerCandidates: List<IceCandidate> = emptyList(),
    val calleeCandidates: List<IceCandidate> = emptyList(),
    /** True while the caller's ICE-restart OFFER is awaiting an answer. */
    val renegotiating: Boolean = false,
    val updatedAtMs: Long? = null,
    val isFromCache: Boolean = false
) {
    companion object {
        fun empty(callId: String) =
            CallDocument(callId, "IDLE", 0, "", "", null, null)
    }
}
