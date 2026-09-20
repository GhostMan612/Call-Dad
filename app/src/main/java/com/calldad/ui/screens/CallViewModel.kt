// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallViewModel.kt — Phase 5: per-call rooms + status machine
// Location: app/src/main/java/com/calldad/ui/screens/CallViewModel.kt
package com.calldad.ui.screens

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.BuildConfig
import com.calldad.data.signaling.CallDocument
import com.calldad.data.signaling.CallStatus
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
            val id = currentCallId
            if (id != null) {
                viewModelScope.launch {
                    signaling.addIceCandidate(id, candidate).onFailure(::reportError)
                }
            } else {
                // Gathering starts at createPeerConnection(), but the room
                // only exists after the Firestore round-trip (~0.5s). Stash
                // early candidates instead of dropping them — on LAN these
                // host candidates ARE the connection (device-proven outage).
                synchronized(pendingLocalCandidates) {
                    pendingLocalCandidates.add(candidate)
                }
            }
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

    private var currentCallId: String? = null
    private val pendingLocalCandidates = mutableListOf<IceCandidate>()
    private var timerJob: Job? = null
    private var sessionJob: Job? = null

    // -------- public API --------

    /**
     * Caller path. Callee address comes from local.properties (CALLEE_UID,
     * operator-provisioned per phone). Blank = not provisioned → kid-safe
     * error card, never a crash.
     */
    fun startCall() {
        val current = _state.value
        if (current is CallState.Connecting || current is CallState.InCall) return
        val callee = BuildConfig.CALLEE_UID
        if (callee.isBlank()) {
            _state.value = CallState.Error(
                kind = SignalingErrorKind.UNKNOWN,
                message = "Calling isn't set up yet. Ask a parent for help."
            )
            return
        }
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
                val callId = signaling.createCallRoom(callee, offer.sdp).getOrThrow()
                currentCallId = callId
                flushPendingCandidates(callId)
                WebRtcLog.transition("OFFER published")

                listenCall(callId)
                listenCandidates(callId)
                listenHangup(callId)
                watchRinging(callId)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                reportError(t)
            }
        }
    }

    /**
     * Callee path. callId arrives via the ring pointer (app-open) or the
     * FCM full-screen intent (killed-app).
     */
    fun answerCall(callId: String) {
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

                val doc = signaling.fetchCall(callId).getOrThrow()
                validateRinging(doc)
                webrtc.setRemoteDescription(SessionDescription(SdpType.OFFER, doc.offer!!))
                WebRtcLog.transition("Remote OFFER applied")

                val answer = webrtc.createAnswer()
                WebRtcLog.transition("ANSWER publish started")
                signaling.publishAnswer(callId, answer.sdp).getOrThrow()
                WebRtcLog.transition("ANSWER published")

                currentCallId = callId
                flushPendingCandidates(callId)
                _state.value = CallState.InCall(
                    role = CallRole.CALLEE,
                    startedAtMillis = System.currentTimeMillis()
                )
                startTimer()
                watchConnection()
                listenCandidates(callId)
                listenHangup(callId)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                reportError(t)
            }
        }
    }

    /** Callee decline: status flip so the caller sees it, then reset + home. */
    suspend fun declineAndAwait(callId: String?) {
        if (!callId.isNullOrBlank()) {
            withTimeoutOrNull(3_000) { signaling.declineCall(callId) }
        }
        cancelSessionJobs()
        resetCallState()
        currentCallId = null
    }

    /** Local hangup: reset now; room ENDED best-effort in the background. */
    fun endCall() {
        val id = currentCallId
        cancelSessionJobs()
        resetCallState()
        currentCallId = null
        viewModelScope.launch {
            if (id != null) withTimeoutOrNull(3_000) { signaling.endCall(id) }
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
        val id = currentCallId
        cancelSessionJobs()
        resetCallState()
        currentCallId = null
        if (id != null) withTimeoutOrNull(3_000) { signaling.endCall(id) }
    }

    fun onToggleCamera() {
        _isCameraOn.update { !it }
        webrtc.toggleCamera()
    }

    /** Dismisses an [CallState.Error] back to Idle so the child can retry. */
    fun clearError() {
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
    }

    /**
     * Incoming overlay only: follow a specific call room home when it stops
     * ringing (declined/ended/deleted) before Answer.
     */
    fun watchIncomingCall(callId: String) {
        viewModelScope.launch {
            signaling.observeCall(callId)
                .catch { /* stay; Answer path reports properly */ }
                .collect { doc ->
                    if (_state.value is CallState.Incoming && doc.status != CallStatus.RINGING) {
                        WebRtcLog.transition("Ring ended before answer — leaving")
                        resetCallState()
                    }
                }
        }
        viewModelScope.launch {
            signaling.observeCallDeleted(callId)
                .catch { /* stay; Answer path reports properly */ }
                .collect {
                    if (_state.value is CallState.Incoming) {
                        WebRtcLog.transition("Room gone before answer — leaving")
                        resetCallState()
                    }
                }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        timerJob?.cancel()
        webrtc.dispose()
        super.onCleared()
    }

    // -------- listeners --------

    /** Caller: ANSWER → remote → InCall; DECLINED/ENDED → silent home. */
    private fun listenCall(callId: String) {
        viewModelScope.launch {
            signaling.observeCall(callId)
                .catch { reportError(it) }
                .collect { doc ->
                    when (doc.status) {
                        CallStatus.CONNECTED -> {
                            val ans = doc.answer
                            if (!ans.isNullOrBlank() && _state.value is CallState.Connecting) {
                                webrtc.setRemoteDescription(
                                    SessionDescription(SdpType.ANSWER, ans)
                                )
                                WebRtcLog.transition("Remote ANSWER applied")
                                _state.value = CallState.InCall(
                                    role = CallRole.CALLER,
                                    startedAtMillis = System.currentTimeMillis()
                                )
                                startTimer()
                                watchConnection()
                            }
                        }
                        CallStatus.DECLINED, CallStatus.ENDED -> {
                            if (_state.value !is CallState.Idle) {
                                WebRtcLog.transition("Peer ended — leaving")
                                cancelSessionJobs()
                                resetCallState()
                            }
                        }
                        CallStatus.RINGING -> Unit
                    }
                }
        }
    }

    /** Sends candidates gathered before the room existed. */
    private fun flushPendingCandidates(callId: String) {
        val pending = synchronized(pendingLocalCandidates) {
            if (pendingLocalCandidates.isEmpty()) return
            pendingLocalCandidates.toList().also { pendingLocalCandidates.clear() }
        }
        viewModelScope.launch {
            pending.forEach { signaling.addIceCandidate(callId, it) }
        }
    }

    private fun listenCandidates(callId: String) {
        viewModelScope.launch {
            signaling.observeIceCandidates(callId)
                .catch { reportError(it) }
                .collect { webrtc.addRemoteIceCandidate(it) }
        }
    }

    /** Deletion backup: docs are status-driven, but never strand on vanish. */
    private fun listenHangup(callId: String) {
        viewModelScope.launch {
            signaling.observeCallDeleted(callId)
                .catch { reportError(it) }
                .collect {
                    WebRtcLog.transition("Remote hangup observed")
                    cancelSessionJobs()
                    resetCallState()
                }
        }
    }

    /**
     * Caller ringing with no answer: FCM wakeup can take tens of seconds on
     * a dozing phone. Give it 45s, then an honest error card (never silent —
     * the child should know Dad didn't pick up).
     */
    private fun watchRinging(callId: String) {
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

    private fun validateRinging(doc: CallDocument) {
        if (doc.status != CallStatus.RINGING || doc.offer.isNullOrBlank() || doc.isStale()) {
            throw SignalingFailure(
                SignalingErrorKind.NOT_FOUND, "Call room does not exist yet."
            )
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
        synchronized(pendingLocalCandidates) { pendingLocalCandidates.clear() }
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
