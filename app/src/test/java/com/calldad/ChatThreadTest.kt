// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ChatThreadTest.kt — BP-03 §Gates: receipt transitions + duplicate-ingest
// Location: app/src/test/java/com/calldad/ChatThreadTest.kt
package com.calldad

import com.calldad.chat.ChatMessage
import com.calldad.chat.ChatThread
import com.calldad.chat.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic ids only. See RULES §1.5a. */
class ChatThreadTest {

    private val kid = "synthetic-kid-uid"
    private val dad = "synthetic-dad-uid"

    private fun msg(
        id: String,
        from: String = dad,
        body: String = "hi",
        at: Long = 1_000L,
        delivered: Long? = null,
        read: Long? = null
    ) = ChatMessage(id, from, body, at, delivered, read)

    // ---------- receipts: monotonic ----------

    @Test
    fun receiptIsDerivedFromTheFieldsPresent() {
        assertEquals(Receipt.SENT, msg("a").receipt)
        assertEquals(Receipt.DELIVERED, msg("a", delivered = 2L).receipt)
        assertEquals(Receipt.READ, msg("a", delivered = 2L, read = 3L).receipt)
    }

    @Test
    fun aReadMessageIsAlsoAtLeastDeliveredAndSent() {
        val read = Receipt.READ
        assertTrue(read.atLeast(Receipt.READ))
        assertTrue(read.atLeast(Receipt.DELIVERED))
        assertTrue(read.atLeast(Receipt.SENT))
        assertFalse(Receipt.SENT.atLeast(Receipt.DELIVERED))
    }

    @Test
    fun markingDeliveredTwiceKeepsTheFirstTimestamp() {
        val once = ChatThread.markDelivered(listOf(msg("a")), "a", 5_000L)
        val twice = ChatThread.markDelivered(once, "a", 9_000L)
        // A second delivery event (reconnect, re-listener) must not move the
        // clock, or the receipt the child sees would change retroactively.
        assertEquals(5_000L, twice[0].deliveredAtMs)
    }

    @Test
    fun markingReadImpliesDelivery() {
        val read = ChatThread.markRead(listOf(msg("a")), "a", 7_000L)
        assertEquals(7_000L, read[0].deliveredAtMs)
        assertEquals(7_000L, read[0].readAtMs)
        assertEquals(Receipt.READ, read[0].receipt)
    }

    @Test
    fun aCachedSnapshotWithoutTheReceiptCannotUndoADelivery() {
        // The real sequence: we delivered, then a cache replay arrives with the
        // pre-delivery version of the doc. A replace-the-list reducer shows the
        // child "Sending..." for a message that already arrived.
        val delivered = listOf(msg("a", delivered = 4_000L))
        val stale = listOf(msg("a", delivered = null))
        val merged = ChatThread.reduce(delivered, stale, viewerUid = kid)
        assertEquals(1, merged.size)
        assertEquals(Receipt.DELIVERED, merged[0].receipt)
        assertEquals(4_000L, merged[0].deliveredAtMs)
    }

    @Test
    fun aNewerServerReceiptDoesWin() {
        val ours = listOf(msg("a", delivered = 4_000L))
        val fresher = listOf(msg("a", delivered = 6_000L, read = 6_500L))
        val merged = ChatThread.reduce(ours, fresher, viewerUid = kid)
        assertEquals(Receipt.READ, merged[0].receipt)
    }

    // ---------- idempotent ingest ----------

    @Test
    fun theSameDocumentTwiceIsOneRow() {
        val m = msg("a")
        val once = ChatThread.reduce(emptyList(), listOf(m), kid)
        val twice = ChatThread.reduce(once, listOf(m), kid)
        val thrice = ChatThread.reduce(twice, listOf(m), kid)
        assertEquals(1, thrice.size)
    }

    @Test
    fun aListenerThatFiresTwiceDoesNotDuplicateTheThread() {
        // The wake-from-sleep case: kid's phone was off, dad sent three, the
        // listener replays the same ADDED changes for all three.
        val batch = listOf(msg("a", at = 1L), msg("b", at = 2L), msg("c", at = 3L))
        val first = ChatThread.reduce(emptyList(), batch, kid)
        val second = ChatThread.reduce(first, batch, kid)
        assertEquals(3, second.size)
        assertEquals(listOf("a", "b", "c"), second.map { it.id })
    }

    @Test
    fun mergingIsOrderIndependent() {
        val batch = listOf(msg("a", at = 1L), msg("b", at = 2L))
        val forward = ChatThread.reduce(emptyList(), batch, kid)
        val backward = ChatThread.reduce(emptyList(), batch.reversed(), kid)
        assertEquals(forward.map { it.id }, backward.map { it.id })
    }

    // ---------- ordering ----------

    @Test
    fun messagesAreOrderedByTimeThenId() {
        val batch = listOf(
            msg("z", at = 5L), msg("a", at = 5L), msg("m", at = 1L)
        )
        val out = ChatThread.reduce(emptyList(), batch, kid)
        // Same millisecond: the id tiebreak keeps them from swapping on every
        // snapshot, which reads as "the app is broken" to a child.
        assertEquals(listOf("m", "a", "z"), out.map { it.id })
    }

    // ---------- direction ----------

    @Test
    fun directionIsRelativeToTheViewerNotStored() {
        val fromDad = msg("a", from = dad)
        val fromKid = msg("b", from = kid)
        assertTrue(fromDad.isFromMe(dad))
        assertFalse(fromDad.isFromMe(kid))
        assertTrue(fromKid.isFromMe(kid))
    }
}
