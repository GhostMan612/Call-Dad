// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/SignalingClient.kt — Phase 5: per-call rooms + ring bridge
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
import java.util.UUID

/**
 * Pure data-layer client for WebRTC signaling over per-call rooms.
 *
 * Firestore shape (Phase 5):
 *
 *   calls/{callId}
 *       callerUid : String | calleeUid : String
 *       status    : RINGING | CONNECTED | DECLINED | ENDED
 *       offer     : String  | answer : String?
 *       createdAt : ServerTimestamp
 *       candidates/{autoId}: serverUrl?/sdpMid?/sdpMLineIndex?/sdpCandidate
 *
 *   ring/dad (executor bridge, ADR-007 — presence ONLY, no SDP):
 *       callId | callerUid | createdAt(client millis)
 *
 * Lifecycle: createCallRoom → observeCall/status → publishAnswer →
 * Connected; declineCall/endCall flip status. Ring pointer lets the
 * app-open callee discover callIds before Phase 6 token plumbing.
 *
 * No WebRTC knowledge, no Hilt, no Android context. Suspend + cold Flows.
 */
class SignalingClient(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    private val callsCollection = firestore.collection(COLLECTION_CALLS)
    private val ringDoc = firestore.collection(COLLECTION_RING).document(RING_DOC_ID)

    private fun candidatesRef(callId: String) =
        callsCollection.document(callId).collection(COLLECTION_CANDIDATES)

    /** Current uid, or throws if anonymous auth hasn't completed yet. */
    private fun currentUid(): String =
        auth.currentUser?.uid
            ?: throw SignalingFailure(
                SignalingErrorKind.UNKNOWN,
                "Not signed in yet. Please wait a moment and try again."
            )

    // ---------------------------------------------------------------------
    // Caller path
    // ---------------------------------------------------------------------

    /**
     * Caller: create a NEW call room with a fresh UUID.
     * Returns the generated callId so the VM can listen on it.
     */
    suspend fun createCallRoom(
        calleeUid: String,
        offerSdp: String
    ): Result<String> = runCatchingFirestore {
        require(calleeUid.isNotBlank()) { "calleeUid must not be blank" }
        require(offerSdp.isNotBlank()) { "Offer SDP must not be blank" }

        val callId = UUID.randomUUID().toString()
        OwnCallRegistry.markPublished(callId)
        callsCollection.document(callId).set(
            mapOf(
                FIELD_CALLER_UID to currentUid(),
                FIELD_CALLEE_UID to calleeUid,
                FIELD_STATUS to CallStatus.RINGING.name,
                FIELD_OFFER to offerSdp,
                FIELD_ANSWER to null,
                FIELD_CREATED_AT to FieldValue.serverTimestamp()
            )
        ).await()
        // Ring pointer (bridge): presence only, so the app-open callee can
        // discover this callId. Best-effort — the FCM path doesn't need it.
        // Failures are LOGGED (literal only): a silent ring-write failure
        // looks exactly like "the other phone never rang" (device-proven).
        runCatching {
            ringDoc.set(
                mapOf(
                    FIELD_CALL_ID to callId,
                    FIELD_CALLER_UID to currentUid(),
                    FIELD_CREATED_AT to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        }.onFailure {
            WebRtcLog.transition("Ring pointer write failed")
        }
        WebRtcLog.transition("Call room created")
        callId
    }

    // ---------------------------------------------------------------------
    // Callee path
    // ---------------------------------------------------------------------

    /** Callee: write the ANSWER and flip status to CONNECTED. */
    suspend fun publishAnswer(
        callId: String,
        answerSdp: String
    ): Result<Unit> = runCatchingFirestore {
        require(answerSdp.isNotBlank()) { "Answer SDP must not be blank" }
        callsCollection.document(callId).update(
            mapOf(
                FIELD_ANSWER to answerSdp,
                FIELD_STATUS to CallStatus.CONNECTED.name
            )
        ).await()
        WebRtcLog.transition("ANSWER published, status CONNECTED")
    }

    /** Fetch a call document once. */
    suspend fun fetchCall(callId: String): Result<CallDocument> =
        runCatchingFirestore {
            val snap = callsCollection.document(callId).get().await()
            if (!snap.exists()) throw SignalingFailure(
                SignalingErrorKind.NOT_FOUND, "Call not found."
            )
            snap.toCallDocumentOrNull(callId) ?: throw SignalingFailure(
                SignalingErrorKind.MALFORMED, "Call document malformed."
            )
        }

    // ---------------------------------------------------------------------
    // Either side: decline / hangup
    // ---------------------------------------------------------------------

    /** Either side: flip status to DECLINED (doc kept for the peer to see). */
    suspend fun declineCall(callId: String): Result<Unit> = runCatchingFirestore {
        callsCollection.document(callId).update(
            mapOf(FIELD_STATUS to CallStatus.DECLINED.name)
        ).await()
        clearRingIfOurs(callId)
        WebRtcLog.transition("Call DECLINED")
    }

    /** Either side: flip status to ENDED. Used by hang-up. */
    suspend fun endCall(callId: String): Result<Unit> = runCatchingFirestore {
        callsCollection.document(callId).update(
            mapOf(FIELD_STATUS to CallStatus.ENDED.name)
        ).await()
        clearRingIfOurs(callId)
        WebRtcLog.transition("Call ENDED")
    }

    /**
     * Delete our own ring pointer if it still points at this call.
     * Read-then-delete so we never clear someone else's ring.
     */
    private suspend fun clearRingIfOurs(callId: String) {
        runCatching {
            val snap = ringDoc.get().await()
            if (snap.exists() && snap.getString(FIELD_CALL_ID) == callId) {
                ringDoc.delete().await()
            }
        }
    }

    // ---------------------------------------------------------------------
    // Observers
    // ---------------------------------------------------------------------

    /**
     * Listen to the whole call document. Emits the parsed status + sdp
     * on every change. The VM decides what each transition means.
     */
    fun observeCall(callId: String): Flow<CallDocument> = callbackFlow {
        val reg = callsCollection.document(callId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val doc = snap.toCallDocumentOrNull(callId) ?: return@addSnapshotListener
                trySend(doc)
            }
        awaitClose { reg.remove() }
    }

    /**
     * Ring pointer observer (bridge, ADR-007): emits live rings from OTHER
     * devices. Skips our own rings and anything stale (>60s). Cold.
     */
    fun observeRing(): Flow<RingAnnouncement> = callbackFlow {
        val reg: ListenerRegistration = ringDoc.addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) return@addSnapshotListener
            val callId = snap.getString(FIELD_CALL_ID) ?: return@addSnapshotListener
            if (OwnCallRegistry.isOwn(callId)) return@addSnapshotListener
            val createdAt = snap.getLong(FIELD_CREATED_AT) ?: 0L
            if (System.currentTimeMillis() - createdAt > OFFER_STALE_MS) {
                return@addSnapshotListener
            }
            trySend(
                RingAnnouncement(
                    callId = callId,
                    callerUid = snap.getString(FIELD_CALLER_UID) ?: "",
                    createdAtMillis = createdAt
                )
            )
        }
        awaitClose { reg.remove() }
    }

    /**
     * Deletion backup for a specific call: docs are status-driven and
     * nothing deletes them in the happy path, but a present-then-gone
     * transition must never strand a watcher. Cold.
     */
    fun observeCallDeleted(callId: String): Flow<Unit> = callbackFlow {
        var wasPresent = false
        val reg: ListenerRegistration = callsCollection.document(callId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    wasPresent = true
                } else if (wasPresent) {
                    trySend(Unit)
                }
            }
        awaitClose { reg.remove() }
    }

    /**
     * Emits only NEW ICE candidates in calls/{callId}/candidates.
     * ADDED-only: listeners replay the full set on attach, and we must not
     * re-feed history on every rotation.
     */
    fun observeIceCandidates(callId: String): Flow<IceCandidate> = callbackFlow {
        val registration = candidatesRef(callId).addSnapshotListener { snapshot, error ->
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
    suspend fun addIceCandidate(callId: String, candidate: IceCandidate): Result<Unit> =
        runCatchingFirestore {
            require(candidate.sdpCandidate.isNotBlank()) { "Candidate payload must not be blank" }
            val payload = mutableMapOf<String, Any?>(
                FIELD_SDP_CANDIDATE to candidate.sdpCandidate
            )
            candidate.serverUrl?.let { payload[FIELD_SERVER_URL] = it }
            candidate.sdpMid?.let { payload[FIELD_SDP_MID] = it }
            candidate.sdpMLineIndex?.let { payload[FIELD_SDP_MLINE_INDEX] = it }

            candidatesRef(callId).add(payload).await()
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
        private const val COLLECTION_CALLS = "calls"
        private const val COLLECTION_CANDIDATES = "candidates"
        private const val COLLECTION_RING = "ring"
        private const val RING_DOC_ID = "dad"

        private const val FIELD_CALLER_UID = "callerUid"
        private const val FIELD_CALLEE_UID = "calleeUid"
        private const val FIELD_STATUS = "status"
        private const val FIELD_OFFER = "offer"
        private const val FIELD_ANSWER = "answer"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_CALL_ID = "callId"
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
