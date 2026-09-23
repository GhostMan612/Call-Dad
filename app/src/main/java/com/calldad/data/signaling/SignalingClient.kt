// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/SignalingClient.kt — Contracts 2 & 3: per-call rooms
// Location: app/src/main/java/com/calldad/data/signaling/SignalingClient.kt
package com.calldad.data.signaling

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.TransactionOptions
import com.calldad.webrtc.WebRtcLog
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class SignalingClient(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val calls = firestore.collection("calls")

    private val txOptions = TransactionOptions.Builder()
        .setMaxAttempts(5)
        .build()

    private val STALE_THRESHOLD_MS = 1_200_000L

    suspend fun publishOffer(
        callId: String,
        offerSdp: String,
        callerUid: String,
        calleeUid: String
    ): Result<Int> = try {
        val newSeq = firestore.runTransaction(txOptions) { txn ->
            val ref = calls.document(callId)
            val snap = txn.get(ref)

            if (snap.exists() && snap.getString("status") == "CONNECTED") {
                val updatedAt = snap.getTimestamp("updatedAt")
                val isStale = updatedAt == null ||
                    (System.currentTimeMillis() -
                        updatedAt.toDate().time) > STALE_THRESHOLD_MS
                if (!isStale) {
                    throw FirebaseFirestoreException(
                        "Peer busy",
                        FirebaseFirestoreException.Code.ABORTED
                    )
                }

                // Peer-unreachability guard. The pairing rules allow
                // reads of all pairing documents (expired or not), so
                // this txn.get() returns the document even when
                // expiresAt is in the past. The client checks expiry.
                val peerPairing = txn.get(
                    firestore.collection("pairings").document(calleeUid)
                )
                if (peerPairing.exists()) {
                    val peerExpiresAt = peerPairing.getTimestamp("expiresAt")
                    if (peerExpiresAt != null &&
                        peerExpiresAt.toDate().time >
                            System.currentTimeMillis()) {
                        throw FirebaseFirestoreException(
                            "Peer reachable",
                            FirebaseFirestoreException.Code.ABORTED
                        )
                    }
                }

                WebRtcLog.transition("Stale CONNECTED room taken over")
            }

            val currentSeq = snap.getLong("seq")?.toInt() ?: 0
            val nextSeq = currentSeq + 1

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
        Result.success(newSeq)
    } catch (t: FirebaseFirestoreException) {
        Result.failure(
            if (t.code == FirebaseFirestoreException.Code.ABORTED) {
                PeerBusyException()
            } else {
                TransactionExhaustedException(t)
            }
        )
    } catch (t: Throwable) {
        Result.failure(t)
    }

    suspend fun publishAnswer(
        callId: String,
        answerSdp: String
    ): Result<Unit> = runCatching {
        firestore.runTransaction(txOptions) { txn ->
            val ref = calls.document(callId)
            val snap = txn.get(ref)
            require(snap.exists()) { "Call document missing." }
            require(snap.getString("status") == "RINGING") {
                "Cannot answer: status is ${snap.getString("status")}"
            }
            txn.update(ref, mapOf(
                "status" to "CONNECTED",
                "answer" to mapOf("type" to "ANSWER", "sdp" to answerSdp),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
        Unit
    }

    suspend fun declineCall(callId: String): Result<Unit> = runCatching {
        calls.document(callId).update(
            mapOf(
                "status" to "DECLINED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    suspend fun endCall(callId: String): Result<Unit> = runCatching {
        calls.document(callId).update(
            mapOf(
                "status" to "ENDED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    /**
     * Heartbeat: refreshes the room timestamp and keeps this device's
     * pairing presence alive, atomically. Called every 120s while
     * Connected; the 20-minute stale threshold gives margin against
     * Doze throttling the loop.
     */
    suspend fun heartbeat(
        callId: String,
        ownUid: String
    ): Result<Unit> = runCatching {
        val batch = firestore.batch()

        batch.update(
            calls.document(callId),
            "updatedAt", FieldValue.serverTimestamp()
        )

        // Use set(merge) instead of update(). The pairing document
        // may not exist (fresh install, cleaned up). update() would
        // fail with NOT_FOUND and abort the entire batch.
        batch.set(
            firestore.collection("pairings").document(ownUid),
            mapOf(
                "uid" to ownUid,
                "expiresAt" to Timestamp(
                    Date(System.currentTimeMillis() + 1_200_000L)
                )
            ),
            SetOptions.merge()
        )

        batch.commit().await()
        Unit
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
                if (snap == null || !snap.exists()) {
                    trySend(CallDocument.EMPTY)
                    return@addSnapshotListener
                }
                val doc = snap.toCallDocumentOrNull()
                if (doc != null) {
                    trySend(doc.copy(isFromCache = snap.metadata.isFromCache))
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun fetchCall(callId: String): Result<CallDocument> =
        runCatching {
            val snap = calls.document(callId).get().await()
            if (!snap.exists()) throw IllegalStateException("Call not found.")
            snap.toCallDocumentOrNull()
                ?: throw IllegalStateException("Malformed call document.")
        }

    private fun com.google.firebase.firestore.DocumentSnapshot
        .toCallDocumentOrNull(): CallDocument? {
        // serverTimestamp() fires the listener twice: once optimistically
        // with hasPendingWrites=true and a null timestamp, then again
        // with the server value. The optimistic snap has no usable clock
        // (and a half-written shape); skip it and wait for the server ack.
        // NOTE: spec text uses property syntax; the Java getter requires
        // explicit parens to compile.
        if (metadata.hasPendingWrites()) return null
        val status = getString("status") ?: return null
        val seq = getLong("seq")?.toInt() ?: 0
        val callerUid = getString("callerUid").orEmpty()
        val calleeUid = getString("calleeUid").orEmpty()

        val offerMap = get("offer") as? Map<*, *>
        val answerMap = get("answer") as? Map<*, *>
        val offer = offerMap?.let {
            SessionDescription(
                SdpType.fromWire(it["type"] as? String) ?: return@let null,
                it["sdp"] as? String ?: return@let null
            )
        }
        val answer = answerMap?.let {
            SessionDescription(
                SdpType.fromWire(it["type"] as? String) ?: return@let null,
                it["sdp"] as? String ?: return@let null
            )
        }
        return CallDocument(
            callId = id,
            status = status,
            seq = seq,
            callerUid = callerUid,
            calleeUid = calleeUid,
            offer = offer,
            answer = answer,
            callerCandidates = (get("callerCandidates") as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.toIceCandidateOrNull() },
            calleeCandidates = (get("calleeCandidates") as? List<*>)
                .orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.toIceCandidateOrNull() },
            isFromCache = false
        )
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
}

class PeerBusyException : Exception("Peer is already in a call.")
class TransactionExhaustedException(cause: Throwable) :
    Exception("Transaction retries exhausted.", cause)

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
    val isFromCache: Boolean
) {
    companion object {
        val EMPTY = CallDocument("", "IDLE", 0, "", "", null, null, emptyList(), emptyList(), false)
    }
}
