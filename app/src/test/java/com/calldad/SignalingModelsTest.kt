// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host-side tests: dependency-free signaling models and room logic.
package com.calldad

import com.calldad.data.signaling.CallRoom
import com.calldad.data.signaling.IceCandidate
import com.calldad.data.signaling.SdpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalingModelsTest {

    @Test
    fun fromWire_parsesBothRoles() {
        assertEquals(SdpType.OFFER, SdpType.fromWire("OFFER"))
        assertEquals(SdpType.ANSWER, SdpType.fromWire("ANSWER"))
    }

    @Test
    fun fromWire_isCaseInsensitive() {
        assertEquals(SdpType.OFFER, SdpType.fromWire("offer"))
        assertEquals(SdpType.ANSWER, SdpType.fromWire("answer"))
    }

    @Test
    fun fromWire_neverCrashesOnUnknown() {
        assertNull(SdpType.fromWire(null))
        assertNull(SdpType.fromWire(""))
        assertNull(SdpType.fromWire("PRANSWER"))
    }

    @Test
    fun iceCandidate_defaultsAreNull() {
        val c = IceCandidate(sdpCandidate = "candidate:1")
        assertNull(c.sdpMid)
        assertNull(c.sdpMLineIndex)
        assertNull(c.serverUrl)
    }

    @Test
    fun roomId_isOrderIndependent() {
        assertEquals(CallRoom.idFor("DADTEST0001", "KIDTEST0001"), CallRoom.idFor("KIDTEST0001", "DADTEST0001"))
        assertEquals("DADTEST0001_KIDTEST0001", CallRoom.idFor("KIDTEST0001", "DADTEST0001"))
    }

    @Test
    fun roomId_membersRoundTrip() {
        val id = CallRoom.idFor("DADTEST0001", "KIDTEST0001")!!
        assertEquals(setOf("DADTEST0001", "KIDTEST0001"), CallRoom.members(id).toSet())
    }

    @Test
    fun roomId_rejectsSelfBlankAndSeparator() {
        assertNull(CallRoom.idFor("DADTEST0001", "DADTEST0001"))
        assertNull(CallRoom.idFor("", "KIDTEST0001"))
        assertNull(CallRoom.idFor("DAD_TEST", "KIDTEST0001"))
    }

    @Test
    fun freshRing_windowAndUnknownTimestamps() {
        val now = 1_000_000_000L
        assertTrue(CallRoom.isFreshRing(now, now))
        assertTrue(CallRoom.isFreshRing(now - CallRoom.RING_FRESH_MS, now))
        assertFalse(CallRoom.isFreshRing(now - CallRoom.RING_FRESH_MS - 1, now))
        assertTrue(CallRoom.isFreshRing(now + 5_000, now))
        assertFalse(CallRoom.isFreshRing(null, now))
    }
}
