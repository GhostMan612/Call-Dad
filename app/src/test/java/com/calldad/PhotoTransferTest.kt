// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// PhotoTransferTest.kt — BP-04 §Gates: photo byte-proof, host-only
// Location: app/src/test/java/com/calldad/PhotoTransferTest.kt
package com.calldad

import com.calldad.photos.PhotoPolicy
import com.calldad.photos.PhotoTransfer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic bytes only (RULES §1.5a). A deterministic generator, not
 * `Random`, so a failure is reproducible from the seed alone.
 */
class PhotoTransferTest {

    private fun synthBytes(n: Int, seed: Int = 7): ByteArray =
        ByteArray(n) { i -> (((i * 31 + seed) % 251) - 125).toByte() }

    // ---------- the byte-proof: split then reassemble is the identity ----------

    @Test
    fun aSmallPhotoSurvivesTheRoundTripByteForByte() {
        val original = synthBytes(1_000)
        val split = PhotoTransfer.split(original) as PhotoTransfer.Split.Ok
        val back = PhotoTransfer.reassemble(split.chunks, split.totalBytes, split.sha256)
                as PhotoTransfer.Reassembled.Ok
        assertArrayEquals(original, back.bytes)
        assertEquals(original.size, back.bytesCount)
    }

    @Test
    fun aMultiChunkPhotoSurvivesTheRoundTripByteForByte() {
        // Larger than one chunk, so the boundary logic is actually exercised.
        val original = synthBytes(PhotoTransfer.CHUNK_BYTES * 3 + 17, seed = 3)
        val split = PhotoTransfer.split(original) as PhotoTransfer.Split.Ok
        assertEquals(4, split.chunks.size)
        val back = PhotoTransfer.reassemble(split.chunks, split.totalBytes, split.sha256)
                as PhotoTransfer.Reassembled.Ok
        assertArrayEquals(original, back.bytes)
    }

    @Test
    fun everyChunkIsWithinTheDocumentLimit() {
        val split = PhotoTransfer.split(synthBytes(PhotoTransfer.MAX_TOTAL_BYTES))
                as PhotoTransfer.Split.Ok
        split.chunks.forEach {
            assertTrue("chunk of ${it.size}", it.size <= PhotoTransfer.CHUNK_BYTES)
        }
    }

    // ---------- ordering ----------

    @Test
    fun chunksAreOrderedByIndexNotByArrival() {
        // What a listener actually hands us: out of order, keyed by doc id.
        val received = mapOf(
            "0002" to byteArrayOf(3),
            "0000" to byteArrayOf(1),
            "0003" to byteArrayOf(4),
            "0001" to byteArrayOf(2)
        )
        val ordered = PhotoTransfer.orderByIndex(received)
        assertArrayEquals(
            listOf<Byte>(1, 2, 3, 4).map { it.toByte() }.toByteArray(),
            ordered.fold(ByteArray(0)) { acc, b -> acc + b }
        )
    }

    @Test
    fun tenOrMoreChunksStillSortNumerically() {
        // The ids are zero-padded to FOUR places, and that padding is the whole
        // reason the sort works: "0009" < "0010" lexicographically, whereas an
        // unpadded "9" would sort AFTER "10" and a photo would reassemble into
        // noise. The 12-chunk case is here because the first break is at 10.
        val received = (0 until 12).associate { "%04d".format(it) to byteArrayOf(it.toByte()) }
        val ordered = PhotoTransfer.orderByIndex(received)
        assertEquals((0 until 12).toList(), ordered.map { it[0].toInt() })
    }

    @Test
    fun anUnpaddedIdWouldBreakTheSort() {
        // The counter-test: proves the padding is load-bearing rather than
        // decorative. If someone "simplifies" the ids to plain numbers, this is
        // the assertion that fails.
        val received = (0 until 12).associate { "$it" to byteArrayOf(it.toByte()) }
        val ordered = PhotoTransfer.orderByIndex(received)
        assertTrue(
            "unpadded ids are expected to misorder -- that is why they are padded",
            ordered.map { it[0].toInt() } != (0 until 12).toList()
        )
    }

    @Test
    fun expectedIdsAreZeroPadded() {
        assertEquals(
            listOf("0000", "0001", "0002"),
            PhotoTransfer.expectedChunkIds(3)
        )
        assertEquals("0099", PhotoTransfer.expectedChunkIds(100)[99])
    }

    // ---------- verification ----------

    @Test
    fun aMissingChunkIsCaughtByTheLengthCheck() {
        val split = PhotoTransfer.split(synthBytes(PhotoTransfer.CHUNK_BYTES * 2))
                as PhotoTransfer.Split.Ok
        val lossy = split.chunks.dropLast(1)
        val r = PhotoTransfer.reassemble(lossy, split.totalBytes, split.sha256)
        assertTrue(r is PhotoTransfer.Reassembled.Corrupt)
        assertTrue((r as PhotoTransfer.Reassembled.Corrupt).why.contains("expected"))
    }

    @Test
    fun aFlippedByteIsCaughtByTheDigest() {
        // Right length, wrong content. This is the case a length check alone
        // waves through and the child sees as noise.
        val split = PhotoTransfer.split(synthBytes(5_000)) as PhotoTransfer.Split.Ok
        val tampered = split.chunks.map { it.copyOf() }.onEach { c ->
            c[0] = (c[0] + 1).toByte()
        }
        val r = PhotoTransfer.reassemble(tampered, split.totalBytes, split.sha256)
        assertTrue(r is PhotoTransfer.Reassembled.Corrupt)
        assertEquals("checksum did not match", (r as PhotoTransfer.Reassembled.Corrupt).why)
    }

    @Test
    fun reorderedChunksFailTheDigest() {
        val split = PhotoTransfer.split(synthBytes(PhotoTransfer.CHUNK_BYTES * 2))
                as PhotoTransfer.Split.Ok
        val swapped = split.chunks.reversed()
        val r = PhotoTransfer.reassemble(swapped, split.totalBytes, split.sha256)
        assertTrue(r is PhotoTransfer.Reassembled.Corrupt)
    }

    @Test
    fun noChunksIsACorruptTransferNotAnEmptyPhoto() {
        val r = PhotoTransfer.reassemble(emptyList(), 100, "deadbeef")
        assertEquals("no chunks arrived", (r as PhotoTransfer.Reassembled.Corrupt).why)
    }

    @Test
    fun anOversizedChunkIsRefused() {
        val r = PhotoTransfer.reassemble(
            listOf(ByteArray(PhotoTransfer.CHUNK_BYTES + 1)), 1, "x"
        )
        assertTrue((r as PhotoTransfer.Reassembled.Corrupt).why.contains("oversized"))
    }

    @Test
    fun theDigestIsTheKnownSha256OfTheEmptyishVector() {
        // A fixed vector so a future refactor cannot quietly change the digest
        // algorithm and every stored photo silently fails verification.
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            PhotoTransfer.sha256Hex("hello".toByteArray())
        )
    }

    // ---------- caps ----------

    @Test
    fun anOversizedPhotoIsRefusedWithItsSize() {
        val huge = synthBytes(PhotoTransfer.MAX_TOTAL_BYTES + 1)
        val r = PhotoTransfer.split(huge)
        assertTrue(r is PhotoTransfer.Split.TooLarge)
        assertEquals(huge.size, (r as PhotoTransfer.Split.TooLarge).bytes)
    }

    @Test
    fun anEmptyPhotoIsRefused() {
        assertTrue(PhotoTransfer.split(ByteArray(0)) is PhotoTransfer.Split.TooLarge)
    }

    @Test
    fun theTotalCapIsReachableWithoutExceedingTheChunkCount() {
        assertTrue(PhotoTransfer.MAX_TOTAL_BYTES <= PhotoTransfer.CHUNK_BYTES * PhotoTransfer.MAX_CHUNKS)
    }

    // ---------- the downscale policy ----------

    @Test
    fun aSmallPhotoIsNotUpscaled() {
        assertEquals(640 to 480, PhotoPolicy.targetSize(640, 480))
    }

    @Test
    fun theLongEdgeIsCappedAndTheAspectRatioSurvives() {
        val (w, h) = PhotoPolicy.targetSize(4032, 3024)
        assertEquals(PhotoPolicy.MAX_EDGE, maxOf(w, h))
        // 4:3 stays 4:3 to within a pixel of rounding.
        assertTrue(abs(w * 3 - h * 4) <= 2)
    }

    @Test
    fun portraitIsCappedOnItsLongEdgeToo() {
        val (w, h) = PhotoPolicy.targetSize(3024, 4032)
        assertEquals(PhotoPolicy.MAX_EDGE, maxOf(w, h))
        assertTrue(w < h)
    }

    @Test
    fun aDegenerateSourceDoesNotProduceAZeroEdge() {
        // coerceAtLeast(1) matters: a 1px-wide source scaled to nothing produces
        // a zero dimension, and Bitmap.createBitmap throws on that.
        assertEquals(1 to 1, PhotoPolicy.targetSize(1, 1))
        assertEquals(0 to 0, PhotoPolicy.targetSize(0, 10))
    }

    private fun abs(v: Int) = if (v < 0) -v else v
}
