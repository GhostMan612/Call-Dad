// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallViewModel.kt — Phase 3: WebRTC peer integration
// Location: app/src/main/java/com/calldad/ui/screens/CallViewModel.kt
//
// Fixes Phase 2 latent callee bug: description-observation is now scoped to
// the caller only (callee already applied the OFFER in answerCall()).
package com.calldad.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.data.signaling.IceCandidate
import com.calldad.data.signaling.SdpType
import com.calldad.data.signaling.SessionDescription
import com.calldad.data.signaling.SignalingClient
import com.calldad.data.signaling.SignalingErrorKind
import com.calldad.data.signaling.SignalingFailure
import com.calldad.webrtc.WebRTCClient
import com.calldad.webrtc.WebRtcLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import java.util.Locale

class CallViewModel(application: Application) : AndroidViewModel(application) {

    // Hilt lands in a later phase; manual construction for now.
    private val signaling: SignalingClient = SignalingClient()

    private val webrtc: WebRTCClient = WebRTCClient(
        context = application.applicationContext,
        onLocalIceCandidate = { candidate ->
            viewModelScope.launch { signaling.addIceCandidate(candidate) }
        },
        onRemoteVideoTrack = { track -> _remoteVideoTrack.value = track },
        onConnectionStateChanged = { state ->
            _peerConnected.value = (state == "CONNECTED")
            WebRtcLog.transition("VM observed peer state: $state")
        }
    )

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _isCameraOn = MutableStateFlow(true)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _eglContext = MutableStateFlow<EglBase.Context?>(null)
    val eglContext: StateFlow<EglBase.Context?> = _eglContext.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    /** True once the peer connection reaches CONNECTED. Drives the watchdog. */
    private val _peerConnected = MutableStateFlow(false)

    private var timerJob: Job? = null
    private var remoteListenerJob: Job? = null

    // -------- public API --------

    fun startCall() {
        val current = _state.value
        if (current is CallState.Connecting || current is CallState.InCall) return
        _state.value = CallState.Connecting

        viewModelScope.launch {
            try {
                // Fresh room: wipe stale OFFER/ANSWER/candidates left by prior
                // QA runs or crashes. Failures ignored (room may not exist).
                // Caller-only: the callee must NEVER wipe a live OFFER.
                runCatching { signaling.teardown() }
                webrtc.initialize()
                webrtc.createPeerConnection()
                _eglContext.value = webrtc.eglContext
                _localVideoTrack.value = webrtc.localVideoTrack
                webrtc.startCapture()

                val offer = webrtc.createOffer()
                WebRtcLog.transition("OFFER publish started")
                signaling.publishOffer(offer.sdp).getOrThrow()
                WebRtcLog.transition("OFFER published")

                listenForAnswer()
                listenForRemoteCandidates()
                listenForRemoteHangup()
            } catch (t: Throwable) {
                // Scope cancellation (e.g. hangup popping the destination and
                // clearing the VM) is not an error — never report it.
                if (t is CancellationException) throw t
                reportError(t)
            }
        }
    }

    /**
     * Peer hung up or declined (teardown deletes the room). Mirror it
     * locally: reset to Idle so the screen auto-returns home. Our own
     * teardown is idempotent, so calling endCall() here is safe.
     */
    private fun listenForRemoteHangup() {
        viewModelScope.launch {
            signaling.observeRoomDeleted()
                .catch { reportError(it) }
                .collect {
                    WebRtcLog.transition("Remote hangup observed")
                    endCall()
                }
        }
    }

    fun answerCall() {
        val current = _state.value
        if (current is CallState.Connecting || current is CallState.InCall) return
        _state.value = CallState.Connecting

        viewModelScope.launch {
            try {
                webrtc.initialize()
                webrtc.createPeerConnection()
                _eglContext.value = webrtc.eglContext
                _localVideoTrack.value = webrtc.localVideoTrack
                webrtc.startCapture()

                // Two humans never tap in sync: poll for the OFFER up to
                // ~15s (NOT_FOUND only — other failures throw immediately).
                // Covers caller-taps-second as well as caller-taps-first.
                var offer: SessionDescription? = null
                repeat(15) {
                    val attempt = signaling.fetchOffer()
                    offer = attempt.getOrNull()
                    if (offer != null) return@repeat
                    val failure = attempt.exceptionOrNull() as? SignalingFailure
                    if (failure != null && failure.kind != SignalingErrorKind.NOT_FOUND) {
                        throw failure
                    }
                    delay(1_000)
                }
                val validOffer = offer ?: throw SignalingFailure(
                    SignalingErrorKind.NOT_FOUND,
                    "Call room does not exist yet."
                )
                webrtc.setRemoteDescription(validOffer)
                WebRtcLog.transition("Remote OFFER applied")

                val answer = webrtc.createAnswer()
                WebRtcLog.transition("ANSWER publish started")
                signaling.publishAnswer(answer.sdp).getOrThrow()
                WebRtcLog.transition("ANSWER published")

                _state.value = CallState.InCall(
                    role = CallRole.CALLEE,
                    startedAtMillis = System.currentTimeMillis()
                )
                startTimer()
                watchConnection()
                listenForRemoteCandidates()
                listenForRemoteHangup()
            } catch (t: Throwable) {
                // Scope cancellation (e.g. hangup popping the destination and
                // clearing the VM) is not an error — never report it.
                if (t is CancellationException) throw t
                reportError(t)
            }
        }
    }

    /**
     * Zombie-call guard: answered a room whose peer already vanished (quick
     * hangup race) → media never connects. Give it 15s (LAN connects in
     * ~1-3s proven), then leave silently — auto-home follows, no scary card
     * for a kid whose dad hung up first. Revisit the timeout with TURN.
     */
    private fun watchConnection() {
        viewModelScope.launch {
            delay(15_000)
            if (!_peerConnected.value && _state.value is CallState.InCall) {
                WebRtcLog.transition("Media never connected — leaving call")
                _state.value = CallState.Idle
            }
        }
    }

    /**
     * Incoming overlay only: if the room vanishes before Answer (caller hung
     * up fast), follow home instead of stranding on the overlay. Never
     * tears down here — the room is already gone (or never existed).
     *
     * Validates FIRST: the overlay opens on a snapshot that may already be
     * stale (offer lived and died between the Home listener's read and this
     * screen's arrival). A non-answerable room — missing, malformed, or our
     * own ringback — bounces straight home instead of stranding.
     */
    fun watchIncomingRoom() {
        viewModelScope.launch {
            val current = signaling.fetchOffer().getOrNull()
            if (current == null || OwnOfferRegistry.isOwn(current.sdp)) {
                if (_state.value is CallState.Incoming) {
                    WebRtcLog.transition("No answerable offer — leaving")
                    _state.value = CallState.Idle
                }
                return@launch
            }
            signaling.observeRoomDeleted()
                .catch { /* stay on overlay; Answer path reports properly */ }
                .collect {
                    if (_state.value is CallState.Incoming) {
                        WebRtcLog.transition("Room gone before answer — leaving")
                        _state.value = CallState.Idle
                    }
                }
        }
    }

    fun onLocalIceCandidate(candidate: IceCandidate) {
        viewModelScope.launch {
            signaling.addIceCandidate(candidate).onFailure(::reportError)
        }
    }

    /**
     * Caller's hangup OR callee's decline. Also resets from Incoming.
     *
     * Does NOT dispose WebRTC here: the composables still hold native sinks
     * until navigation pops (removeSink on a disposed track = SIGSEGV —
     * device-proven). Disposal happens in onCleared, strictly after the
     * composition is gone.
     */
    fun endCall() {
        cancelSessionJobs()
        resetCallState()
        viewModelScope.launch { signaling.teardown() }
    }

    /**
     * Local user-initiated hangup/decline. Same reset, but the room delete is
     * awaited (3s cap) BEFORE the caller navigates: navigating pops the
     * screen, clears this VM, and cancels viewModelScope — a fire-and-forget
     * teardown usually dies with it, leaving the room behind so the peer
     * hangs forever (device-proven). Offline can't trap us: the timeout
     * guarantees navigation proceeds.
     */
    suspend fun endCallAndAwait() {
        cancelSessionJobs()
        resetCallState()
        withTimeoutOrNull(3_000) { signaling.teardown() }
    }

    private fun cancelSessionJobs() {
        remoteListenerJob?.cancel(); remoteListenerJob = null
        timerJob?.cancel(); timerJob = null
    }

    private fun resetCallState() {
        _elapsedSeconds.value = 0
        _remoteVideoTrack.value = null
        _eglContext.value = null
        _localVideoTrack.value = null
        _peerConnected.value = false
        _state.value = CallState.Idle
    }

    fun onToggleCamera() {
        _isCameraOn.update { !it }
        webrtc.toggleCamera()
    }

    fun clearError() {
        if (_state.value is CallState.Error) _state.value = CallState.Idle
    }

    /**
     * QA-only. Drives the Incoming overlay without an FCM push.
     * Phase 5 will replace this with a real FCM-triggered transition.
     * Guard: only reachable when the current state is Idle.
     */
    fun simulateIncomingCall() {
        if (_state.value !is CallState.Idle) return
        _state.value = CallState.Incoming(fromDisplayName = "Dad")
    }

    override fun onCleared() {
        remoteListenerJob?.cancel()
        timerJob?.cancel()
        webrtc.dispose()
        super.onCleared()
    }

    // -------- listeners --------

    /** Caller-only. Callee already applied the OFFER in answerCall(). */
    private fun listenForAnswer() {
        remoteListenerJob = viewModelScope.launch {
            signaling.observeRemoteDescription(SdpType.ANSWER)
                .catch { reportError(it) }
                .collect { remote ->
                    webrtc.setRemoteDescription(remote)
                    WebRtcLog.transition("Remote ANSWER applied")
                    if (_state.value is CallState.Connecting) {
                        _state.value = CallState.InCall(
                            role = CallRole.CALLER,
                            startedAtMillis = System.currentTimeMillis()
                        )
                        startTimer()
                        watchConnection()
                    }
                }
        }
    }

    private fun listenForRemoteCandidates() {
        viewModelScope.launch {
            signaling.observeIceCandidates()
                .catch { reportError(it) }
                .collect { webrtc.addRemoteIceCandidate(it) }
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
        // Kind-name only: guardrail-compliant, distinguishes hang (no line)
        // from failure (this line) in logcat.
        WebRtcLog.transition("Call failed: ${failure?.kind?.name ?: "UNKNOWN"}")
        _state.value = CallState.Error(
            kind = failure?.kind ?: SignalingErrorKind.UNKNOWN,
            message = failure?.userMessage ?: t.message ?: "Something went wrong."
        )
    }
}

fun formatElapsed(totalSeconds: Int): String = String.format(
    Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60
)
