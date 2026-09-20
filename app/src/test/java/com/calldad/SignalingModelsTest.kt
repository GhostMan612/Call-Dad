// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Phase 2 host-side tests: dependency-free signaling models only.
// SignalingClient (Firebase) and CallViewModel (ViewModel) run in Studio.
package com.calldad

import com.calldad.data.signaling.IceCandidate
import com.calldad.data.signaling.OFFER_STALE_MS
import com.calldad.data.signaling.SdpType
import com.calldad.data.signaling.SessionDescription
import com.calldad.data.signaling.SignalingErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(null, c.sdpMid)
        assertEquals(null, c.sdpMLineIndex)
        assertEquals(null, c.serverUrl)
    }

    @Test
    fun errorKinds_coverOfflineContract() {
        val kinds = SignalingErrorKind.entries.map { it.name }.toSet()
        setOf("OFFLINE", "TIMEOUT", "NOT_FOUND", "PERMISSION_DENIED", "MALFORMED", "UNKNOWN")
            .forEach { assert(kinds.contains(it)) }
    }

    @Test
    fun staleOffer_neverAnswerable() {
        val now = System.currentTimeMillis()
        assert(SessionDescription(SdpType.OFFER, "s", now).isStale(now).not())
        assert(SessionDescription(SdpType.OFFER, "s", now - OFFER_STALE_MS - 1).isStale(now))
        assert(SessionDescription(SdpType.OFFER, "s", null).isStale(now))
    }
}
