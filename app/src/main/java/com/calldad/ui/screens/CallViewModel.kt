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
import com.calldad.data.signaling.SignalingClient
import com.calldad.data.signaling.SignalingErrorKind
import com.calldad.data.signaling.SignalingFailure
import com.calldad.webrtc.WebRTCClient
import com.calldad.webrtc.WebRtcLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private var timerJob: Job? = null
    private var remoteListenerJob: Job? = null

    // -------- public API --------

    fun startCall() {
        val current = _state.value
        if (current is CallState.Connecting || current is CallState.InCall) return
        _state.value = CallState.Connecting

        viewModelScope.launch {
            try {
                webrtc.initialize()
                webrtc.createPeerConnection()
                webrtc.startCapture()

                val offer = webrtc.createOffer()
                signaling.publishOffer(offer.sdp).getOrThrow()
                WebRtcLog.transition("OFFER published")

                listenForAnswer()
                listenForRemoteCandidates()
            } catch (t: Throwable) {
                reportError(t)
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
                webrtc.startCapture()

                val offer = signaling.fetchOffer().getOrThrow()
                webrtc.setRemoteDescription(offer)
                WebRtcLog.transition("Remote OFFER applied")

                val answer = webrtc.createAnswer()
                signaling.publishAnswer(answer.sdp).getOrThrow()
                WebRtcLog.transition("ANSWER published")

                _state.value = CallState.InCall(
                    role = CallRole.CALLEE,
                    startedAtMillis = System.currentTimeMillis()
                )
                startTimer()
                listenForRemoteCandidates()
            } catch (t: Throwable) {
                reportError(t)
            }
        }
    }

    fun onLocalIceCandidate(candidate: IceCandidate) {
        viewModelScope.launch {
            signaling.addIceCandidate(candidate).onFailure(::reportError)
        }
    }

    fun endCall() {
        remoteListenerJob?.cancel(); remoteListenerJob = null
        timerJob?.cancel(); timerJob = null
        _elapsedSeconds.value = 0
        _remoteVideoTrack.value = null
        _state.value = CallState.Idle
        webrtc.dispose()
        viewModelScope.launch { signaling.teardown() }
    }

    fun onToggleCamera() {
        _isCameraOn.update { !it }
        webrtc.toggleCamera()
    }

    fun clearError() {
        if (_state.value is CallState.Error) _state.value = CallState.Idle
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
        _state.value = CallState.Error(
            kind = failure?.kind ?: SignalingErrorKind.UNKNOWN,
            message = failure?.userMessage ?: t.message ?: "Something went wrong."
        )
    }
}

fun formatElapsed(totalSeconds: Int): String = String.format(
    Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60
)
