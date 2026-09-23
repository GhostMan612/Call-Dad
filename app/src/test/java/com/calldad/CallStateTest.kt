// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Contract 7 host-side test: 7-state call machine (dependency-free).
package com.calldad

import com.calldad.ui.screens.CallState
import com.calldad.ui.screens.CallErrorKind
import com.calldad.ui.screens.EndReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateTest {

    @Test
    fun fromDocument_ringingPassesThrough() {
        val s = CallState.fromDocument("RINGING", 7, true, "Mama", "fam")
        assertTrue(s is CallState.Ringing)
        s as CallState.Ringing
        assertEquals(7, s.seq)
        assertEquals(true, s.isIncoming)
        assertEquals("Mama", s.peerName)
        assertEquals("fam", s.callId)
    }

    @Test
    fun fromDocument_connectedCarriesSeq() {
        val s = CallState.fromDocument("CONNECTED", 3, false, "Dad", "fam")
        assertEquals(CallState.Connected(3), s)
    }

    @Test
    fun fromDocument_terminalStates() {
        assertEquals(CallState.Declined, CallState.fromDocument("DECLINED", 1, false, "", ""))
        assertEquals(
            CallState.Ended(EndReason.REMOTE_HANGUP),
            CallState.fromDocument("ENDED", 1, false, "", "")
        )
        assertEquals(CallState.Idle, CallState.fromDocument("IDLE", 0, false, "", ""))
    }

    @Test
    fun fromDocument_unknownIsMalformedError() {
        val s = CallState.fromDocument("HOLD", 9, false, "", "")
        assertTrue(s is CallState.Error)
        s as CallState.Error
        assertEquals(CallErrorKind.MALFORMED, s.kind)
        assertEquals(9, s.seq)
    }
}
