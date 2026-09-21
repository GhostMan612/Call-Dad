// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallViewModel.kt — Phase 11: static room + status machine
// Location: app/src/main/java/com/calldad/ui/screens/CallViewModel.kt
//
// NO role gates: both flavors/products call AND answer. A previous prompt
// revision gated startCall/answerCall by flavor — that would brick the
// product in both directions (no flavor-differentiated UI exists to
// compensate). See ADR-013. There is exactly one user story: either side
// may ring, either side may answer.
package com.calldad.ui.screens

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.audio.CallAudioManager
import com.calldad.data.signaling.CallStatus
import com.calldad.data.signaling.IceCandidate
import com.calldad.data.signaling.SdpType
import com.calldad.data.signaling.SessionDescription
import com.calldad.data.signaling.SignalingClient
import com.calldad.data.signaling.SignalingErrorKind
import com.calldad.data.signaling.SignalingFailure
import com.calldad.webrtc.ConnectionHealth
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

    /** Incoming-call ringer. Single owner (overlay-local ring was removed). */
    private val callAudio: CallAudioManager =
        CallAudioManager(application.applicationContext)

    private val webrtc: WebRTCClient = WebRTCClient(
        context = application.applicationContext,
        onLocalIceCandidate = { candidate -> onLocalIceCandidate(candidate) },
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

    /** Connection health for the UI banner + auto-reconnect trigger. */
    val connectionHealth: StateFlow<ConnectionHealth> = webrtc.connectionHealth

    /** Last applied ANSWER/OFFER seq. Stale generations are ignored. */
    private var lastAppliedSeq: Int = -1

    private var timerJob: Job? = null
    private var sessionJob: Job? = null

    // -------- public API --------

    /** Caller path. Either side may ring. */
    fun startCall() {
        callAudio.stop()
        val current = _state.value
        if (current is CallState.Connecting || current is CallState.InCall) return
        _state.value = CallState.Connecting

        sessionJob = viewModelScope.launch {
            try {
                webrtc.initialize()
                webrtc.createPeerConnection()
                _eglContext.value = webrtc.eglContext
                _localVideoTrack.value = webrtc.localVideoTrack
                webrtc.startCapture()

                val offer = webrtc.createOffer()
                WebRtcLog.transition("OFFER publish started")
                signaling.publishOffer(offer.sdp).getOrThrow()
                WebRtcLog.transition("OFFER published")

                listenAnswerSeq()
                listenCandidates()
                listenStatus()
                watchRinging()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                reportError(t)
            }
        }
    }

    /** Callee path. Either side may answer. */
    fun answerCall() {
        callAudio.stop()
        val current = _state.value
        if (current is CallState.Connecting || current is CallState.InCall) return
        _state.value = CallState.Connecting

        sessionJob = viewModelScope.launch {
            try {
                webrtc.initialize()
                webrtc.createPeerConnection()
                _eglContext.value = webrtc.eglContext
                _localVideoTrack.value = webrtc.localVideoTrack
                webrtc.startCapture()

                val sequenced = signaling.fetchOffer().getOrThrow()
                lastAppliedSeq = sequenced.seq
                webrtc.setRemoteDescription(sequenced.description)
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
                listenAnswerSeq()
                listenCandidates()
                listenStatus()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                reportError(t)
            }
        }
    }

    /**
     * Incoming overlay only: is there a live, answerable ring right now?
     * True → caller sets the Incoming state. False → caller navigates back.
     * Own ringback and stale generations answer false (never strand).
     */
    suspend fun checkIncomingCall(): Boolean {
        val result = signaling.fetchOffer()
        return result.isSuccess
    }

    /** Callee decline: status flip so the caller sees it, then reset + home. */
    suspend fun declineAndAwait() {
        callAudio.stop()
        withTimeoutOrNull(3_000) { signaling.declineCall() }
        cancelSessionJobs()
        resetCallState()
    }

    /** Local hangup: reset now; room ENDED best-effort in the background. */
    fun endCall() {
        callAudio.stop()
        cancelSessionJobs()
        resetCallState()
        viewModelScope.launch {
            withTimeoutOrNull(3_000) { signaling.endCall() }
        }
    }

    /**
     * Local user-initiated hangup with guaranteed room update. Await the
     * ENDED write BEFORE the caller navigates: popping the screen clears
     * this VM and would cancel a fire-and-forget write, stranding the peer
     * (device-proven). Offline can't trap us — the timeout guarantees
     * navigation proceeds.
     */
    suspend fun endCallAndAwait() {
        callAudio.stop()
        cancelSessionJobs()
        resetCallState()
        withTimeoutOrNull(3_000) { signaling.endCall() }
    }

    fun onToggleCamera() {
        _isCameraOn.update { !it }
        webrtc.toggleCamera()
    }

    /** Writes a locally-gathered ICE candidate to the static room. */
    fun onLocalIceCandidate(candidate: IceCandidate) {
        viewModelScope.launch {
            signaling.addIceCandidate(candidate).onFailure(::reportError)
        }
    }

    /** Exposes the WebRTCClient for the game bridge. Null before init. */
    fun webrtcClientOrNull(): WebRTCClient? = webrtc

    /**
     * Called from the UI when ConnectionHealth.LOST is observed.
     * Caller-only: the callee never initiates ICE restarts (it answers
     * them via the offer-watcher below). Role derives from state — no
     * extra field to clear.
     */
    fun onReconnectRequested() {
        val s = _state.value
        if (s !is CallState.InCall || s.role != CallRole.CALLER) return
        viewModelScope.launch {
            val newOffer = webrtc.restartIce() ?: run {
                WebRtcLog.transition("ICE restart produced no offer")
                return@launch
            }
            signaling.updateOffer(newOffer.sdp).onFailure(::reportError)
            WebRtcLog.transition("ICE restart offer published")
        }
    }

    /** Dismisses an [CallState.Error] back to Idle so the child can retry. */
    fun clearError() {
        callAudio.stop()
        if (_state.value is CallState.Error) _state.value = CallState.Idle
    }

    /**
     * QA-only. Drives the Incoming overlay without a ring or push.
     * FCM is the real trigger; kept for tests.
     */
    @VisibleForTesting
    fun simulateIncomingCall() {
        if (_state.value !is CallState.Idle) return
        _state.value = CallState.Incoming(fromDisplayName = "Dad")
        callAudio.start()
    }

    override fun onCleared() {
        callAudio.stop()
        sessionJob?.cancel()
        timerJob?.cancel()
        webrtc.dispose()
        super.onCleared()
    }

    // -------- listeners --------

    /**
     * Caller: sequenced ANSWER → remote → InCall (stale generations
     * ignored via lastAppliedSeq). Callee: sequenced re-OFFER mid-call
     * (caller ICE restart) → apply + answer. One collector serves both.
     */
    private fun listenAnswerSeq() {
        viewModelScope.launch {
            signaling.observeRemoteDescriptionWithSeq(SdpType.ANSWER)
                .catch { reportError(it) }
                .collect { sequenced ->
                    if (sequenced.seq <= lastAppliedSeq) {
                        WebRtcLog.transition("Stale answer ignored")
                        return@collect
                    }
                    lastAppliedSeq = sequenced.seq
                    webrtc.setRemoteDescription(sequenced.description)
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
        viewModelScope.launch {
            signaling.observeRemoteDescriptionWithSeq(SdpType.OFFER)
                .catch { reportError(it) }
                .collect { sequenced ->
                    val s = _state.value
                    if (s is CallState.InCall && s.role == CallRole.CALLEE &&
                        sequenced.seq > lastAppliedSeq
                    ) {
                        lastAppliedSeq = sequenced.seq
                        webrtc.setRemoteDescription(sequenced.description)
                        WebRtcLog.transition("Remote re-OFFER applied")
                        val reAnswer = webrtc.createAnswer()
                        signaling.publishAnswer(reAnswer.sdp)
                        WebRtcLog.transition("Renegotiation ANSWER published")
                    }
                }
        }
    }

    private fun listenCandidates() {
        viewModelScope.launch {
            signaling.observeIceCandidates()
                .catch { reportError(it) }
                .collect { webrtc.addRemoteIceCandidate(it) }
        }
    }

    /** Status mirror: peer DECLINED/ENDED → silent home (both roles). */
    private fun listenStatus() {
        viewModelScope.launch {
            signaling.observeStatus()
                .catch { reportError(it) }
                .collect { status ->
                    if ((status == CallStatus.DECLINED || status == CallStatus.ENDED) &&
                        _state.value !is CallState.Idle
                    ) {
                        WebRtcLog.transition("Peer ended — leaving")
                        cancelSessionJobs()
                        resetCallState()
                    }
                }
        }
    }

    /**
     * Caller ringing with no answer: FCM wakeup can take tens of seconds on
     * a dozing phone. Give it 45s, then an honest error card (never silent —
     * the child should know Dad didn't pick up).
     */
    private fun watchRinging() {
        viewModelScope.launch {
            delay(45_000)
            if (_state.value is CallState.Connecting) {
                WebRtcLog.transition("Ring unanswered — leaving")
                cancelSessionJobs()
                resetCallState()
                _state.value = CallState.Error(
                    kind = SignalingErrorKind.TIMEOUT,
                    message = "Dad didn't answer. Try again later."
                )
            }
        }
    }

    /**
     * Zombie-call guard: media never connects (peer vanished mid-handshake).
     * LAN connects in ~1-3s proven; 15s then silent home. Revisit with TURN.
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

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _elapsedSeconds.update { it + 1 }
            }
        }
    }

    private fun cancelSessionJobs() {
        sessionJob?.cancel(); sessionJob = null
        timerJob?.cancel(); timerJob = null
    }

    private fun resetCallState() {
        _elapsedSeconds.value = 0
        _remoteVideoTrack.value = null
        _eglContext.value = null
        _localVideoTrack.value = null
        _peerConnected.value = false
        lastAppliedSeq = -1
        _state.value = CallState.Idle
    }

    private fun reportError(t: Throwable) {
        val failure = t as? SignalingFailure
        // Kind-name only: guardrail-compliant, distinguishes hang (no line)
        // from failure (this line) in logcat.
        WebRtcLog.transition("Call failed: ${failure?.kind?.name ?: "UNKNOWN"}")
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
    Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60
)
