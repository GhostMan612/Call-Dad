// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ReconnectRegressionTest.kt — the bugs found by the 2026-10-01 audit
// Location: app/src/test/java/com/calldad/ReconnectRegressionTest.kt
package com.calldad

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every assertion here corresponds to a bug that SHIPPED GREEN.
 *
 * All of them passed every gate in the repo at the time. That is the point of
 * this file: the standing suite said a feature worked, and a careful read found
 * five ways it did not. They are collected here so a future edit that
 * reintroduces any of them has to argue with a named regression rather than with
 * a general sense of caution.
 *
 * `IceRestartTest` covers the SHAPE of the reconnect. This file covers the
 * specific defects the audit found in that shape.
 */
class ReconnectRegressionTest {

    private val vm = File("src/main/java/com/calldad/ui/screens/CallViewModel.kt").readText()
    private val signaling = File("src/main/java/com/calldad/data/signaling/SignalingClient.kt").readText()

    private fun codeOnly(src: String): String = Regex("""//[^\n]*""").replace(src, "")
    private fun body(src: String, signature: String): String =
        codeOnly(src.substringAfter(signature).substringBefore("\n    }"))

    /**
     * BUG 1 (CRITICAL): the caller applied the PREVIOUS answer to its own restart
     * offer.
     *
     * `publishRenegotiation` swapped the offer but LEFT the round-0 handshake
     * answer in the document. Firestore echoes the caller's own write straight
     * back, so the echoed document carried the new round-1 offer beside the OLD
     * answer; the caller applied that stale answer, marked round 1 consumed, and
     * then discarded the real answer because it now looked like a duplicate. The
     * restart could not complete on any round, ever.
     */
    @Test
    fun publishingARestartMustINVALIDATETheStaleAnswer() {
        val publish = body(signaling, "suspend fun publishRenegotiation(")
        assertTrue(
            "publishRenegotiation must DELETE the previous answer. A round number and " +
                "an answer SDP cannot share a field without being invalidated " +
                "together, or the caller applies the pre-restart ufrag to its new " +
                "offer. Body: " + publish,
            publish.contains("FieldValue.delete()")
        )
    }

    @Test
    fun theCallerRefusesAnAnswerWhileItsOwnOfferIsOutstanding() {
        val apply = body(vm, "private fun applyRenegotiationAnswer(")
        assertTrue(
            "applyRenegotiationAnswer must bail while `renegotiating` is still true: " +
                "such a document is one where our own offer is outstanding and any " +
                "answer beside it is from BEFORE the restart. Body: " + apply,
            apply.contains("doc.renegotiating")
        )
    }

    /**
     * BUG 2 (HIGH): `restartInFlight` was released in only two places, so a
     * restart whose answer was lost held the latch for the rest of the call. The
     * documented behaviour was "a handful of offers" over the grace window; the
     * real behaviour was exactly ONE, ever -- indistinguishable from the feature
     * not existing.
     */
    @Test
    fun aLostAnswerReleasesTheInFlightLatch() {
        val restart = body(vm, "private fun attemptIceRestart(")
        assertTrue(
            "a published restart whose answer never arrives must release the latch " +
                "after a timeout, or a dead-zone phone gets exactly one attempt. Body: " +
                restart,
            restart.contains("restartTimeoutJob") &&
                restart.contains("RESTART_ANSWER_TIMEOUT_MS")
        )
        assertTrue(
            "the timeout must be defined, and comfortably longer than a Firestore " +
                "round trip on mobile data",
            vm.contains("const val RESTART_ANSWER_TIMEOUT_MS")
        )
    }

    @Test
    fun theAnswerPathCancelsTheWatchdog() {
        val apply = body(vm, "private fun applyRenegotiationAnswer(")
        assertTrue(
            "arriving at the answer must cancel the watchdog and release the latch, " +
                "or a stale timeout fires during a LATER round. Body: " + apply,
            apply.contains("restartTimeoutJob?.cancel()") &&
                apply.contains("restartInFlight = false")
        )
    }

    /**
     * BUG 3 (HIGH): `everConnected` was reset only inside `recordCallOutcome`.
     * Two exit paths -- a listener `fail()` and a pairing change -- write a
     * terminal state without going through that funnel, so the flag survived: call
     * 1 connects, fails silently, the child retries, that call RINGS OUT, and the
     * stale `true` logged it as ANSWERED with zero duration. The parent's history
     * claimed a call that never happened and the callback card was suppressed for
     * the miss that did.
     */
    @Test
    fun everConnectedResetsAtTheNewAttemptBoundary() {
        val begin = body(vm, "private fun beginGeneration(")
        assertTrue(
            "everConnected must reset where a NEW attempt begins, not only where a " +
                "row is written -- the funnel is not the only exit. Body: " + begin,
            begin.contains("everConnected = false")
        )
    }

    /**
     * BUG 4 (HIGH, latent): the no-answer and transport-failure paths assigned
     * `_state.value` DIRECTLY, bypassing `commitTerminal` and therefore the call
     * log. So `CallOutcome.MISSED` was unreachable dead code and the missed-call
     * callback card -- the entire reason `CallLog` exists -- could never appear.
     */
    @Test
    fun everyTerminalStateGoesThroughTheLogFunnel() {
        val noAnswer = body(vm, "private fun startNoAnswerTimer(")
        assertTrue(
            "no-answer must route through commitTerminal, not assign the state " +
                "directly, or a missed call is never logged. Body: " + noAnswer,
            noAnswer.contains("commitTerminal(CallState.NoAnswer(")
        )
        assertTrue(
            "no-answer must NOT write the state directly any more",
            !noAnswer.contains("_state.value = CallState.NoAnswer(")
        )
        val fail = body(vm, "private fun fail(")
        assertTrue(
            "a transport failure must route through commitTerminal too. Body: " + fail,
            fail.contains("commitTerminal(CallState.Error(")
        )
        assertTrue(
            "fail() must not write the state directly any more",
            !fail.contains("_state.value = CallState.Error(")
        )
    }

    @Test
    fun theFunnelStillRecordsEveryOutcome() {
        val record = body(vm, "private fun recordCallOutcome(")
        listOf("CallOutcome.ANSWERED", "CallOutcome.DECLINED", "CallOutcome.MISSED", "CallOutcome.FAILED")
            .forEach {
                assertTrue(
                    "recordCallOutcome must still map to $it -- a name that is present " +
                        "but unreachable is how MISSED stayed dead. Body: $record",
                    record.contains(it)
                )
            }
    }
}
