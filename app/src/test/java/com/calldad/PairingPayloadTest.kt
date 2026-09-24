// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host-side tests: pairing QR payload validation (org.json on the test classpath).
package com.calldad

import com.calldad.pairing.PairingPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPayloadTest {

    private val own = "KIDTEST0001"
    private val peer = "DADTEST0001"
    private val nonce = "0f8fad5b-d9cb-469f-a165-70867728950e"

    @Test
    fun roundTrip() {
        val raw = PairingPayload(peer, nonce).encode()
        assertEquals(PairingPayload.Parsed.Ok(PairingPayload(peer, nonce)), PairingPayload.parse(raw, own))
    }

    @Test
    fun legacyV1_isAccepted_fcmIgnored() {
        val raw = """{"v":1,"uid":"$peer","fcm":"x","nonce":"$nonce"}"""
        assertTrue(PairingPayload.parse(raw, own) is PairingPayload.Parsed.Ok)
    }

    @Test
    fun ownCode_isRejected() {
        val raw = PairingPayload(own, nonce).encode()
        assertEquals(PairingPayload.Parsed.OwnCode, PairingPayload.parse(raw, own))
    }

    @Test
    fun wrongVersion() {
        val raw = """{"v":9,"uid":"$peer","nonce":"$nonce"}"""
        assertEquals(PairingPayload.Parsed.WrongVersion, PairingPayload.parse(raw, own))
    }

    @Test
    fun garbage_andBadFields_areInvalid() {
        assertEquals(PairingPayload.Parsed.Invalid, PairingPayload.parse("not json", own))
        assertEquals(
            PairingPayload.Parsed.Invalid,
            PairingPayload.parse("""{"v":2,"uid":"","nonce":"$nonce"}""", own)
        )
        assertEquals(
            PairingPayload.Parsed.Invalid,
            PairingPayload.parse("""{"v":2,"uid":"a_b_cdefghijk","nonce":"$nonce"}""", own)
        )
        assertEquals(
            PairingPayload.Parsed.Invalid,
            PairingPayload.parse("""{"v":2,"uid":"$peer","nonce":"<script>"}""", own)
        )
    }

    @Test
    fun sessionNonce_isSymmetric() {
        assertEquals(
            PairingPayload.sessionNonce("aaaaaaaa", "bbbbbbbb"),
            PairingPayload.sessionNonce("bbbbbbbb", "aaaaaaaa")
        )
    }
}
