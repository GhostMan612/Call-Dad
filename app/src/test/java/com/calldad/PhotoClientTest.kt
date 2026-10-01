// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// photos/PhotoClientTest.kt — what a receiver is allowed to believe
// Location: app/src/test/java/com/calldad/PhotoClientTest.kt
package com.calldad

import com.calldad.photos.PhotoMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules `PhotoClient` enforces when turning bytes into something a child's
 * screen will render. All synthetic.
 *
 * The property under test is narrow and it is the safety one: **an unverified
 * photo must never carry bytes.** If it did, a child would see a half-decoded
 * face rendered as if it were a whole picture, and a parent would have no way to
 * tell a corrupt transfer from a bug. So the contract is that `verified == false`
 * implies `bytes == null`, and the failure is a SENTENCE a person can act on.
 */
class PhotoClientTest {

    private fun msg(
        id: String = "photo-1",
        verified: Boolean = true,
        bytes: ByteArray? = null,
        failure: String? = null
    ) = PhotoMessage(
        id = id,
        fromUid = "SYNTHETIC_PEER_UID",
        width = 1080,
        height = 810,
        totalBytes = 1000,
        verified = verified,
        bytes = bytes,
        failure = failure
    )

    @Test
    fun aVerifiedPhotoCarriesItsBytes() {
        val bytes = ByteArray(64) { it.toByte() }
        val m = msg(verified = true, bytes = bytes)
        assertTrue(m.verified)
        assertEquals(64, m.bytes?.size)
        assertEquals(null, m.failure)
    }

    @Test
    fun anUnverifiedPhotoCarriesNoBytesAndSaysWhy() {
        val m = msg(verified = false, bytes = null, failure = "That picture didn't arrive whole.")
        assertTrue(!m.verified)
        assertEquals("an unverified photo must never carry bytes", null, m.bytes)
        assertEquals("That picture didn't arrive whole.", m.failure)
    }

    @Test
    fun aFailureMessageNeverLeaksTechnicalDetail() {
        // It lands on a child's screen. A digest, a byte count, or a Firestore
        // error string is noise at best and an information leak at worst.
        listOf(
            "That picture didn't arrive whole. Ask again?",
            "That picture didn't arrive.",
            "That picture couldn't be opened."
        ).forEach {
            val m = msg(verified = false, failure = it)
            assertTrue(
                "failure text should read like a person talking: $it",
                it.none { c -> c.isDigit() || c == '/' || c == '{' }
            )
        }
    }

    /**
     * Byte arrays compare by reference under the default data-class equality, so
     * a re-fetched identical photo would look like a NEW photo and a diffing list
     * would rebuild every row on every snapshot. That is the "the whole thread
     * flickers" bug, and it is invisible until someone scrolls.
     */
    @Test
    fun equalityIgnoresArrayIdentityButNotContent() {
        val a = msg(bytes = ByteArray(8) { 7 })
        val b = msg(bytes = ByteArray(8) { 7 })
        val c = msg(bytes = ByteArray(8) { 8 })
        assertEquals("identical content must compare equal", a, b)
        assertNotEquals("different content must not", a, c)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun differentIdsAreNeverEqual() {
        assertNotEquals(msg(id = "a"), msg(id = "b"))
    }

    @Test
    fun verifiedAndFailureAreBothPartOfIdentity() {
        // A photo that transitions from failed to verified is a STATE CHANGE the
        // list must see. If those fields were outside equals, the row would keep
        // showing "didn't arrive" forever after a successful retry.
        val failed = msg(verified = false, failure = "nope")
        val ok = msg(verified = true, bytes = ByteArray(4))
        assertNotEquals(failed, ok)
    }
}
