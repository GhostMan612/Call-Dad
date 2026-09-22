// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// data/signaling/SignalingClient.kt — Contracts 2 & 3: per-call rooms
// Location: app/src/main/java/com/calldad/data/signaling/SignalingClient.kt
package com.calldad.data.signaling

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
        .setMaxAttempts(5)
        .build()

    suspend fun publishOffer(
        callId: String,
        offerSdp: String,
        callerUid: String,
        calleeUid: String
    ): Result<Int> = try {
        val newSeq = firestore.runTransaction(txOptions) { txn ->
            val ref = calls.document(callId)
            val snap = txn.get(ref)

            if (snap.getString("status") == "CONNECTED") {
                throw FirebaseFirestoreException(
                    "Peer busy",
                    FirebaseFirestoreException.Code.ABORTED
                )
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
            isFromCache = false
        )
    }
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
    val isFromCache: Boolean
) {
    companion object {
        val EMPTY = CallDocument("", "IDLE", 0, "", "", null, null, false)
    }
}
