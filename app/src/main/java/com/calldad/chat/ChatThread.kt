// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// chat/ChatThread.kt — the 1:1 Dad thread, receipts, idempotent ingest
// Location: app/src/main/java/com/calldad/chat/ChatThread.kt
package com.calldad.chat

/**
 * The Dad<->Kid text thread (SPEC_SHEET §2.3, BP-03). Pure Kotlin: no Android,
 * no Firebase. Everything that can be decided without a network is decided
 * here, which is where BP-03's G3 gate lives ("receipt-transition tests,
 * duplicate-ingest single-row").
 *
 * Two rules drove the design, and both are pinned by tests:
 *
 * 1. **Receipts never move backwards.** A DELIVERED message must not fall back
 *    to SENT because a snapshot arrived out of order, a listener re-fired from
 *    cache, or the phone woke from a day of sleep. Firestore replays a cached
 *    snapshot before the live one, so a naive "replace the list" reducer
 *    visibly flips a delivered message back to "Sending..." in front of the
 *    child. Hence [Receipt.atLeast] and the merges below.
 *
 * 2. **Ingest is idempotent.** The same document arriving twice is one row.
 *    [ChatThread.reduce] keys on id and merges rather than appending, so a
 *    reconnect, a cache replay, or a listener double-fire cannot duplicate a
 *    message. This is BP-03's "duplicate-ingest single-row" gate, and it is the
 *    most likely bug in a listener-driven list: append-on-change is what
 *    everyone writes first, and it is wrong under exactly the conditions a
 *    family app sees (kid's phone asleep, dad sends, kid opens the app).
 */
data class ChatMessage(
    val id: String,
    val fromUid: String,
    val body: String,
    val createdAtMs: Long,
    val deliveredAtMs: Long? = null,
    val readAtMs: Long? = null
) {
    val receipt: Receipt
        get() = when {
            readAtMs != null -> Receipt.READ
            deliveredAtMs != null -> Receipt.DELIVERED
            else -> Receipt.SENT
        }

    fun isFromMe(viewerUid: String): Boolean = fromUid == viewerUid
}

/** Sent -> Delivered -> Read. Monotonic. */
enum class Receipt {
    SENT, DELIVERED, READ;

    /** True when this receipt is at least as far along as [other]. */
    fun atLeast(other: Receipt): Boolean = ordinal >= other.ordinal
}

/** Pure reducer over the thread. Every function returns a new list. */
object ChatThread {

    /**
     * Folds freshly-observed documents into the thread.
     *
     * Order is (createdAtMs, id). Sorting on id as a tiebreak matters: two
     * messages written in the same millisecond would otherwise swap places on
     * every snapshot, and a child watching their own messages jump around is
     * exactly the "looks broken" feel the walkie-talkie already had to avoid.
     */
    fun reduce(
        existing: List<ChatMessage>,
        incoming: List<ChatMessage>,
        viewerUid: String
    ): List<ChatMessage> {
        val byId = LinkedHashMap<String, ChatMessage>(existing.size + incoming.size)
        for (m in existing) byId[m.id] = m
        for (m in incoming) {
            val prior = byId[m.id]
            byId[m.id] = if (prior == null) m else m.mergeReceiptFrom(prior)
        }
        return byId.values.sortedWith(compareBy({ it.createdAtMs }, { it.id }))
    }

    /** Marks delivered without ever un-delivering. */
    fun markDelivered(list: List<ChatMessage>, id: String, atMs: Long): List<ChatMessage> =
        list.map { if (it.id == id) it.copy(deliveredAtMs = it.deliveredAtMs ?: atMs) else it }

    /** Marks read. Reading implies delivery, so both are stamped. */
    fun markRead(list: List<ChatMessage>, id: String, atMs: Long): List<ChatMessage> =
        list.map {
            if (it.id != id) it
            else it.copy(
                deliveredAtMs = it.deliveredAtMs ?: atMs,
                readAtMs = it.readAtMs ?: atMs
            )
        }

    /**
     * Keeps whichever version of the receipt is further along. Used when the
     * same id is seen twice: a local optimistic copy carries no receipt fields
     * at all, and a cache snapshot can predate the delivery the server has
     * already recorded. Neither may erase what we already know.
     */
    internal fun ChatMessage.mergeReceiptFrom(prior: ChatMessage): ChatMessage {
        val priorWins = prior.receipt.atLeast(receipt)
        return if (priorWins) {
            copy(
                deliveredAtMs = deliveredAtMs ?: prior.deliveredAtMs,
                readAtMs = readAtMs ?: prior.readAtMs
            )
        } else {
            this
        }
    }
}
