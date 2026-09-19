// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallViewModel.kt
// Location: app/src/main/java/com/calldad/ui/screens/CallViewModel.kt
package com.calldad.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.data.signaling.IceCandidate
import com.calldad.data.signaling.SdpType
import com.calldad.data.signaling.SessionDescription
import com.calldad.data.signaling.SignalingClient
import com.calldad.data.signaling.SignalingErrorKind
import com.calldad.data.signaling.SignalingFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Manages one call attempt.
 *
 * Phase 2 scope: the signaling dance only. The two "outbound" flows
 * ([remoteDescription], [remoteCandidates]) are intentionally exposed so the
 * Phase 3 WebRTC peer can subscribe to them without touching this class.
 *
 * Hilt is deliberately NOT used yet; the repository is defaulted in the
 * constructor for tests and previews.
 */
class CallViewModel(
    private val signaling: SignalingClient = SignalingClient()
) : ViewModel() {

    // ---- Primary UI state -------------------------------------------------
    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _isCameraOn = MutableStateFlow(true)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    // ---- Phase 3 hand-off (consumed by the future WebRTC peer) -----------
    private val _remoteDescription = MutableStateFlow<SessionDescription?>(null)
    val remoteDescription: StateFlow<SessionDescription?> = _remoteDescription.asStateFlow()

    private val _remoteCandidates = MutableSharedFlow<IceCandidate>(extraBufferCapacity = 32)
    val remoteCandidates: SharedFlow<IceCandidate> = _remoteCandidates.asSharedFlow()

    // ---- Internals --------------------------------------------------------
    private var signalingJob: Job? = null
    private var timerJob: Job? = null

    // ---------------------------------------------------------------------
    // Public API — called from CallScreen buttons
    // ---------------------------------------------------------------------

    /**
     * Caller path. The child taps "Call Dad" on Home.
     * [localSdp] is the OFFER that Phase 3's peer connection will produce;
     * for now callers may pass a placeholder string.
     */
    fun startCall(localSdp: String) {
        if (_state.value !is CallState.Idle && _state.value !is CallState.Error) return
        beginSession(role = CallRole.CALLER, expectedRemote = SdpType.ANSWER) {
            signaling.publishOffer(localSdp).getOrThrow()
        }
    }

    /**
     * Callee path. Dad taps "Answer" on the incoming-call overlay.
     * [localSdp] is the ANSWER to be written to the room.
     */
    fun answerCall(localSdp: String) {
        if (_state.value !is CallState.Idle && _state.value !is CallState.Error) return
        beginSession(role = CallRole.CALLEE, expectedRemote = SdpType.OFFER) {
            // Confirm a valid OFFER is waiting, then post our ANSWER.
            signaling.fetchOffer().getOrThrow()
            signaling.publishAnswer(localSdp).getOrThrow()
        }
    }

    /** Writes a locally-gathered ICE candidate to Firestore. */
    fun onLocalIceCandidate(candidate: IceCandidate) {
        viewModelScope.launch {
            signaling.addIceCandidate(candidate).onFailure { t ->
                val failure = t as? SignalingFailure
                _state.value = CallState.Error(
                    kind = failure?.kind ?: SignalingErrorKind.UNKNOWN,
                    message = failure?.userMessage ?: (t.message ?: "ICE write failed.")
                )
            }
        }
    }

    /** Child taps the giant red Hang Up button, or BackHandler fires. */
    fun endCall() {
        signalingJob?.cancel()
        signalingJob = null
        timerJob?.cancel()
        timerJob = null
        _elapsedSeconds.value = 0
        _remoteDescription.value = null
        _state.value = CallState.Idle

        // Fire-and-forget cleanup. Failure here must NOT block the UI
        // (a child who tapped Hang Up should never see an error dialog).
        viewModelScope.launch { signaling.teardown() }
    }

    fun onToggleCamera() {
        _isCameraOn.update { !it }
    }

    /** Dismisses an [CallState.Error] back to Idle so the child can retry. */
    fun clearError() {
        if (_state.value is CallState.Error) {
            _state.value = CallState.Idle
        }
    }

    override fun onCleared() {
        signalingJob?.cancel()
        timerJob?.cancel()
        super.onCleared()
    }

    // ---------------------------------------------------------------------
    // Orchestration
    // ---------------------------------------------------------------------

    /**
     * Shared lifecycle for both roles:
     *   1. Push our SDP.
     *   2. Listen for the peer's SDP (typed by role).
     *   3. Listen for the peer's trickled ICE candidates.
     *   4. On first remote SDP, flip to InCall and start the timer.
     */
    private fun beginSession(
        role: CallRole,
        expectedRemote: SdpType,
        publishLocal: suspend () -> Unit
    ) {
        signalingJob?.cancel()
        _state.value = CallState.Connecting

        signalingJob = viewModelScope.launch {
            // -------- Step 1: publish our side --------
            try {
                publishLocal()
            } catch (t: Throwable) {
                reportError(t)
                return@launch
            }

            // -------- Step 2: remote SDP listener --------
            val descriptionJob = launch {
                signaling.observeRemoteDescription(expectedRemote)
                    .catch { reportError(it) }
                    .collect { remote ->
                        _remoteDescription.value = remote
                        if (_state.value is CallState.Connecting) {
                            _state.value = CallState.InCall(
                                role = role,
                                startedAtMillis = System.currentTimeMillis()
                            )
                            startTimer()
                        }
                    }
            }

            // -------- Step 3: remote ICE listener --------
            val candidatesJob = launch {
                signaling.observeIceCandidates()
                    .catch { reportError(it) }
                    .collect { candidate ->
                        // SharedFlow with extraBufferCapacity — tryEmit never
                        // suspends and never drops while there is capacity.
                        _remoteCandidates.tryEmit(candidate)
                    }
            }

            // Keep [signalingJob] alive until either child fails; both
            // listeners are cancelled together by endCall()/onCleared().
            descriptionJob.join()
            candidatesJob.join()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _elapsedSeconds.update { it + 1 }
            }
        }
    }

    private fun reportError(t: Throwable) {
        val failure = t as? SignalingFailure
        _state.value = CallState.Error(
            kind = failure?.kind ?: SignalingErrorKind.UNKNOWN,
            message = failure?.userMessage
                ?: t.message
                ?: "Something went wrong."
        )
    }
}

/** Formats elapsed seconds as mm:ss for the CallScreen timer. */
fun formatElapsed(totalSeconds: Int): String = String.format(
    Locale.US,
    "%02d:%02d",
    totalSeconds / 60,
    totalSeconds % 60
)
