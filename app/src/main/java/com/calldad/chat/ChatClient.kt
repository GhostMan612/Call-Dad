// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// chat/ChatClient.kt — the Dad<->Kid thread in the pair's private room
// Location: app/src/main/java/com/calldad/chat/ChatClient.kt
package com.calldad.chat

import com.calldad.consent.ConsentScope
import com.calldad.data.session.FamilyPair
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicLong

/**
 * 1:1 text thread over the same pair room the call uses (SPEC_SHEET §2.3, BP-03).
 *
 * `calls/{roomId}/chat/{auto}` — the same pair-scoped boundary as the walkie-talkie
 * clips, so a stranger cannot read a child's messages (firestore.rules).
 *
 * Honest reporting is the point of this class, and the patterns are carried over
 * from the PTT engine's ADR-016 follow-up rather than reinvented:
 *
 *  - a send is AWAITED, so "Sent" is never shown for a write that is still
 *    queued, offline, or rejected by the rules;
 *  - a receipt only ever moves FORWARD (see [ChatThread]);
 *  - the local optimistic row is removed as soon as the send resolves, and the
 *    server row is reconciled by id, so a slow network cannot show the same
 *    message twice.
 *
 * DELIVERY is the honest part, and it is the one place this class deliberately
 * reports LESS than a parent might expect. A write resolving means the server
 * accepted the message; it does NOT mean the other phone has it, and Firestore
 * has no delivery-receipt primitive. So `deliveredAtMs` is stamped by the
 * RECEIVER, on the document it just read. A message nobody has opened stays
 * SENT forever, which is true. Stamping DELIVERED on write success would be the
 * conventional lie, and it is the lie that makes receipts untrustworthy in every
 * messenger that has shipped it.
 */
class ChatClient(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: Flow<List<ChatMessage>> = _messages.asStateFlow()

    private val _problem = MutableStateFlow<Problem?>(null)
    val problem: Flow<Problem?> = _problem.asStateFlow()

    private var registration: ListenerRegistration? = null
    private var pair: FamilyPair? = null
    private var scope: CoroutineScope? = null

    /** Ids this receiver has already stamped delivered. Deliver-once per id. */
    private val stampedDelivered = mutableSetOf<String>()

    /**
     * Whether inbound messages may be read (ADR-017). Defaults to FALSE —
     * absence denies. See [setInboundAllowed].
     */
    private var inboundAllowed = false

    private val localSeq = AtomicLong(0)

    /** A kid-safe problem string. Never a uid, timestamp, or Firestore message. */
    data class Problem(val message: String)

    fun start(pair: FamilyPair, scope: CoroutineScope) {
        this.pair = pair
        this.scope = scope
        listen()
    }

    fun stop() {
        registration?.remove()
        registration = null
        _messages.value = emptyList()
        _problem.value = null
        stampedDelivered.clear()
    }

    fun clearProblem() { _problem.value = null }

    /**
     * The receive-side consent gate (ADR-017).
     *
     * [send] checked the TEXT grant on every send, and `ChatScreen` hid the
     * thread without one — but the listener itself was never gated, so a
     * revoked child's phone kept pulling up to 200 messages out of the room.
     * Messages are far smaller than photos, so the cost is trivial; the
     * PRINCIPLE is not. "Turn everything off" has to mean the device stops
     * reading, not merely that the screen stops drawing. Anything else is a
     * switch that only changes what is visible, which is not what a parent
     * pressing it is being told.
     *
     * Defaults to FALSE, and re-subscribes on toggle so nothing that arrived
     * while the gate was shut is left as a permanent hole in the thread.
     */
    fun setInboundAllowed(allowed: Boolean) {
        if (inboundAllowed == allowed) return
        inboundAllowed = allowed
        if (!allowed) {
            _messages.value = emptyList()
            stampedDelivered.clear()
            _problem.value = null
        }
        pair?.let { listen() }
    }

    /**
     * Sends one already-validated line. [ChatText.validate] must have run first:
     * the scope check lives there so the consent decision and the send are one
     * step, and a caller cannot skip it by calling this directly with a string
     * it never checked.
     *
     * The returned row is SENT and stays SENT until the RECEIVER stamps it,
     * which is why no receipt field is set here.
     */
    suspend fun send(body: String, scopes: Set<ConsentScope>): Result<ChatMessage> {
        val p = pair ?: return Result.failure(IllegalStateException("not paired"))
        if (ConsentScope.TEXT !in scopes) {
            return Result.failure(IllegalStateException("no text consent"))
        }
        // Re-checked here, not only in the ViewModel and screen.
        //
        // The gate is a snapshot the UI read at some earlier moment, so a press
        // landing in the same frame a parent revokes TEXT would otherwise write
        // anyway — and because [send] adds an OPTIMISTIC row before the write
        // resolves, the child would have watched their message appear and then
        // vanish, with no explanation. The optimistic row is exactly what makes
        // the stale-gate window visible instead of merely wrong.
        if (!inboundAllowed) {
            return Result.failure(IllegalStateException("no text consent"))
        }
        val now = System.currentTimeMillis()
        val localId = "$LOCAL_PREFIX${now}-${localSeq.incrementAndGet()}"
        _messages.value = ChatThread.reduce(
            _messages.value,
            listOf(ChatMessage(localId, p.ownUid, body, now)),
            p.ownUid
        )
        return runCatching {
            val doc = firestore.collection("calls").document(p.roomId).collection("chat").add(
                mapOf(
                    "from" to p.ownUid,
                    "body" to body,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            WebRtcLog.transition("Chat message sent")
            // Swap the optimistic row for the real one. Still SENT: the other
            // phone has not read anything yet.
            val sent = ChatMessage(doc.id, p.ownUid, body, now)
            _messages.value = ChatThread
                .reduce(_messages.value.filterNot { it.id == localId }, listOf(sent), p.ownUid)
            scope?.launch { prune(p.roomId) }
            sent
        }.onFailure {
            // A message that never reached the server must not sit in the thread
            // looking sent: that is the "he said it and it vanished" bug, and it
            // teaches a child that words are unreliable.
            _messages.value = _messages.value.filterNot { m -> m.id == localId }
            _problem.value = Problem("Couldn't send that. Try again?")
            WebRtcLog.transition("Chat message send failed")
        }
    }

    /**
     * Marks every inbound message read. Called when the thread is on screen.
     *
     * Local first so the ticks do not wait on a round trip, then the server copy
     * so READ is durable and survives the app being killed.
     */
    fun markVisibleAsRead() {
        val p = pair ?: return
        val me = p.ownUid
        val now = System.currentTimeMillis()
        val unread = _messages.value.filter { it.fromUid != me && it.readAtMs == null }
        if (unread.isEmpty()) return
        var list = _messages.value
        for (m in unread) list = ChatThread.markRead(list, m.id, now)
        _messages.value = list
        unread.forEach { m -> updateReceipt(p.roomId, m.id, "readAt") }
    }

    private fun updateReceipt(roomId: String, id: String, field: String) {
        runCatching {
            firestore.collection("calls").document(roomId).collection("chat")
                .document(id)
                .update(field, FieldValue.serverTimestamp())
        }.onFailure {
            // Not fatal. The local state is already correct and the next
            // snapshot re-derives it; surfacing a receipt write failure to a
            // child would be noise about something they cannot act on.
            WebRtcLog.transition("Chat receipt write failed")
        }
    }

    private fun listen() {
        registration?.remove()
        registration = null
        val p = pair ?: return
        // Revocation can land while the listener is already attached, so this is
        // checked here as well as in [setInboundAllowed] — otherwise a live
        // listener keeps delivering into a thread the screen has stopped showing.
        if (!inboundAllowed) {
            WebRtcLog.transition("Chat receive gated off")
            return
        }
        // DESCENDING + limit, so the window is the NEWEST 200. This was ASCENDING,
        // which made the window the OLDEST 200 while [prune] correctly kept the
        // newest 200 — so once a thread passed 200 messages the newest ones fell
        // outside the listener entirely: the child could send message 205 and see
        // 1-200 on screen. The reducer still sorts ascending, so display order is
        // unaffected; only the window is wrong.
        registration = firestore.collection("calls").document(p.roomId).collection("chat")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_WINDOW.toLong())
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    // A dead thread must not be indistinguishable from a silent
                    // parent. Silence is the real defect.
                    _problem.value = Problem("Couldn't get messages. Try again.")
                    WebRtcLog.transition("Chat listener failed")
                    return@addSnapshotListener
                }
                if (snap.metadata.isFromCache) return@addSnapshotListener
                val rows = snap.documents.mapNotNull { it.toChatMessage() }
                val serverIds = rows.mapTo(HashSet()) { it.id }
                // Keep only what the server has not accounted for, so an
                // optimistic row survives exactly until its real doc lands.
                val carried = _messages.value.filter { it.id !in serverIds }
                val merged = ChatThread.reduce(carried, rows, p.ownUid)
                _messages.value = merged
                stampDelivered(p, merged, rows)
            }
    }

    /**
     * DELIVERED means "this device has it", so it is stamped here, by the
     * receiver, and only once per id.
     */
    private fun stampDelivered(
        p: FamilyPair,
        merged: List<ChatMessage>,
        rows: List<ChatMessage>
    ) {
        val fresh = rows.filter { it.fromUid == p.peerUid && stampedDelivered.add(it.id) }
        if (fresh.isEmpty()) return
        val now = System.currentTimeMillis()
        var list = merged
        for (m in fresh) list = ChatThread.markDelivered(list, m.id, now)
        _messages.value = list
        fresh.forEach { m -> updateReceipt(p.roomId, m.id, "deliveredAt") }
    }

    /**
     * Bounds the thread.
     *
     * A chat row is a few hundred bytes, so the window is generous and there is
     * deliberately NO age grace — a parent away for a month does not lose the
     * month. Only READ history is dropped, and only past the window: an unread
     * message is never deleted, because "it disappeared" is worse than a long
     * thread, and a child asking about a message Dad thinks he sent is a bug
     * with no good answer.
     */
    private suspend fun prune(roomId: String) {
        runCatching {
            val docs = firestore.collection("calls").document(roomId).collection("chat")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get().await().documents
            docs.drop(MAX_WINDOW).forEach { drop ->
                if (drop.getTimestamp("readAt") != null) {
                    runCatching { drop.reference.delete() }
                }
            }
        }.onFailure {
            // Never surfaced: the message WAS sent. A failed prune is storage
            // pressure, not a lost message, and must not make the child think
            // their words did not go through.
            WebRtcLog.transition("Chat prune skipped")
        }
    }

    private fun DocumentSnapshot.toChatMessage(): ChatMessage? {
        // A document with a pending local write is the optimistic echo; the
        // caller already holds that row, and taking both is the duplicate this
        // class exists to prevent.
        if (metadata.hasPendingWrites()) return null
        val from = getString("from") ?: return null
        val body = getString("body") ?: return null
        return ChatMessage(
            id = id,
            fromUid = from,
            body = body,
            createdAtMs = getTimestamp("createdAt")?.toDate()?.time
                ?: getLong("createdAt")?.toLong()
                ?: 0L,
            deliveredAtMs = getTimestamp("deliveredAt")?.toDate()?.time,
            readAtMs = getTimestamp("readAt")?.toDate()?.time
        )
    }

    private companion object {
        const val LOCAL_PREFIX = "local-"
        const val MAX_WINDOW = 200
    }
}
