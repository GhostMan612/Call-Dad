// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host-side tests: the call state machine's transition table.
package com.calldad

import com.calldad.ui.screens.CallErrorKind
import com.calldad.ui.screens.CallState
import com.calldad.ui.screens.EndReason
import com.calldad.ui.screens.isLive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateTransitionTest {

    private val ringingOut = CallState.Ringing(1, false, "Dad", "room")
    private val ringingIn = CallState.Ringing(1, true, "Dad", "room")
    private val connected = CallState.Connected(1)
    private val ended = CallState.Ended(EndReason.REMOTE_HANGUP)
    private val error = CallState.Error(CallErrorKind.UNKNOWN, "x")
    private val noAnswer = CallState.NoAnswer(1)

    private val all = listOf(
        CallState.Idle, ringingOut, ringingIn, connected,
        CallState.Declined, noAnswer, ended, error
    )

    @Test
    fun idle_onlyStartsRinging() {
        assertTrue(CallState.canTransition(CallState.Idle, ringingOut))
        assertTrue(CallState.canTransition(CallState.Idle, ringingIn))
        assertFalse(CallState.canTransition(CallState.Idle, connected))
        assertFalse(CallState.canTransition(CallState.Idle, ended))
    }

    @Test
    fun ringing_reachesEveryOutcome() {
        listOf(connected, CallState.Declined, noAnswer, ended, error).forEach {
            assertTrue(it.toString(), CallState.canTransition(ringingOut, it))
        }
    }

    @Test
    fun connected_canBeRerungOrEnded() {
        assertTrue(CallState.canTransition(connected, ringingIn))
        assertTrue(CallState.canTransition(connected, ended))
        assertTrue(CallState.canTransition(connected, error))
        assertFalse(CallState.canTransition(connected, noAnswer))
    }

    @Test
    fun everyNonIdleState_hasAWayOut() {
        all.filter { it != CallState.Idle }.forEach { from ->
            val exits = all.filter { to -> to::class != from::class && CallState.canTransition(from, to) }
            assertTrue("$from is a dead end", exits.isNotEmpty())
        }
    }

    @Test
    fun terminalStates_acceptANewRing() {
        listOf(CallState.Declined, ended, error, noAnswer).forEach {
            assertTrue(it.toString(), CallState.canTransition(it, ringingIn))
        }
    }

    @Test
    fun liveness() {
        assertTrue(ringingOut.isLive)
        assertTrue(connected.isLive)
        assertFalse(CallState.Idle.isLive)
        assertFalse(ended.isLive)
        assertFalse(noAnswer.isLive)
    }

    @Test
    fun recoverableErrors_offerTryAgain() {
        assertTrue(CallErrorKind.SIGNALING_FAILED.isRecoverable)
        assertTrue(CallErrorKind.WEBRTC_FAILED.isRecoverable)
        assertFalse(CallErrorKind.NOT_PAIRED.isRecoverable)
        assertFalse(CallErrorKind.PERMISSION_DENIED.isRecoverable)
    }
}
