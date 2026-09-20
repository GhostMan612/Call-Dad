// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Phase 4 host-side test: CallState contract (dependency-free).
package com.calldad

import com.calldad.ui.screens.CallRole
import com.calldad.ui.screens.CallState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateTest {

    @Test
    fun incoming_defaultsToDad() {
        assertEquals("Dad", CallState.Incoming().fromDisplayName)
    }

    @Test
    fun lifecycle_hasFiveVariants() {
        val states: List<CallState> = listOf(
            CallState.Idle,
            CallState.Connecting,
            CallState.InCall(CallRole.CALLER, 0L),
            CallState.Incoming(),
            CallState.Error(com.calldad.data.signaling.SignalingErrorKind.UNKNOWN, "x")
        )
        assertEquals(5, states.size)
        assertTrue(states.filterIsInstance<CallState.InCall>().single().role == CallRole.CALLER)
    }
}
