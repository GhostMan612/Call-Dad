// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// photos/PhotoClient.kt — pair-scoped photo send/receive (BP-04, §2.4)
// Location: app/src/main/java/com/calldad/photos/PhotoClient.kt
package com.calldad.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import com.calldad.consent.ConsentScope
import com.calldad.data.session.FamilyPair
import com.calldad.data.session.FamilySession
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * A photo in the thread.
 *
 * [verified] is the field that matters: an unverified photo is a photo that may
 * be truncated, and showing a child's half-rendered face as if it were whole is
 * worse than saying "this didn't arrive". The UI must not render
 * `verified == false`.
 */
data class PhotoMessage(
    val id: String,
    val fromUid: String,
    val width: Int,
    val height: Int,
    val totalBytes: Int,
    val verified: Boolean,
    /**
     * When it was sent, in epoch millis. Carried because the doc id is a random
     * auto-id and cannot order anything — see [PhotoClient.prepend].
     */
    val createdAtMs: Long = 0L,
    val bytes: ByteArray? = null,
    val failure: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoMessage) return false
        // Byte arrays compare by reference under the default data-class
        // equality, which makes a re-fetched identical photo look like a new one
        // and makes a diffing list rebuild every row on every snapshot.
        return id == other.id &&
            fromUid == other.fromUid &&
            width == other.width &&
            height == other.height &&
            totalBytes == other.totalBytes &&
            verified == other.verified &&
            createdAtMs == other.createdAtMs &&
            failure == other.failure &&
            (bytes?.size ?: -1) == (other.bytes?.size ?: -1) &&
            (bytes == null || other.bytes == null || bytes.contentEquals(other.bytes))
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fromUid.hashCode()
        result = 31 * result + verified.hashCode()
        result = 31 * result + createdAtMs.hashCode()
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Photo sharing over the pair's private room (SPEC_SHEET §2.4, BP-04).
 *
 * `calls/{room}/photos/{auto}` carries the immutable manifest
 * (totalBytes, chunkCount, sha256, mime) and `.../chunks/{0000..}` carries the
 * bytes. The manifest is written LAST, so a receiver that sees it is guaranteed
 * the chunks were at least *offered* — and [PhotoTransfer.reassemble] then
 * proves they arrived intact.
 *
 * The writer order is the interesting part and it is deliberate:
 *  1. write the chunks, each AWAITED. A queued chunk is not a sent chunk.
 *  2. write the manifest, also AWAITED, carrying the digest.
 *  3. only now can a receiver assemble anything.
 *
 * Written the other way round, a receiver could read a manifest, fetch chunks
 * that were never written, and report "corrupt" for a transfer that is merely
 * still in flight. The emulator suite pins the field rules that make the
 * manifest immutable, so a sender cannot swap the digest after the fact and make
 * a broken transfer look verified.
 */
class PhotoClient(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val _photos = MutableStateFlow<List<PhotoMessage>>(emptyList())
    val photos: Flow<List<PhotoMessage>> = _photos.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)
    val problem: Flow<String?> = _problem.asStateFlow()

    private var registration: ListenerRegistration? = null
    private var seen = mutableSetOf<String>()

    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context, scope: CoroutineScope) {
        // Resolved once, to a NON-NULL local. The field is nullable only because
        // it is assigned here, and reading it back out of the field is how a
        // nullability assertion ends up in a callback that fires before `start`.
        val app = context.applicationContext
        scope.launch {
            FamilySession.pair(app).collect { pair -> onPair(pair) }
        }
    }

    fun stop() {
        registration?.remove()
        registration = null
        seen = mutableSetOf()
        _photos.value = emptyList()
    }

    /**
     * Cancels the work scope. [PhotoViewModel.onCleared] calls this.
     *
     * [workScope] is a root `SupervisorJob` and nothing else can complete it, so
     * without this every `PhotoClient` instance — one per activity-scoped
     * ViewModel — leaks a live job for the life of the process, and any in-flight
     * `loadOne` keeps running after the ViewModel is gone: downloading up to
     * 800KB, reassembling, and publishing into a StateFlow nobody observes.
     *
     * `stop()` deliberately does NOT do this, because it is also called on a
     * pairing change and a photo mid-verify should survive that. Stopping the
     * listener and ending the object's life are different operations.
     */
    fun close() {
        stop()
        workScope.cancel()
    }

    fun clearProblem() { _problem.value = null }

    private fun onPair(pair: FamilyPair?) {
        registration?.remove()
        registration = null
        seen = mutableSetOf()
        _photos.value = emptyList()
        if (pair == null) return
        registration = firestore.collection("calls").document(pair.roomId).collection("photos")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(MAX_VISIBLE.toLong())
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    _problem.value = "Couldn't get pictures. Try again."
                    WebRtcLog.transition("Photo listener failed")
                    return@addSnapshotListener
                }
                if (snap.metadata.isFromCache) return@addSnapshotListener
                val fresh = snap.documentChanges
                    .filter { it.type == DocumentChange.Type.ADDED }
                    .map { it.document }
                    .filter { it.getString("from") == pair.peerUid }
                    .filter { seen.add(it.id) }
                if (fresh.isNotEmpty()) {
                    // Sequentially, not with forEach { async { } }: two 800KB
                    // transfers racing on a phone's uplink is what makes a send
                    // feel broken, and the emulator suite is per-write anyway.
                    workScope.launch { fresh.forEach { loadOne(pair, it) } }
                }
            }
    }

    /**
     * Downscale -> WEBP -> chunk -> upload -> manifest.
     *
     * Returns the manifest id on success. Every failure returns a `failure`
     * string rather than throwing, because every caller is a Compose screen and
     * a thrown exception there is a crash on a child's phone.
     */
    suspend fun send(
        pair: FamilyPair,
        bitmap: Bitmap,
        scopes: Set<ConsentScope>
    ): Result<String> {
        if (ConsentScope.PHOTO !in scopes) {
            return Result.failure(IllegalStateException("no photo consent"))
        }
        return runCatching {
            val (tw, th) = PhotoPolicy.targetSize(bitmap.width, bitmap.height)
            if (tw <= 0 || th <= 0) error("empty source image")
            val scaled = withContext(Dispatchers.Default) {
                Bitmap.createScaledBitmap(bitmap, tw, th, true)
            }
            val encoded = withContext(Dispatchers.Default) { encodeWebp(scaled) }
            if (scaled !== bitmap) scaled.recycle()
            when (val split = PhotoTransfer.split(encoded)) {
                is PhotoTransfer.Split.TooLarge ->
                    error("That picture is too big to send.")
                is PhotoTransfer.Split.Ok -> {
                    val chunksRef = firestore.collection("calls").document(pair.roomId)
                        .collection("photos")
                    // Chunks FIRST, each awaited, into a manifest-less doc whose
                    // id we then use for the manifest. Two writes either way
                    // round; this order is the one that cannot lie.
                    val photoId = chunksRef.document().id
                    split.chunks.forEachIndexed { index, bytes ->
                        chunksRef.document(photoId).collection("chunks")
                            .document(PhotoTransfer.expectedChunkIds(split.chunks.size)[index])
                            .set(
                                mapOf(
                                    "from" to pair.ownUid,
                                    "bytes" to Blob.fromBytes(bytes),
                                    "index" to index,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                    }
                    chunksRef.document(photoId).set(
                        mapOf(
                            "from" to pair.ownUid,
                            "totalBytes" to split.totalBytes,
                            "chunkCount" to split.chunks.size,
                            "sha256" to split.sha256,
                            "mime" to "image/webp",
                            "width" to tw,
                            "height" to th,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    WebRtcLog.transition("Photo sent")
                    photoId
                }
            }
        }
    }

    private fun encodeWebp(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        // WEBP_LOSSY: a photo is a photo. LOSSY at q75 lands well under the
        // 800KB cap and is what PhotoPolicy's size budget assumes.
        val ok = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, PhotoPolicy.QUALITY, out)
        } else {
            @Suppress("DEPRECATION")
            bmp.compress(Bitmap.CompressFormat.WEBP, PhotoPolicy.QUALITY, out)
        }
        if (!ok) error("could not encode the picture")
        return out.toByteArray()
    }

    /**
     * Fetches and VERIFIES one photo. An unverified photo is published with
     * `verified = false` and no bytes, never with the partial bytes: a child
     * should see "that picture didn't arrive", not a corrupted picture.
     */
    private suspend fun loadOne(pair: FamilyPair, doc: DocumentSnapshot) {
        val id = doc.id
        val total = (doc.getLong("totalBytes") ?: 0L).toInt()
        val count = (doc.getLong("chunkCount") ?: 0L).toInt()
        val sha = doc.getString("sha256").orEmpty()
        val width = (doc.getLong("width") ?: 0L).toInt()
        val height = (doc.getLong("height") ?: 0L).toInt()
        val from = doc.getString("from").orEmpty()
        val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
        if (total <= 0 || count <= 0 || sha.length != 64) {
            _photos.value = prepend(
                PhotoMessage(id, from, width, height, total, verified = false,
                    createdAtMs = createdAt, failure = "That picture didn't arrive.")
            )
            return
        }
        val snapshot = withContext(Dispatchers.IO) {
            firestore.collection("calls").document(pair.roomId).collection("photos")
                .document(id).collection("chunks").get().await()
        }
        val byId = HashMap<String, ByteArray>(snapshot.size())
        snapshot.documents.forEach { d ->
            d.getBlob("bytes")?.toBytes()?.let { byId[d.id] = it }
        }
        val ordered = PhotoTransfer.orderByIndex(byId)
        when (val result = PhotoTransfer.reassemble(ordered, total, sha)) {
            is PhotoTransfer.Reassembled.Ok -> {
                // Do NOT decode here as a validity probe. This used to
                // `BitmapFactory.decodeByteArray`, null-check the result, publish
                // `result.bytes` and throw the bitmap away — never stored, never
                // recycled. That is one abandoned ARGB_8888 bitmap per received
                // photo: ~4.7MB native for a 1080px image, times the 24-photo
                // window, i.e. well over 100MB of unreachable pixel data and a
                // guaranteed OutOfMemoryError on a cheap phone.
                //
                // The bytes are the contract and the UI decodes them once, in a
                // `remember` keyed on the id, inside a `runCatching`. Correctness
                // of the payload is what the digest above just proved; decodability
                // is the renderer's problem, and it already handles it.
                _photos.value = prepend(
                    PhotoMessage(id, from, width, height, total, verified = true,
                        bytes = result.bytes)
                )
            }
            is PhotoTransfer.Reassembled.Corrupt -> {
                // The digest is the point of the whole transport. Never publish
                // unverified bytes.
                WebRtcLog.transition("Photo failed verification")
                _photos.value = prepend(
                    PhotoMessage(id, from, width, height, total, verified = false,
                        createdAtMs = createdAt,
                        failure = "That picture didn't arrive whole. Ask again?")
                )
            }
        }
    }

    /**
     * Newest first, and capped to the newest [MAX_VISIBLE].
     *
     * Ordered by [PhotoMessage.createdAtMs], NOT by `id`: Firestore auto-ids are
     * random 20-character strings, so sorting by id put a week-old picture above
     * an hour-old one and let `take()` keep an arbitrary 24 rather than the 24 a
     * child would expect to still be able to scroll back to. The listener queries
     * `createdAt` DESCENDING for the same reason.
     */
    private fun prepend(photo: PhotoMessage): List<PhotoMessage> =
        (listOf(photo) + _photos.value.filterNot { it.id == photo.id })
            .sortedWith(compareByDescending<PhotoMessage> { it.createdAtMs }.thenBy { it.id })
            .take(MAX_VISIBLE)

    private companion object {
        const val MAX_VISIBLE = 24
    }
}
