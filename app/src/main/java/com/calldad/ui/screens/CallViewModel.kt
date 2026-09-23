// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallViewModel.kt — static room + seq-tracked SDP
// Location: app/src/main/java/com/calldad/ui/screens/CallViewModel.kt
package com.calldad.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.BuildConfig
import com.calldad.R
import com.calldad.data.signaling.CallDocument
import com.calldad.data.signaling.IceCandidate
import com.calldad.data.signaling.PeerBusyException
import com.calldad.data.signaling.SignalingClient
import com.calldad.data.signaling.TransactionExhaustedException
import com.calldad.pairing.SecurePeerStore
import com.calldad.webrtc.ConnectionHealth
import com.calldad.webrtc.WebRTCClient
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import java.util.Locale

class CallViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val STATIC_ROOM_ID = "family_channel"
    }

    private val signaling: SignalingClient = SignalingClient()

    private val webrtc: WebRTCClient = WebRTCClient(
        context = application.applicationContext,
        onLocalIceCandidate = { candidate -> onLocalIceCandidate(candidate) },
        onRemoteVideoTrack = { track -> _remoteVideoTrack.value = track },
        onConnectionStateChanged = { }
    )

    private val peerStore = SecurePeerStore(getApplication())
    private var listenerSilentSince: Long = System.currentTimeMillis()

    private var lastAppliedOfferSeq: Int = -1
    private var lastAppliedAnswerSeq: Int = -1

    private var currentSeq: Int = 0

    private var amCaller: Boolean = false

    /** SDP payloads already fed to the PeerConnection (dedupe trickle). */
    private val appliedCandidates = mutableSetOf<String>()

    private val peerConnectionMutex = Mutex()

    private var autoDismissJob: Job? = null
    private var noAnswerJob: Job? = null
    private var elapsedJob: Job? = null
    private var listenerWatchdogJob: Job? = null
    private var callObserverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastPauseTime: Long = 0L

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

    val connectionHealth: StateFlow<ConnectionHealth> = webrtc.connectionHealth

    init {
        startListenerWatchdog()
        restartCallObserver()
    }

    private fun restartCallObserver() {
        callObserverJob?.cancel()
        callObserverJob = viewModelScope.launch {
            signaling.observeCall(STATIC_ROOM_ID)
                .catch { t -> reportError(t) }
                .collect { doc -> handleDocument(doc) }
        }
    }

    // -------- public API --------

    fun startCall() {
        if (_state.value !is CallState.Idle) return
        val callerUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            _state.value = CallState.Error(
                CallErrorKind.SIGNALING_FAILED,
                "Not signed in yet. Please wait a moment."
            )
            return
        }
        viewModelScope.launch {
            // Any throw below (peer store, WebRTC, publish) must surface
            // as an Error card, never a stuck screen. Cancellation still
            // propagates so teardown stays prompt.
            try {
            val calleeUid = withTimeout(5_000) { peerStore.observePeerUid().first() }
            if (calleeUid.isNullOrBlank()) {
                _state.value = CallState.Error(
                    CallErrorKind.SIGNALING_FAILED,
                    "Not paired yet. Scan the code again."
                )
                return@launch
            }

            if (!webrtc.isInitialized()) {
                webrtc.initialize()
                webrtc.createPeerConnection()
                _eglContext.value = webrtc.eglContext
                _localVideoTrack.value = webrtc.localVideoTrack
                webrtc.startCapture()
            }

            val offer = withTimeout(10_000) { webrtc.createOffer() }
            amCaller = true
            WebRtcLog.transition("OFFER publish started")

            _state.value = CallState.Ringing(
                seq = currentSeq,
                isIncoming = false,
                peerName = peerDisplayName(),
                callId = STATIC_ROOM_ID
            )

            signaling.publishOffer(
                callId = STATIC_ROOM_ID,
                offerSdp = offer.sdp,
                callerUid = callerUid,
                calleeUid = calleeUid
            ).onSuccess { seq ->
                WebRtcLog.transition("OFFER published")
                appliedCandidates.clear()
                currentSeq = seq
                _state.value = CallState.Ringing(
                    seq = seq,
                    isIncoming = false,
                    peerName = peerDisplayName(),
                    callId = STATIC_ROOM_ID
                )
                startNoAnswerTimer(seq)
            }.onFailure { t -> reportError(t) }
            } catch (t: TimeoutCancellationException) {
                reportError(IllegalStateException("Setup timed out. Tap to try again."))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                reportError(t)
            }
        }
    }

    fun answerCall() {
        val current = _state.value as? CallState.Ringing ?: return
        if (!current.isIncoming) return
        viewModelScope.launch {
            try {
            if (!webrtc.isInitialized()) {
                webrtc.initialize()
                webrtc.createPeerConnection()
                _eglContext.value = webrtc.eglContext
                _localVideoTrack.value = webrtc.localVideoTrack
                webrtc.startCapture()
            }
            amCaller = false
            // First-call defense: if the factory was created between
            // the Ringing transition and now, the offer was not applied
            // in applyDocumentPayload. Fetch and apply it here BEFORE
            // createAnswer. Sequence-tracked so a re-ring at higher seq
            // is not re-applied twice.
            signaling.fetchCall(STATIC_ROOM_ID).onSuccess { doc ->
                val offer = doc.offer
                if (offer != null && doc.seq > lastAppliedOfferSeq) {
                    runCatching { webrtc.setRemoteDescription(offer) }
                    lastAppliedOfferSeq = doc.seq
                }
            }

            val answer = withTimeout(10_000) { webrtc.createAnswer() }
            signaling.publishAnswer(
                callId = STATIC_ROOM_ID,
                answerSdp = answer.sdp
            ).onSuccess {
                WebRtcLog.transition("ANSWER published")
            }.onFailure(::reportError)
            } catch (t: TimeoutCancellationException) {
                reportError(IllegalStateException("Setup timed out. Tap to try again."))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                reportError(t)
            }
        }
    }

    fun onReRing() {
        val current = _state.value
        val allowed = when (current) {
            is CallState.NoAnswer -> true
            is CallState.Error -> when (current.kind) {
                CallErrorKind.PEER_BUSY,
                CallErrorKind.TRANSACTION_EXHAUSTED,
                CallErrorKind.SIGNALING_FAILED,
                CallErrorKind.WEBRTC_FAILED,
                CallErrorKind.LISTENER_DISCONNECTED -> true
                else -> false
            }
            else -> false
        }
        if (!allowed) return

        val callerUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            _state.value = CallState.Error(
                CallErrorKind.UNKNOWN,
                "Missing pairing data"
            )
            return
        }

        viewModelScope.launch {
            try {
            val calleeUid = withTimeout(5_000) { peerStore.observePeerUid().first() }
            if (calleeUid.isNullOrBlank()) {
                _state.value = CallState.Error(
                    CallErrorKind.UNKNOWN,
                    "Missing pairing data"
                )
                return@launch
            }

            if (!webrtc.isInitialized()) {
                webrtc.initialize()
                webrtc.createPeerConnection()
            }

            val newOffer = webrtc.restartIce()
            if (newOffer == null) {
                _state.value = CallState.Error(
                    kind = CallErrorKind.WEBRTC_FAILED,
                    message = "Could not prepare the call. Tap to try again.",
                    seq = currentSeq
                )
                return@launch
            }

            signaling.publishOffer(
                callId = STATIC_ROOM_ID,
                offerSdp = newOffer.sdp,
                callerUid = callerUid,
                calleeUid = calleeUid
            ).onSuccess { seq ->
                appliedCandidates.clear()
                currentSeq = seq
                _state.value = CallState.Ringing(
                    seq = seq,
                    isIncoming = false,
                    peerName = peerDisplayName(),
                    callId = STATIC_ROOM_ID
                )
                startNoAnswerTimer(seq)
            }.onFailure { t -> reportError(t) }
            } catch (t: TimeoutCancellationException) {
                reportError(IllegalStateException("Setup timed out. Tap to try again."))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                reportError(t)
            }
        }
    }

    fun declineCall() {
        viewModelScope.launch { signaling.declineCall(STATIC_ROOM_ID) }
        stopElapsedTimer()
        _state.value = CallState.Declined
    }

    fun endCall() {
        viewModelScope.launch { signaling.endCall(STATIC_ROOM_ID) }
        autoDismissJob?.cancel()
        noAnswerJob?.cancel()
        listenerWatchdogJob?.cancel()
        heartbeatJob?.cancel()
        heartbeatJob = null
        webrtc.dispose()
        lastAppliedOfferSeq = -1
        stopElapsedTimer()
        lastAppliedAnswerSeq = -1
        appliedCandidates.clear()
        _state.value = CallState.Ended(EndReason.LOCAL_HANGUP)
    }

    fun clearError() {
        if (_state.value is CallState.Error) {
            noAnswerJob?.cancel()
            noAnswerJob = null
            heartbeatJob?.cancel()
            heartbeatJob = null
            stopElapsedTimer()
            _state.value = CallState.Idle
        }
    }

    fun onToggleCamera() {
        _isCameraOn.value = !_isCameraOn.value
        webrtc.toggleCamera()
    }

    /**
     * Incoming overlay only: is there a live, answerable ring right now?
     * Own ringback answers false (never strand on our own echo).
     */
    suspend fun checkIncomingCall(): Boolean {
        val localUid = FirebaseAuth.getInstance().currentUser?.uid
        val doc = signaling.fetchCall(STATIC_ROOM_ID).getOrNull() ?: return false
        if (doc.status != "RINGING" || doc.offer == null) return false
        return doc.callerUid.isNotEmpty() && doc.callerUid != localUid
    }

    /** Exposes the WebRTCClient for the game bridge. Null before init. */
    fun webrtcClientOrNull(): WebRTCClient? = webrtc

    /**
     * Trickles a locally-gathered candidate into the room arrays.
     * Fire-and-forget: a single lost candidate never fails the call;
     * regather and ICE restart cover gaps.
     */
    fun onLocalIceCandidate(candidate: IceCandidate) {
        viewModelScope.launch {
            runCatching {
                signaling.addIceCandidate(STATIC_ROOM_ID, candidate, amCaller).getOrThrow()
            }
        }
    }

    /**
     * Returns true when the screen should be kept awake. Used by
     * CallScreen to toggle FLAG_KEEP_SCREEN_ON.
     */
    fun shouldKeepScreenOn(): Boolean {
        val s = _state.value
        return s is CallState.Ringing || s is CallState.Connected
    }

    override fun onCleared() {
        autoDismissJob?.cancel()
        noAnswerJob?.cancel()
        listenerWatchdogJob?.cancel()
        heartbeatJob?.cancel()
        heartbeatJob = null
        webrtc.dispose()
        super.onCleared()
    }

    // -------- document pipeline --------

    private fun handleDocument(doc: CallDocument) {
        listenerSilentSince = System.currentTimeMillis()

        val localUid = FirebaseAuth.getInstance().currentUser?.uid
        val isLocalCaller = doc.callerUid.isNotEmpty() &&
            doc.callerUid == localUid

        val incomingSeq = doc.seq
        when {
            incomingSeq < currentSeq -> {
                WebRtcLog.transition("Stale doc seq ignored")
            }
            incomingSeq == currentSeq -> {
                applyDocumentPayload(doc)
            }
            incomingSeq > currentSeq -> {
                if (doc.status == "RINGING") {
                    autoDismissJob?.cancel()
                    autoDismissJob = null
                    noAnswerJob?.cancel()
                    noAnswerJob = null
                }
                currentSeq = incomingSeq
                appliedCandidates.clear()

                val isActiveGeneration =
                    doc.status == "RINGING" || doc.status == "CONNECTED"

                if (isActiveGeneration && !isLocalCaller &&
                    webrtc.isInitialized()) {
                    viewModelScope.launch {
                        peerConnectionMutex.withLock {
                            webrtc.resetPeerConnection()
                            applyDocumentPayload(doc)
                        }
                    }
                } else {
                    applyDocumentPayload(doc)
                }
            }
        }
    }

    private fun applyDocumentPayload(doc: CallDocument) {
        if (_state.value is CallState.Idle &&
            (doc.status == "ENDED" || doc.status == "DECLINED")) {
            return
        }

        val localUid = FirebaseAuth.getInstance().currentUser?.uid
        val isCallee = doc.calleeUid == localUid

        val newState = CallState.fromDocument(
            status = doc.status,
            seq = doc.seq,
            isIncoming = isCallee,
            peerName = if (isCallee) callerDisplayName() else peerDisplayName(),
            callId = doc.callId
        )

        feedNewRemoteCandidates(doc)

        if (newState == _state.value) return
        if (!canTransition(_state.value, newState)) {
            WebRtcLog.transition("Illegal transition blocked")
            return
        }

        if (newState is CallState.Ringing && newState.isIncoming) {
            WebRtcLog.transition("Remote ring observed")
        }

        if (newState is CallState.Connected) {
            noAnswerJob?.cancel()
            noAnswerJob = null
            startElapsedTimer()
            startHeartbeat()
        }

        // Sequence-tracked SDP application. Runs only on committed
        // transitions. Re-ring at higher seq applies the fresh offer;
        // same-seq metadata echoes are skipped.
        if (webrtc.isInitialized()) {
            if (isCallee && doc.offer != null &&
                doc.seq > lastAppliedOfferSeq) {
                runCatching { webrtc.setRemoteDescription(doc.offer) }
                lastAppliedOfferSeq = doc.seq
            }
            if (!isCallee && doc.answer != null &&
                doc.seq > lastAppliedAnswerSeq) {
                runCatching { webrtc.setRemoteDescription(doc.answer) }
                lastAppliedAnswerSeq = doc.seq
            }
        }

        _state.value = newState
        scheduleAutoDismiss(newState)
    }

    /**
     * Feeds ICE candidates the peer trickled into the room arrays. Runs on
     * EVERY document delivery (not only committed transitions): candidates
     * arrive as same-seq metadata echoes the state machine skips.
     * Already-applied payloads are deduped; a new generation clears the set.
     */
    private fun feedNewRemoteCandidates(doc: CallDocument) {
        if (!webrtc.isInitialized()) return
        val remote = if (amCaller) doc.calleeCandidates else doc.callerCandidates
        remote.forEach { candidate ->
            if (appliedCandidates.add(candidate.sdpCandidate)) {
                runCatching { webrtc.addRemoteIceCandidate(candidate) }
            }
        }
    }

    private fun canTransition(from: CallState, to: CallState): Boolean {
        if (from::class == to::class) return true

        return when (from) {
            is CallState.Idle -> to is CallState.Ringing
            is CallState.Ringing ->
                to is CallState.Connected
                || to is CallState.Declined
                || to is CallState.NoAnswer
                || to is CallState.Ended
                || to is CallState.Error
            is CallState.Connected ->
                to is CallState.Ringing
                || to is CallState.Ended
                || to is CallState.Error
            is CallState.NoAnswer ->
                to is CallState.Ringing || to is CallState.Ended
            is CallState.Declined -> to is CallState.Idle
            is CallState.Ended ->
                to is CallState.Idle || to is CallState.Ringing
            is CallState.Error ->
                to is CallState.Idle || to is CallState.Ringing
        }
    }

    private fun peerDisplayName(): String =
        getApplication<Application>().getString(
            if (BuildConfig.APP_THEME == "blue")
                R.string.parent_peer_name
            else
                R.string.child_peer_name
        )

    /**
     * Name shown on the INCOMING overlay: the caller's side, i.e. the
     * opposite flavor's label. The callee must see who is calling them,
     * not their own peer label (parent sees "Mama", child sees "Dad").
     */
    private fun callerDisplayName(): String =
        getApplication<Application>().getString(
            if (BuildConfig.APP_THEME == "blue")
                R.string.child_peer_name
            else
                R.string.parent_peer_name
        )

    private fun scheduleAutoDismiss(state: CallState) {
        autoDismissJob?.cancel()
        autoDismissJob = null
        if (state is CallState.Declined || state is CallState.Ended) {
            stopElapsedTimer()
            autoDismissJob = viewModelScope.launch {
                delay(2_000)
                _state.value = CallState.Idle
            }
        }
    }

    private fun startElapsedTimer() {
        elapsedJob?.cancel()
        _elapsedSeconds.value = 0
        elapsedJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _elapsedSeconds.update { it + 1 }
            }
        }
    }

    private fun stopElapsedTimer() {
        elapsedJob?.cancel()
        elapsedJob = null
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(120_000L)
                val ownUid = FirebaseAuth.getInstance()
                    .currentUser?.uid ?: continue
                runCatching {
                    signaling.heartbeat(STATIC_ROOM_ID, ownUid)
                }
            }
        }
    }

    private fun startNoAnswerTimer(seq: Int) {
        noAnswerJob?.cancel()
        noAnswerJob = viewModelScope.launch {
            delay(15_000)
            val current = _state.value
            if (current is CallState.Ringing &&
                current.seq == seq) {
                WebRtcLog.transition("No answer after 15s")
                _state.value = CallState.NoAnswer(seq)
                runCatching { webrtc.dispose() }
            }
        }
    }

    private fun startListenerWatchdog() {
        listenerWatchdogJob?.cancel()
        listenerSilentSince = System.currentTimeMillis()
        listenerWatchdogJob = viewModelScope.launch {
            delay(10_000)
            val elapsed = System.currentTimeMillis() - listenerSilentSince
            if (elapsed >= 10_000 && _state.value !is CallState.Error) {
                val iceDead = webrtc.connectionHealth.value !=
                    ConnectionHealth.HEALTHY
                if (iceDead) {
                    _state.value = CallState.Error(
                        kind = CallErrorKind.LISTENER_DISCONNECTED,
                        message = "Connection lost. Please try again.",
                        seq = currentSeq
                    )
                }
            }
        }
    }

    fun onResume() {
        val pausedDuration = System.currentTimeMillis() - lastPauseTime
        if (lastPauseTime > 0L && pausedDuration > 60_000L) {
            WebRtcLog.transition("Resume after long pause: re-attaching")
            restartCallObserver()
        }
        listenerSilentSince = System.currentTimeMillis()

        listenerWatchdogJob?.cancel()
        listenerWatchdogJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                val elapsed =
                    System.currentTimeMillis() - listenerSilentSince
                if (elapsed >= 60_000) {
                    WebRtcLog.transition("Listener stalled: re-subscribing")
                    restartCallObserver()
                    listenerSilentSince = System.currentTimeMillis()
                }
            }
        }
    }

    fun onPause() {
        lastPauseTime = System.currentTimeMillis()
        listenerWatchdogJob?.cancel()
        listenerWatchdogJob = null
    }

    private fun reportError(t: Throwable) {
        val (kind, message) = when (t) {
            is PeerBusyException ->
                CallErrorKind.PEER_BUSY to
                    "Dad is on another call. Try again in a minute."
            is TransactionExhaustedException ->
                CallErrorKind.TRANSACTION_EXHAUSTED to
                    "The line is busy. Tap to try again."
            is FirebaseFirestoreException ->
                when (t.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        CallErrorKind.LISTENER_DISCONNECTED to
                            "No internet connection. Check Wi-Fi."
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        CallErrorKind.PERMISSION_DENIED to
                            "Calling is not allowed right now."
                    else ->
                        CallErrorKind.SIGNALING_FAILED to
                            "Something went wrong. Tap to try again."
                }
            else ->
                CallErrorKind.UNKNOWN to
                    (t.message ?: "Something went wrong.")
        }
        WebRtcLog.transition("Call failed: ${kind.name}")
        _state.value = CallState.Error(kind = kind, message = message, seq = currentSeq)
    }
}

/** Formats elapsed seconds as mm:ss for the CallScreen timer. */
fun formatElapsed(totalSeconds: Int): String = String.format(
    Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60
)
