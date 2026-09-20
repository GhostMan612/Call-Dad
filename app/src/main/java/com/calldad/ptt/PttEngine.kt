// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ptt/PttEngine.kt — Phase 6: transport-agnostic PTT contract
// Location: app/src/main/java/com/calldad/ptt/PttEngine.kt
package com.calldad.ptt

import kotlinx.coroutines.flow.Flow

/**
 * Transport-agnostic PTT engine.
 *
 * Implementations:
 *   - SimulatedPttEngine  (default; loopback for QA and CI)
 *   - SovereignPttAdapter (production; wraps the private Sovereign Mantle
 *                          module when it is on the classpath)
 *
 * Threading: all methods are safe to call from the main thread. Implementations
 * are responsible for dispatching to whatever background executor they need.
 */
interface PttEngine {

    /**
     * Begin transmitting. Idempotent — a second call while already
     * transmitting is a no-op, not an error.
     *
     * @return Result.success(Unit) if the mic was acquired and the channel
     *         opened. Result.failure with a PttFailure if not.
     */
    suspend fun startTransmitting(): Result<Unit>

    /**
     * Stop transmitting and release the mic. Idempotent — a call while
     * already idle is a no-op.
     */
    suspend fun stopTransmitting(): Result<Unit>

    /**
     * Hot flow of inbound PTT state. Emits whenever the remote side
     * begins or ends a transmission. Never completes on its own.
     */
    fun observeIncomingAudio(): Flow<PttAudioState>

    /** Release all resources. Called from ViewModel.onCleared(). */
    fun release()
}

/**
 * Inbound audio state from the peer.
 */
sealed interface PttAudioState {
    data object Idle : PttAudioState
    data object Receiving : PttAudioState
    data class Error(val message: String) : PttAudioState
}

/**
 * Typed failure for PTT operations. Mirrors the SignalingError pattern.
 */
class PttFailure(
    val kind: PttFailureKind,
    val userMessage: String,
    override val cause: Throwable? = null
) : Exception(userMessage, cause)

enum class PttFailureKind {
    /** WebRTC call is active; mic is contended. */
    AUDIO_CONTENDED,
    /** RECORD_AUDIO permission not granted. */
    PERMISSION_DENIED,
    /** Could not acquire audio focus. */
    AUDIO_FOCUS_LOST,
    /** Sovereign Mantle module not on the classpath. */
    ENGINE_UNAVAILABLE,
    /** Underlying engine reported a failure. */
    TRANSPORT_ERROR,
    UNKNOWN
}
