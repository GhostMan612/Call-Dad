// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/SignalingClient.kt — Phase 11: static family room + seq
// Location: app/src/main/java/com/calldad/data/signaling/SignalingClient.kt
package com.calldad.data.signaling

import com.calldad.webrtc.WebRtcLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Pure data-layer client for WebRTC signaling over ONE static room.
 *
 * Firestore shape (Phase 11):
 *
 *   calls/family_channel
 *       seq       : Int (monotonic; incremented on every offer)
 *       type      : "OFFER" | "ANSWER"
 *       sdp       : String
 *       status    : IDLE | RINGING | CONNECTING | CONNECTED | ENDED
 *       updatedAt : ServerTimestamp
 *       candidates/{autoId}: serverUrl?/sdpMid?/sdpMLineIndex?/sdpCandidate
 *
 * Lifecycle: publishOffer (seq+1, clears prior candidates) → callee
 * fetchOffer (OFFER+RINGING only) → publishAnswer → CONNECTED.
 * Decline/End flip status (doc persists; teardown writes ENDED).
 * ICE restart re-publishes with a higher seq (distinguishable).
 *
 * No WebRTC knowledge, no Hilt, no Android context. Suspend + cold Flows.
 */
class SignalingClient(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    private val roomRef =
        firestore.collection(COLLECTION_CALLS).document(STATIC_ROOM_ID)
    private val candidatesRef = roomRef.collection(COLLECTION_CANDIDATES)

    /** Fails fast when anonymous auth hasn't completed yet. */
    private fun requireAuth() {
        if (auth.currentUser == null) {
            throw SignalingFailure(
                SignalingErrorKind.UNKNOWN,
                "Not signed in yet. Please wait a moment and try again."
            )
        }
    }

    // ---------------------------------------------------------------------
    // Caller path
    // ---------------------------------------------------------------------

    /**
     * Publish an OFFER into the static room with the next sequence number,
     * clearing the previous generation's candidates. Every offer —
     * initial or ICE-restart — increments seq (spec correction 5).
     */
    suspend fun publishOffer(localSdp: String): Result<Unit> = runCatchingFirestore {
        requireAuth()
        require(localSdp.isNotBlank()) { "Offer SDP must not be blank" }
        // Mark BEFORE the write: our own ringback must never count as Dad.
        OwnSdpRegistry.markPublished(localSdp)
        val snap = roomRef.get().await()
        val nextSeq = (if (snap.exists()) snap.getLong(FIELD_SEQ) ?: 0 else 0) + 1
        roomRef.set(
            mapOf(
                FIELD_SEQ to nextSeq,
                FIELD_TYPE to SdpType.OFFER.name,
                FIELD_SDP to localSdp,
                FIELD_STATUS to CallStatus.RINGING.name,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        clearCandidates()
        Unit
    }

    // ---------------------------------------------------------------------
    // Callee path
    // ---------------------------------------------------------------------

    /**
     * Read the pending OFFER. Returns it ONLY when type == OFFER and status
     * == RINGING; CONNECTED/IDLE/anything else is NOT_FOUND (nothing to
     * answer). Own ringback is also NOT_FOUND (not answerable).
     */
    suspend fun fetchOffer(): Result<SequencedDescription> = runCatchingFirestore {
        requireAuth()
        val snapshot = roomRef.get().await()
        if (!snapshot.exists()) {
            throw SignalingFailure(SignalingErrorKind.NOT_FOUND, "Call room does not exist yet.")
        }
        val type = SdpType.fromWire(snapshot.getString(FIELD_TYPE))
        val sdp = snapshot.getString(FIELD_SDP)
        val status = CallStatus.fromWire(snapshot.getString(FIELD_STATUS))
        val seq = snapshot.getLong(FIELD_SEQ)?.toInt() ?: 0
        if (type != SdpType.OFFER || status != CallStatus.RINGING || sdp.isNullOrBlank()) {
            throw SignalingFailure(SignalingErrorKind.NOT_FOUND, "Call room does not exist yet.")
        }
        if (OwnSdpRegistry.isOwn(sdp)) {
            throw SignalingFailure(SignalingErrorKind.NOT_FOUND, "Call room does not exist yet.")
        }
        SequencedDescription(SessionDescription(type, sdp), seq)
    }

    /** Callee writes the ANSWER. Status CONNECTED; seq preserved. */
    suspend fun publishAnswer(localSdp: String): Result<Unit> = runCatchingFirestore {
        requireAuth()
        require(localSdp.isNotBlank()) { "Answer SDP must not be blank" }
        roomRef.set(
            mapOf(
                FIELD_TYPE to SdpType.ANSWER.name,
                FIELD_SDP to localSdp,
                FIELD_STATUS to CallStatus.CONNECTED.name,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        WebRtcLog.transition("ANSWER published, status CONNECTED")
    }

    // ---------------------------------------------------------------------
    // Either side: decline / hangup / reset
    // ---------------------------------------------------------------------

    /** Either side: flip status to DECLINED (doc kept for the peer). */
    suspend fun declineCall(): Result<Unit> = runCatchingFirestore {
        requireAuth()
        roomRef.set(
            mapOf(
                FIELD_STATUS to CallStatus.DECLINED.name,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        WebRtcLog.transition("Call DECLINED")
    }

    /** Either side: flip status to ENDED. Used by hang-up. */
    suspend fun endCall(): Result<Unit> = runCatchingFirestore {
        requireAuth()
        roomRef.set(
            mapOf(
                FIELD_STATUS to CallStatus.ENDED.name,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        WebRtcLog.transition("Call ENDED")
    }

    /**
     * Teardown = status ENDED (doc persists; deletion is forbidden by
     * rules). Candidates of a dead generation are cleared on the next
     * offer, not here — the peer may still be trickling.
     */
    suspend fun teardown(): Result<Unit> = endCall()

    /** Explicit cleanup: write the initial IDLE state (satisfies create). */
    suspend fun resetRoom(): Result<Unit> = runCatchingFirestore {
        requireAuth()
        roomRef.set(
            mapOf(
                FIELD_SEQ to 0,
                FIELD_STATUS to CallStatus.IDLE.name,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            )
        ).await()
        clearCandidates()
        Unit
    }

    private suspend fun clearCandidates() {
        val candidates = candidatesRef.get().await()
        if (!candidates.isEmpty) {
            val batch = firestore.batch()
            candidates.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    // ---------------------------------------------------------------------
    // Observers
    // ---------------------------------------------------------------------

    /**
     * Emits (description + seq) every time the room document's type matches
     * expectedType. Emission is additionally gated: OFFER only while
     * RINGING, ANSWER only while CONNECTED — so an ENDED room's leftover
     * type can never ghost-trigger a popup (device-proven class of bug).
     * Cold: one listener per collector, removed on cancellation.
     */
    fun observeRemoteDescriptionWithSeq(
        expectedType: SdpType
    ): Flow<SequencedDescription> = callbackFlow {
        val reg: ListenerRegistration = roomRef.addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) return@addSnapshotListener
            val type = SdpType.fromWire(snap.getString(FIELD_TYPE))
            val sdp = snap.getString(FIELD_SDP)
            val status = CallStatus.fromWire(snap.getString(FIELD_STATUS))
            val seq = snap.getLong(FIELD_SEQ)?.toInt() ?: 0
            val live = (expectedType == SdpType.OFFER && status == CallStatus.RINGING) ||
                    (expectedType == SdpType.ANSWER && status == CallStatus.CONNECTED)
            if (type == expectedType && !sdp.isNullOrBlank() && live) {
                trySend(
                    SequencedDescription(
                        SessionDescription(
                            type,
                            sdp,
                            snap.getTimestamp(FIELD_UPDATED_AT)?.toDate()?.time
                        ),
                        seq
                    )
                )
            }
        }
        awaitClose { reg.remove() }
    }

    /** Whole-document status observer (hangup/decline mirror). Cold. */
    fun observeStatus(): Flow<CallStatus> = callbackFlow {
        val reg: ListenerRegistration = roomRef.addSnapshotListener { snap, err ->
            if (err != null) {
                close(err)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) return@addSnapshotListener
            CallStatus.fromWire(snap.getString(FIELD_STATUS))?.let { trySend(it) }
        }
        awaitClose { reg.remove() }
    }

    /**
     * Emits only NEW ICE candidates. ADDED-only: listeners replay the full
     * set on attach, and we must not re-feed history on rotation.
     */
    fun observeIceCandidates(): Flow<IceCandidate> = callbackFlow {
        val registration = candidatesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            snapshot
                ?.documentChanges
                ?.asSequence()
                ?.filter { it.type == DocumentChange.Type.ADDED }
                ?.mapNotNull { it.document.toIceCandidateOrNull() }
                ?.forEach { trySend(it) }
        }
        awaitClose { registration.remove() }
    }

    // ---------------------------------------------------------------------
    // ICE writes
    // ---------------------------------------------------------------------

    /** Trickle a locally-gathered candidate to the peer. */
    suspend fun addIceCandidate(candidate: IceCandidate): Result<Unit> = runCatchingFirestore {
        requireAuth()
        require(candidate.sdpCandidate.isNotBlank()) { "Candidate payload must not be blank" }
        val payload = mutableMapOf<String, Any?>(
            FIELD_SDP_CANDIDATE to candidate.sdpCandidate
        )
        candidate.serverUrl?.let { payload[FIELD_SERVER_URL] = it }
        candidate.sdpMid?.let { payload[FIELD_SDP_MID] = it }
        candidate.sdpMLineIndex?.let { payload[FIELD_SDP_MLINE_INDEX] = it }

        candidatesRef.add(payload).await()
        Unit
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private fun com.google.firebase.firestore.DocumentSnapshot.toIceCandidateOrNull(): IceCandidate? {
        val payload = getString(FIELD_SDP_CANDIDATE) ?: return null
        return IceCandidate(
            sdpCandidate = payload,
            sdpMid = getString(FIELD_SDP_MID),
            sdpMLineIndex = getLong(FIELD_SDP_MLINE_INDEX)?.toInt(),
            serverUrl = getString(FIELD_SERVER_URL)
        )
    }

    /**
     * Wraps a suspending Firestore call, translating SDK exceptions into
     * [SignalingError] values the UI can present to a parent.
     */
    private inline fun <T> runCatchingFirestore(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (t: Throwable) {
            Result.failure(t.toSignalingException())
        }

    private fun Throwable.toSignalingException(): Throwable {
        if (this is SignalingFailure) return this
        return when (this) {
            is FirebaseFirestoreException -> SignalingFailure(
                kind = when (code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        SignalingErrorKind.OFFLINE
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                        SignalingErrorKind.TIMEOUT
                    FirebaseFirestoreException.Code.NOT_FOUND ->
                        SignalingErrorKind.NOT_FOUND
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        SignalingErrorKind.PERMISSION_DENIED
                    else -> SignalingErrorKind.UNKNOWN
                },
                userMessage = friendlyMessage(code),
                cause = this
            )
            else -> SignalingFailure(
                kind = SignalingErrorKind.UNKNOWN,
                userMessage = message ?: "Something went wrong.",
                cause = this
            )
        }
    }

    private fun friendlyMessage(code: FirebaseFirestoreException.Code): String = when (code) {
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "No internet connection. Check Wi-Fi and try again."
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "The network is too slow right now. Please try again."
        FirebaseFirestoreException.Code.NOT_FOUND ->
            "Could not find the call room."
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Calling is not allowed right now."
        else -> "Something went wrong. (${code.name})"
    }

    companion object {
        const val STATIC_ROOM_ID = "family_channel"

        private const val COLLECTION_CALLS = "calls"
        private const val COLLECTION_CANDIDATES = "candidates"

        private const val FIELD_SEQ = "seq"
        private const val FIELD_TYPE = "type"
        private const val FIELD_SDP = "sdp"
        private const val FIELD_STATUS = "status"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_SERVER_URL = "serverUrl"
        private const val FIELD_SDP_MID = "sdpMid"
        private const val FIELD_SDP_MLINE_INDEX = "sdpMLineIndex"
        private const val FIELD_SDP_CANDIDATE = "sdpCandidate"
    }
}

/** Internal wrapper so callers can pattern-match on the typed error kind. */
class SignalingFailure(
    val kind: SignalingErrorKind,
    val userMessage: String,
    override val cause: Throwable? = null
) : Exception(userMessage, cause)
