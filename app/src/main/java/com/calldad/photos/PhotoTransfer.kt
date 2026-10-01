// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// photos/PhotoTransfer.kt — chunked, verified, byte-identical photo send
// Location: app/src/main/java/com/calldad/photos/PhotoTransfer.kt
package com.calldad.photos

import java.security.MessageDigest

/**
 * The transport half of photo sharing (SPEC_SHEET §2.4, BP-04), ported from the
 * donor `SovereignImageEngine` idiom: downscale -> WEBP cap -> chunk ->
 * reassemble -> VERIFY.
 *
 * Pure Kotlin on purpose. `Bitmap` and `Bitmap.compress` are Android, so the
 * pixel work lives in `PhotoEncoder` (implemented against Bitmap in the app, and
 * against a synthetic encoder in host tests). Everything that can go wrong on
 * the wire lives here and is host-testable, which is where BP-04's G4 gate
 * lives ("photo byte-proof (host)").
 *
 * WHY VERIFY AT ALL. Firestore gives us per-document size limits and a
 * transaction, not an end-to-end guarantee that 32 chunks arrived intact and in
 * order. Without a digest, a transfer that silently lost a chunk produces a
 * truncated image, and a truncated photo of a child is a mystery rather than an
 * error: it looks like a rendering bug to the parent and like a broken app to
 * the child. A SHA-256 over the reassembled bytes turns that into an explicit
 * "this didn't arrive whole, ask again".
 */
object PhotoTransfer {

    /**
     * Firestore's own document ceiling is ~10MB, but a photo is not a document:
     * it is N documents plus a parent. 256KB per chunk leaves room for the
     * field overhead and keeps every individual write well inside the limit on a
     * slow mobile connection.
     */
    const val CHUNK_BYTES = 256_000

    /** 800KB of payload after the WEBP cap, so at most 4 chunks in practice. */
    const val MAX_TOTAL_BYTES = 800_000

    /** Firestore limits subcollection depth by path segments; 32 is generous. */
    const val MAX_CHUNKS = 32

    sealed interface Split {
        data class Ok(val chunks: List<ByteArray>, val totalBytes: Int, val sha256: String) : Split
        data class TooLarge(val bytes: Int) : Split
    }

    /**
     * Splits encoded bytes into fixed chunks and digests the whole.
     *
     * The digest is over the CONCATENATED bytes, not per chunk, because a
     * per-chunk digest would only prove each piece arrived; it would not prove
     * they arrived in order. Concatenation order is therefore part of the
     * contract and the chunk index is written alongside each piece.
     */
    fun split(encoded: ByteArray): Split {
        if (encoded.isEmpty()) return Split.TooLarge(0)
        if (encoded.size > MAX_TOTAL_BYTES) return Split.TooLarge(encoded.size)
        val count = (encoded.size + CHUNK_BYTES - 1) / CHUNK_BYTES
        if (count > MAX_CHUNKS) return Split.TooLarge(encoded.size)
        val chunks = ArrayList<ByteArray>(count)
        var offset = 0
        while (offset < encoded.size) {
            val end = minOf(offset + CHUNK_BYTES, encoded.size)
            chunks.add(encoded.copyOfRange(offset, end))
            offset = end
        }
        return Split.Ok(chunks, encoded.size, sha256Hex(encoded))
    }

    sealed interface Reassembled {
        data class Ok(val bytes: ByteArray, val bytesCount: Int) : Reassembled
        data class Corrupt(val why: String) : Reassembled
    }

    /**
     * Rebuilds and verifies.
     *
     * Every failure mode is a distinct, specific `why` rather than one
     * "transfer failed", because the operator debugging a family phone needs to
     * tell "the network dropped it" from "the digest did not match and something
     * is actually wrong".
     */
    fun reassemble(
        chunks: List<ByteArray>,
        expectedTotal: Int,
        expectedSha: String
    ): Reassembled {
        if (chunks.isEmpty()) return Reassembled.Corrupt("no chunks arrived")
        if (chunks.size > MAX_CHUNKS) return Reassembled.Corrupt("too many chunks")
        if (chunks.any { it.size > CHUNK_BYTES }) {
            return Reassembled.Corrupt("a chunk was oversized")
        }
        val total = chunks.sumOf { it.size }
        if (total != expectedTotal) {
            return Reassembled.Corrupt("got $total bytes, expected $expectedTotal")
        }
        val joined = ByteArray(total)
        var offset = 0
        for (c in chunks) {
            c.copyInto(joined, offset)
            offset += c.size
        }
        val digest = sha256Hex(joined)
        if (!digest.equals(expectedSha, ignoreCase = true)) {
            // The important one: right length, wrong bytes. That is corruption,
            // not a short transfer, and it must not be retried silently.
            return Reassembled.Corrupt("checksum did not match")
        }
        return Reassembled.Ok(joined, joined.size)
    }

    /**
     * Orders received chunks by their Firestore document id.
     *
     * The chunk subcollection doc id IS the zero-padded index ("0000", "0001"),
     * so a lexicographic sort is a numeric sort. Relying on arrival order instead
     * is the classic way a photo arrives as noise: Firestore has no ordering
     * guarantee across concurrent writes, and a listener emits ADDED changes in
     * whatever order the server sends them.
     */
    fun orderByIndex(chunks: Map<String, ByteArray>): List<ByteArray> =
        chunks.entries
            .sortedBy { it.key }
            .map { it.value }

    fun expectedChunkIds(count: Int): List<String> =
        (0 until count).map { it.toString().padStart(4, '0') }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

/** What the platform layer must provide; host tests supply a synthetic one. */
interface PhotoEncoder {
    /** @return WEBP bytes, or null if the source could not be encoded. */
    fun encode(width: Int, height: Int, rgba: ByteArray): ByteArray?
}

/**
 * The downscale decision, kept separate from the encoder because the POLICY is
 * testable and the pixels are not.
 *
 * Target: [MAX_EDGE] on the long edge, WEBP quality [QUALITY]. The cap is what
 * keeps a 12MP phone camera inside [PhotoTransfer.MAX_TOTAL_BYTES] over a mobile
 * connection. 1080px at WEBP q75 is comfortably under 800KB for a photograph,
 * and it is the smallest size that still looks like a real photo to a child
 * holding the phone at arm's length.
 */
object PhotoPolicy {
    const val MAX_EDGE = 1080
    const val QUALITY = 75

    /** Aspect-preserving target size for a source of [w]x[h]. */
    fun targetSize(w: Int, h: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0) return 0 to 0
        val long = maxOf(w, h)
        if (long <= MAX_EDGE) return w to h
        val scale = MAX_EDGE.toDouble() / long
        return (w * scale).toInt().coerceAtLeast(1) to
            (h * scale).toInt().coerceAtLeast(1)
    }
}
