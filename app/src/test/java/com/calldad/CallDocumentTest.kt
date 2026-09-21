// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Phase 11 host-side tests: static-room model (dependency-free parts).
package com.calldad

import com.calldad.data.signaling.CallDocument
import com.calldad.data.signaling.CallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallDocumentTest {

    @Test
    fun status_parsesBothCases() {
        assertEquals(CallStatus.RINGING, CallStatus.fromWire("RINGING"))
        assertEquals(CallStatus.CONNECTED, CallStatus.fromWire("connected"))
        assertEquals(CallStatus.DECLINED, CallStatus.fromWire("Declined"))
        assertEquals(CallStatus.ENDED, CallStatus.fromWire("ended"))
        assertEquals(CallStatus.IDLE, CallStatus.fromWire("idle"))
    }

    @Test
    fun status_rejectsUnknown() {
        assertNull(CallStatus.fromWire(null))
        assertNull(CallStatus.fromWire(""))
        assertNull(CallStatus.fromWire("HOLD"))
    }

    @Test
    fun stale_onlyAppliesToRinging() {
        val now = System.currentTimeMillis()
        val ringingOld = CallDocument(CallStatus.RINGING, "o", null, 7, now - 61_000)
        val ringingFresh = CallDocument(CallStatus.RINGING, "o", null, 8, now)
        val ringingPending = CallDocument(CallStatus.RINGING, "o", null, 9, null)
        val connectedOld = CallDocument(CallStatus.CONNECTED, "o", "a", 10, now - 61_000)
        assertTrue(ringingOld.isStale(now))
        assertFalse(ringingFresh.isStale(now))
        assertFalse(ringingPending.isStale(now))
        assertFalse(connectedOld.isStale(now))
    }
}
