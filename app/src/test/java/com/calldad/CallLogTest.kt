// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// CallLogTest.kt — BP-05 §4 missed-call callback + SPEC_SHEET §2.5 history
// Location: app/src/test/java/com/calldad/CallLogTest.kt
package com.calldad

import com.calldad.history.CallLog
import com.calldad.history.CallOutcome
import com.calldad.history.CallRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogTest {

    private val now = 1_000_000_000L

    private fun rec(
        id: String,
        at: Long = now,
        outcome: CallOutcome = CallOutcome.ANSWERED,
        outgoing: Boolean = true,
        durationMs: Long = 0L
    ) = CallRecord(id, at, durationMs, outcome, outgoing)

    // ---------- who gets a callback card ----------

    @Test
    fun aMissedCallIsTheCallbackCase() {
        assertTrue(CallOutcome.MISSED.wantsCallback)
    }

    @Test
    fun aFailedCallIsAlsoTheCallbackCase() {
        // The child's tap produced nothing, so the same promise applies whether
        // nobody picked up or the transport failed.
        assertTrue(CallOutcome.FAILED.wantsCallback)
    }

    @Test
    fun aDeclineIsNotACallbackCase() {
        // A grown-up who declined is saying they are busy. Re-ringing them
        // because a child asked again is the app nagging on someone's behalf.
        assertFalse(CallOutcome.DECLINED.wantsCallback)
    }

    @Test
    fun anAnsweredCallIsNotACallbackCase() {
        assertFalse(CallOutcome.ANSWERED.wantsCallback)
    }

    // ---------- the card itself ----------

    @Test
    fun thereIsNoCardWhenNothingWasMissed() {
        assertNull(CallLog.callbackCard(listOf(rec("a", outcome = CallOutcome.ANSWERED)), now))
    }

    @Test
    fun theMostRecentMissRaisesExactlyOneCard() {
        val rows = listOf(
            rec("old", at = now - 60_000, outcome = CallOutcome.MISSED),
            rec("new", at = now - 1_000, outcome = CallOutcome.MISSED)
        )
        val card = CallLog.callbackCard(rows, now)
        // One card, not two: choosing between two moments is not a decision a
        // 6-year-old can make and should not be handed.
        assertEquals("new", card?.id)
    }

    @Test
    fun aCardIgnoresAnAnsweredCallThatIsMoreRecent() {
        val rows = listOf(
            rec("missed", at = now - 60_000, outcome = CallOutcome.MISSED),
            rec("answered", at = now - 1_000, outcome = CallOutcome.ANSWERED)
        )
        // The newest OUTCOME decides, not the newest row: answering after a miss
        // means the child got through and the card is noise.
        assertEquals("missed", CallLog.callbackCard(rows, now)?.id)
    }

    @Test
    fun aStaleMissDoesNotRaiseACard() {
        // A Home screen still saying "Dad didn't answer" a week later reads as
        // the app being broken, not as history.
        val old = rec("a", at = now - CallLog.CARD_WINDOW_MS - 1, outcome = CallOutcome.MISSED)
        assertNull(CallLog.callbackCard(listOf(old), now))
    }

    @Test
    fun aMissAtTheEdgeOfTheWindowStillCounts() {
        val edge = rec("a", at = now - CallLog.CARD_WINDOW_MS, outcome = CallOutcome.MISSED)
        assertEquals("a", CallLog.callbackCard(listOf(edge), now)?.id)
    }

    // ---------- dismiss ----------

    @Test
    fun dismissingRemovesTheCardButKeepsHistory() {
        val rows = listOf(
            rec("missed", at = now - 1_000, outcome = CallOutcome.MISSED),
            rec("answered", at = now - 2_000, outcome = CallOutcome.ANSWERED)
        )
        val after = CallLog.dismissCallback(rows, now)
        // The card is gone and the answered call is still there.
        assertNull(CallLog.callbackCard(after, now))
        assertEquals(2, after.size)
    }

    @Test
    fun dismissingDoesNotDELETETheMissedRow() {
        // The original `clearCallbacks` FILTERED the rows out while its own KDoc
        // promised "history stays" and `CallLogStore` promised the same. One tap on
        // the card would have silently erased every miss in the window from the
        // history screen and the parent would never know. It was latent only
        // because the card was never raised (the no-answer path bypassed the log
        // funnel), so fixing that bug would have activated this one.
        val rows = listOf(rec("missed", at = now - 1_000, outcome = CallOutcome.MISSED))
        val after = CallLog.dismissCallback(rows, now)
        assertEquals("the missed row must survive being dismissed", 1, after.size)
        assertEquals(CallOutcome.MISSED, after[0].outcome)
        assertEquals("it must be marked dismissed, not deleted", now, after[0].dismissedAtMs)
    }

    @Test
    fun aDismissedRowNeverOffersTheCardAgain() {
        var rows = listOf(rec("missed", at = now - 1_000, outcome = CallOutcome.MISSED))
        assertEquals("missed", CallLog.callbackCard(rows, now)?.id)
        rows = CallLog.dismissCallback(rows, now)
        assertNull(CallLog.callbackCard(rows, now))
        // Still suppressed as time passes: the dismissal is permanent.
        assertNull(CallLog.callbackCard(rows, now + CallLog.CARD_WINDOW_MS))
    }

    @Test
    fun aNEWERMissStillOffersACardAfterAnOlderOneWasDismissed() {
        // Suppression is per-ROW, not global. Otherwise acting on one card would
        // permanently silence the callback feature for the rest of the window.
        var rows = listOf(rec("old", at = now - 60_000, outcome = CallOutcome.MISSED))
        rows = CallLog.dismissCallback(rows, now)
        rows = CallLog.add(rows, rec("new", at = now - 500, outcome = CallOutcome.MISSED))
        assertEquals("new", CallLog.callbackCard(rows, now)?.id)
    }

    @Test
    fun dismissingWithNoCardIsANoOp() {
        val rows = listOf(rec("answered", at = now - 1_000, outcome = CallOutcome.ANSWERED))
        assertEquals(rows, CallLog.dismissCallback(rows, now))
    }

    @Test
    fun aStaleMissIsNeverOfferedEvenWithoutADismissal() {
        val stale = rec("a", at = now - CallLog.CARD_WINDOW_MS - 1, outcome = CallOutcome.MISSED)
        assertNull(CallLog.callbackCard(listOf(stale), now))
        // And dismissing it changes nothing, because there was nothing to dismiss.
        assertEquals(1, CallLog.dismissCallback(listOf(stale), now).size)
    }

    // ---------- the log itself ----------

    @Test
    fun rowsAreNewestFirst() {
        val rows = listOf(
            rec("a", at = 3), rec("c", at = 1), rec("b", at = 2)
        )
        assertEquals(listOf("a", "b", "c"), CallLog.ordered(rows).map { it.id })
    }

    @Test
    fun equalTimestampsDoNotFlap() {
        val rows = listOf(rec("b", at = 5), rec("a", at = 5), rec("c", at = 5))
        val once = CallLog.ordered(rows).map { it.id }
        val twice = CallLog.ordered(rows.reversed()).map { it.id }
        assertEquals(once, twice)
    }

    @Test
    fun addingTheSameIdTwiceReplacesRatherThanDuplicates() {
        var rows = listOf(rec("a", at = 1, outcome = CallOutcome.MISSED))
        rows = CallLog.add(rows, rec("a", at = 1, outcome = CallOutcome.ANSWERED))
        assertEquals(1, rows.size)
        assertEquals(CallOutcome.ANSWERED, rows[0].outcome)
    }

    @Test
    fun theLogIsBoundedAndDropsTheOldestFirst() {
        var rows = emptyList<CallRecord>()
        repeat(CallLog.MAX_ROWS + 25) { i ->
            rows = CallLog.add(rows, rec("id-$i", at = i.toLong()))
        }
        assertEquals(CallLog.MAX_ROWS, rows.size)
        // Newest kept, oldest gone.
        assertEquals("id-${CallLog.MAX_ROWS + 24}", rows.first().id)
        assertEquals("id-25", rows.last().id)
    }

    @Test
    fun anIncomingMissAlsoRaisesACard() {
        // The kid's phone is asleep, dad calls, the child never hears it. That is
        // a miss on their side too and they deserve the same way to try again.
        val rows = listOf(rec("in", at = now - 5_000, outcome = CallOutcome.MISSED, outgoing = false))
        assertEquals("in", CallLog.callbackCard(rows, now)?.id)
    }
}
