// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ptt/VoiceClipPttEngine.kt — real walkie-talkie: hold to record, release to send (ADR-016)
// Location: app/src/main/java/com/calldad/ptt/VoiceClipPttEngine.kt
package com.calldad.ptt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.calldad.data.session.FamilyPair
import com.calldad.data.session.FamilySession
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Walkie-talkie over the pair's private room. While the button is held a
 * short AAC clip is recorded; on release it is written to
 * `calls/{roomId}/ptt/{auto}` (only the two paired UIDs can touch it, see
 * firestore.rules). The other phone plays clips in arrival order on ANY
 * screen, holds them while a video call owns the audio, and deletes a clip
 * ONLY after it has actually played to completion. A clip that fails to
 * decode, or that is interrupted by a ring, is kept so the message is not
 * silently lost. Clips sent while the other phone is offline play when it
 * comes back; there is no age-based destruction.
 *
 * Honesty rules enforced here (ADR-016 follow-up):
 *  - the upload is AWAITED, so "SENT!" cannot be shown for a write that is
 *    still queued, offline, or rejected by the rules;
 *  - deletion happens only on onCompletionListener, never on a failure or an
 *    interrupt;
 *  - the listener's own errors surface as PttAudioState.Error rather than
 *    leaving a silent walkie-talkie.
 *
 * No new dependencies: MediaRecorder / MediaPlayer + Firestore bytes.
 */
class VoiceClipPttEngine(
    context: Context,
    private val scope: CoroutineScope
) : PttEngine {

    private val appContext = context.applicationContext
    private val firestore = FirebaseFirestore.getInstance()

    private val _incoming = MutableStateFlow<PttAudioState>(PttAudioState.Idle)
    override fun observeIncomingAudio(): Flow<PttAudioState> = _incoming.asStateFlow()

    private var pair: FamilyPair? = null
    private var pairJob: Job? = null
    private var registration: ListenerRegistration? = null
    private val seenClips = mutableSetOf<String>()
    private val playQueue = Channel<Clip>(Channel.UNLIMITED)
    private var playerJob: Job? = null
    private var player: MediaPlayer? = null
    private var interruptPlayback: (() -> Unit)? = null

    private val playbackBlocked = MutableStateFlow(false)

    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var recordStartedAt = 0L

    /**
     * MediaRecorder stops itself at MAX_CLIP_MS and only says so through
     * this listener. Without it the child talks into a dead mic, watches
     * "TALKING" for the rest of the hold, and is then told they held the
     * button wrong. Set by the engine, cleared by stopTransmitting.
     */
    @Volatile
    private var recordLimitReached = false

    private data class Clip(val id: String, val bytes: ByteArray)

    init {
        pairJob = scope.launch {
            FamilySession.pair(appContext).collect { p ->
                pair = p
                listenForClips(p)
            }
        }
        playerJob = scope.launch { runPlayer() }
    }

    /** A live video call owns the audio: clips wait until it ends. */
    fun setPlaybackBlocked(blocked: Boolean) {
        playbackBlocked.value = blocked
        if (blocked) stopPlayer()
    }

    // -------- transmit --------

    override suspend fun startTransmitting(): Result<Unit> {
        if (recorder != null) return Result.success(Unit)
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(
                PttFailure(PttFailureKind.PERMISSION_DENIED, "The walkie talkie needs the microphone. Ask a grown-up.")
            )
        }
        if (pair == null) {
            return Result.failure(
                PttFailure(PttFailureKind.TRANSPORT_ERROR, "Pair the phones first (grown-ups: gear button).")
            )
        }
        stopPlayer()
        return runCatching {
            val file = File(appContext.cacheDir, "ptt_out.m4a").also { it.delete() }
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(SAMPLE_RATE)
            r.setAudioEncodingBitRate(BIT_RATE)
            r.setMaxDuration(MAX_CLIP_MS)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    recordLimitReached = true
                    WebRtcLog.transition("PTT clip reached max duration")
                }
            }
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recordFile = file
            recordStartedAt = System.currentTimeMillis()
            WebRtcLog.transition("PTT clip recording")
            Unit
        }.recoverCatching {
            releaseRecorder()
            throw PttFailure(PttFailureKind.TRANSPORT_ERROR, "The microphone is busy. Try again.", it)
        }
    }

    override suspend fun stopTransmitting(): Result<Unit> {
        val r = recorder ?: return Result.success(Unit)
        val file = recordFile

        // Encoder-drain tail (device-proven clipping fix, 2026-09-30).
        // MediaRecorder.stop() finalises the MPEG-4 container immediately, and
        // an AAC encoder holds a priming delay plus whatever frames are still
        // in its write buffer. Stopping the instant the finger lifts therefore
        // throws away the last fraction of a second of speech — the end of the
        // last word. The child hears themselves get cut off on every message.
        // Recording a short silent tail gives the encoder time to flush, so the
        // final words survive. Silent audio is inaudible; a truncated last word
        // is very audible, so paying this in dead air is the right trade.
        //
        // Only on the release path: a clip that hit MAX_CLIP_MS is already
        // finalised by the platform, and stopping it again throws.
        if (!recordLimitReached) delay(ENCODER_DRAIN_MS)

        val durationMs = System.currentTimeMillis() - recordStartedAt
        // A clip that hit the cap stopped itself, so stop() throws. That is
        // NOT a failed recording — the bytes are on disk and the child did
        // nothing wrong. Only treat a throw as fatal when the cap was not hit.
        val capped = recordLimitReached
        val stopped = capped || runCatching { r.stop() }.isSuccess
        recordLimitReached = false
        releaseRecorder()
        if (!stopped || file == null || durationMs < MIN_CLIP_MS) {
            file?.delete()
            return Result.failure(
                PttFailure(PttFailureKind.UNKNOWN, "Hold the button down while you talk.")
            )
        }
        val p = pair ?: run {
            file.delete()
            return Result.failure(
                PttFailure(PttFailureKind.TRANSPORT_ERROR, "Pair the phones first.")
            )
        }
        return runCatching {
            // Read the bytes BEFORE any failure path can delete the file, so
            // a too-large clip does not destroy the recording first.
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            file.delete()
            if (bytes.size > MAX_CLIP_BYTES) error("clip too large")
            // AWAITED. Without this the Task is discarded, runCatching only
            // ever sees the synchronous path, and the child is shown "SENT!"
            // for a write that is queued, offline, or rejected by the rules.
            firestore.collection("calls").document(p.roomId).collection("ptt").add(
                mapOf(
                    "from" to p.ownUid,
                    "audio" to Blob.fromBytes(bytes),
                    "durationMs" to durationMs.coerceAtMost(MAX_CLIP_MS.toLong()),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            WebRtcLog.transition("PTT clip sent")
            Unit
        }.recoverCatching {
            throw PttFailure(PttFailureKind.TRANSPORT_ERROR, "Couldn't send. Try again.", it)
        }
    }

    private fun releaseRecorder() {
        runCatching { recorder?.release() }
        recorder = null
        recordFile = null
    }

    // -------- receive --------

    private fun listenForClips(p: FamilyPair?) {
        registration?.remove()
        registration = null
        seenClips.clear()
        if (p == null) return
        registration = firestore.collection("calls").document(p.roomId).collection("ptt")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    // A dead walkie-talkie must not be indistinguishable from
                    // a quiet parent. Surface it; silence is the real defect.
                    WebRtcLog.transition("PTT clip listener failed")
                    _incoming.value = PttAudioState.Error("Couldn't get messages. Try again.")
                    return@addSnapshotListener
                }
                if (snap.metadata.isFromCache) return@addSnapshotListener
                for (change in snap.documentChanges) {
                    if (change.type != DocumentChange.Type.ADDED) continue
                    val doc = change.document
                    if (doc.metadata.hasPendingWrites()) continue
                    if (doc.getString("from") != p.peerUid) continue
                    if (!seenClips.add(doc.id)) continue
                    val bytes = doc.getBlob("audio")?.toBytes()
                    if (bytes != null) {
                        // Age is NOT a reason to destroy a message the child
                        // was told was sent. Play it; the sender's retention
                        // decision is the sender's, not ours.
                        playQueue.trySend(Clip(doc.id, bytes))
                    } else {
                        // Unreadable payload: do not delete, just skip.
                        WebRtcLog.transition("PTT clip had no audio")
                    }
                }
            }
    }

    private fun deleteClip(roomId: String, clipId: String) {
        runCatching {
            firestore.collection("calls").document(roomId)
                .collection("ptt").document(clipId).delete()
        }.onFailure {
            // Leaving the id in seenClips orphans the doc forever. Drop it so
            // a later snapshot can retry the delete.
            seenClips.remove(clipId)
            WebRtcLog.transition("PTT clip delete failed")
        }
    }

    private suspend fun runPlayer() {
        for (clip in playQueue) {
            playbackBlocked.first { !it }
            while (recorder != null) delay(200)
            val roomId = pair?.roomId
            _incoming.value = PttAudioState.Receiving
            // play() reports true ONLY on onCompletionListener. A failure, a
            // decode error, or an interrupt all return false, and the clip is
            // kept so the message is not silently eaten.
            val played = runCatching { play(clip.bytes) }.getOrElse {
                WebRtcLog.transition("PTT clip playback failed")
                false
            }
            _incoming.value = PttAudioState.Idle
            if (played) {
                if (roomId != null) deleteClip(roomId, clip.id)
            } else {
                _incoming.value = PttAudioState.Error("That message didn't play. Ask again?")
            }
        }
    }

    /** @return true only when the clip played to completion. */
    private suspend fun play(bytes: ByteArray): Boolean {
        val file = File(appContext.cacheDir, "ptt_in.m4a")
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }
        val mp = MediaPlayer()
        player = mp
        var completed = false
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            suspendCancellableCoroutine { cont ->
                val finish = { ok: Boolean ->
                    if (ok) completed = true
                    if (cont.isActive) cont.resume(Unit)
                }
                mp.setOnCompletionListener { finish(true) }
                mp.setOnErrorListener { _, _, _ -> finish(false); true }
                interruptPlayback = {
                    runCatching { mp.stop() }
                    finish(false)
                }
                cont.invokeOnCancellation { runCatching { mp.stop() } }
                mp.start()
                WebRtcLog.transition("PTT clip playing")
            }
        } finally {
            interruptPlayback = null
            player = null
            runCatching { mp.release() }
            file.delete()
        }
        return completed
    }

    private fun stopPlayer() {
        interruptPlayback?.invoke()
    }

    override fun release() {
        pairJob?.cancel()
        playerJob?.cancel()
        registration?.remove()
        registration = null
        playQueue.close()
        runCatching { recorder?.stop() }
        releaseRecorder()
        runCatching { player?.release() }
        player = null
        WebRtcLog.transition("PTT voice clips: released")
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val BIT_RATE = 32_000
        const val MIN_CLIP_MS = 400L
        const val MAX_CLIP_MS = 15_000

        /**
         * Silent recording kept after the finger lifts, so the AAC encoder can
         * flush its buffered tail before the container is finalised. 700ms is
         * comfortably more than the ~2048-sample (128ms) priming delay plus
         * a few 23ms frames, with margin for a slow device.
         */
        const val ENCODER_DRAIN_MS = 700L
        const val MAX_CLIP_BYTES = 200_000
    }
}
