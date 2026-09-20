// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/SignalingClient.kt
// Location: app/src/main/java/com/calldad/data/signaling/SignalingClient.kt
package com.calldad.data.signaling

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Pure data-layer client for the WebRTC signaling channel.
 *
 * Firestore shape it owns (per Phase 2 spec):
 *
 *   calls/{callRoomId}
 *       type      : "OFFER" | "ANSWER"
 *       sdp       : String
 *       candidates/{autoId}
 *           serverUrl     : String?
 *           sdpMid        : String?
 *           sdpMLineIndex : Int?
 *           sdpCandidate  : String
 *
 * Lifecycle contract:
 *   - Caller:  publishOffer() -> observeRemoteDescription(ANSWER) + observeIceCandidates()
 *   - Callee:  fetchOffer()  -> publishAnswer()                 + observeIceCandidates()
 *   - Either:  addIceCandidate() for locally-gathered candidates.
 *   - Either:  teardown() on hang-up.
 *
 * This class deliberately has no knowledge of WebRTC, no Hilt, and no
 * Android context. It exposes suspend functions and cold Flows only.
 */
class SignalingClient(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val roomId: String = DEFAULT_ROOM_ID
) {

    private val roomRef = firestore.collection(COLLECTION_CALLS).document(roomId)
    private val candidatesRef = roomRef.collection(COLLECTION_CANDIDATES)

    // ---------------------------------------------------------------------
    // Caller path
    // ---------------------------------------------------------------------

    /** Caller writes the initial OFFER document. Idempotent via merge. */
    suspend fun publishOffer(localSdp: String): Result<Unit> = runCatchingFirestore {
        require(localSdp.isNotBlank()) { "Offer SDP must not be blank" }
        // Mark BEFORE the write: our own ringback must never count as Dad.
        OwnOfferRegistry.markPublished(localSdp)
        roomRef.set(
            mapOf(
                FIELD_TYPE to SdpType.OFFER.name,
                FIELD_SDP to localSdp,
                FIELD_CREATED_AT to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
        Unit
    }

    // ---------------------------------------------------------------------
    // Callee path
    // ---------------------------------------------------------------------

    /** Callee reads the pending OFFER before constructing an ANSWER. */
    suspend fun fetchOffer(): Result<SessionDescription> = runCatchingFirestore {
        val snapshot = roomRef.get().await()
        if (!snapshot.exists()) {
            throw SignalingFailure(SignalingErrorKind.NOT_FOUND, "Call room does not exist yet.")
        }
        val type = SdpType.fromWire(snapshot.getString(FIELD_TYPE))
        val sdp = snapshot.getString(FIELD_SDP)
        if (type != SdpType.OFFER || sdp.isNullOrBlank()) {
            throw SignalingFailure(SignalingErrorKind.MALFORMED, "Room does not contain a valid OFFER.")
        }
        if (OwnOfferRegistry.isOwn(sdp)) {
            // Our own ringback (double-call/hangup races) — not answerable.
            throw SignalingFailure(SignalingErrorKind.NOT_FOUND, "Call room does not exist yet.")
        }
        val description = SessionDescription(type, sdp, snapshot.getLong(FIELD_CREATED_AT))
        if (description.isStale()) {
            // Abandoned ring (caller vanished without teardown) — not answerable.
            throw SignalingFailure(SignalingErrorKind.NOT_FOUND, "Call room does not exist yet.")
        }
        description
    }

    /** Callee writes the ANSWER document. Idempotent via merge. */
    suspend fun publishAnswer(localSdp: String): Result<Unit> = runCatchingFirestore {
        require(localSdp.isNotBlank()) { "Answer SDP must not be blank" }
        roomRef.set(
            mapOf(
                FIELD_TYPE to SdpType.ANSWER.name,
                FIELD_SDP to localSdp
            ),
            SetOptions.merge()
        ).await()
        Unit
    }

    // ---------------------------------------------------------------------
    // Observers
    // ---------------------------------------------------------------------

    /**
     * Emits every time the room document's type matches [expectedType].
     * Caller passes ANSWER; a callee that arrived late passes OFFER.
     *
     * Cold: one Firestore listener per collector, removed on cancellation.
     */
    fun observeRemoteDescription(expectedType: SdpType): Flow<SessionDescription> = callbackFlow {
        val registration: ListenerRegistration = roomRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val type = SdpType.fromWire(snapshot.getString(FIELD_TYPE))
            val sdp = snapshot.getString(FIELD_SDP)
            if (type == expectedType && !sdp.isNullOrBlank()) {
                trySend(SessionDescription(type, sdp, snapshot.getLong(FIELD_CREATED_AT)))
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Emits only NEW ICE candidates written to the sub-collection.
     *
     * Uses [DocumentChange.Type.ADDED] rather than raw document iteration
     * because a Firestore snapshot listener always replays the full result
     * set on first attach — we would otherwise re-feed historical candidates
     * into the peer connection on every screen rotation.
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
    // Remote hangup
    // ---------------------------------------------------------------------

    /**
     * Emits once when the room document disappears (peer hung up or
     * declined — teardown deletes it). Cold; removed on cancellation.
     *
     * Start collecting only AFTER our own publish: the room legitimately
     * doesn't exist before that, and only a present-then-gone transition
     * counts as a hangup.
     */
    fun observeRoomDeleted(): Flow<Unit> = callbackFlow {
        var wasPresent = false
        val registration: ListenerRegistration = roomRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                wasPresent = true
            } else if (wasPresent) {
                trySend(Unit)
            }
        }
        awaitClose { registration.remove() }
    }

    // ---------------------------------------------------------------------
    // Teardown
    // ---------------------------------------------------------------------

    /**
     * Removes the room document and its candidate sub-collection.
     * Candidates are deleted in one batched commit; the room doc is removed
     * last so a racing observer never sees an empty parent with stale kids.
     */
    suspend fun teardown(): Result<Unit> = runCatchingFirestore {
        val candidates = candidatesRef.get().await()
        if (!candidates.isEmpty) {
            val batch = firestore.batch()
            candidates.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        roomRef.delete().await()
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
        /** Phase 2 hardcodes a single channel; Phase 4 will parameterise this. */
        const val DEFAULT_ROOM_ID = "dad_channel"

        private const val COLLECTION_CALLS = "calls"
        private const val COLLECTION_CANDIDATES = "candidates"

        private const val FIELD_TYPE = "type"
        private const val FIELD_SDP = "sdp"
        private const val FIELD_CREATED_AT = "createdAt"
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
