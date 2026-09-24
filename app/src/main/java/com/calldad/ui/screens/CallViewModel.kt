// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallViewModel.kt — activity-scoped call session (ADR-015)
// Location: app/src/main/java/com/calldad/ui/screens/CallViewModel.kt
package com.calldad.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.BuildConfig
import com.calldad.R
import com.calldad.audio.CallAudioManager
import com.calldad.data.session.FamilyPair
import com.calldad.data.session.FamilySession
import com.calldad.data.signaling.CallDocument
import com.calldad.data.signaling.CallNoLongerRingingException
import com.calldad.data.signaling.CallRoom
import com.calldad.data.signaling.IceCandidate
import com.calldad.data.signaling.SignalingClient
import com.calldad.fcm.CallForegroundService
import com.calldad.webrtc.ConnectionHealth
import com.calldad.webrtc.WebRTCClient
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import java.util.Locale

/**
 * The app's single call session. ACTIVITY-SCOPED (see [callViewModel]):
 * it outlives the call screen, so it observes the paired room from every
 * screen (incoming rings are never missed off Home) and the game screen
 * shares the live call's data channel.
 *
 * Media is per attempt: every call/answer builds a fresh [WebRTCClient]
 * and every exit path goes through [teardownMedia]. The EglBase is owned
 * here for the ViewModel's lifetime so renderers never outlive it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val signaling = SignalingClient()
    private val eglBase: EglBase = EglBase.create()

    /** Stable for the ViewModel's lifetime; renderers init with it. */
    val eglContext: EglBase.Context = eglBase.eglBaseContext

    private val rtcFlow = MutableStateFlow<WebRTCClient?>(null)
    private val rtc: WebRTCClient? get() = rtcFlow.value

    private var pair: FamilyPair? = null
    private var lastDoc: CallDocument? = null

    private var currentSeq = 0
    private var amCaller = false
    private var publishingOffer = false
    /**
     * Bumped on every teardown and new generation. A coroutine captures it
     * at start and does nothing after a suspension if it moved on: a slow
     * answer/offer from an old attempt can never touch a newer call.
     */
    private var attempt = 0
    private var answering = false
    private var localSdpPublished = false
    private val pendingLocalCandidates = mutableListOf<IceCandidate>()
    private val appliedRemoteCandidates = mutableSetOf<String>()
    private var answerAppliedSeq = -1

    private var roomJob: Job? = null
    private var awaitingFirstSnapshot = true
    private var autoDismissJob: Job? = null
    private var noAnswerJob: Job? = null
    private var elapsedJob: Job? = null
    private var connectionWatchJob: Job? = null

    private val _state = MutableStateFlow<CallState>(CallState.Idle)
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _isPaired = MutableStateFlow<Boolean?>(null)
    /** null until known; false = no contact yet (Call button explains). */
    val isPaired: StateFlow<Boolean?> = _isPaired.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _isCameraOn = MutableStateFlow(true)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn.asStateFlow()

    private val _isMicOn = MutableStateFlow(true)
    val isMicOn: StateFlow<Boolean> = _isMicOn.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    val connectionHealth: StateFlow<ConnectionHealth> = rtcFlow
        .flatMapLatest { it?.connectionHealth ?: flowOf(ConnectionHealth.HEALTHY) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionHealth.HEALTHY)

    /** Inbound game-sync messages of the live call (empty between calls). */
    val gameMessages: Flow<String> =
        rtcFlow.flatMapLatest { it?.gameSyncMessages ?: emptyFlow() }

    /** True once the game data channel can carry messages. */
    val canPlayTogether: StateFlow<Boolean> = state
        .map { it is CallState.Connected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            FamilySession.pair(app).collectLatest { p ->
                onPairChanged(p)
            }
        }
    }

    // -------- public API --------

    fun startCall() {
        val s = _state.value
        val canStart = s is CallState.Idle || s is CallState.NoAnswer ||
            s is CallState.Declined || s is CallState.Ended || s is CallState.Error
        if (!canStart || publishingOffer || answering) return
        val p = pair ?: run {
            _state.value = CallState.Error(
                CallErrorKind.NOT_PAIRED,
                "Not paired yet. Ask a grown-up to pair the phones."
            )
            return
        }

        publishingOffer = true
        autoDismissJob?.cancel()
        teardownMedia()
        beginGeneration(asCaller = true)
        _state.value = CallState.Ringing(currentSeq, false, peerDisplayName(), p.roomId)
        CallAudioManager.startRingback()
        val myAttempt = attempt

        viewModelScope.launch {
            try {
                val client = newClient()
                val offer = withTimeout(SETUP_TIMEOUT_MS) { client.createOffer() }
                WebRtcLog.transition("OFFER publish started")
                val seq = withTimeout(SETUP_TIMEOUT_MS) {
                    signaling.publishOffer(p.roomId, offer.sdp, p.ownUid, p.peerUid).getOrThrow()
                }
                WebRtcLog.transition("OFFER published")
                val stillCalling = _state.value.let { it is CallState.Ringing && !it.isIncoming }
                if (!stillCalling || rtc !== client || attempt != myAttempt) {
                    // Hung up (or failed) while the offer was in flight:
                    // cancel the ring we just started so the peer never
                    // rings for a call nobody is on.
                    signaling.finishCall(p.roomId, seq, "ENDED")
                    return@launch
                }
                localSdpPublished = true
                currentSeq = seq
                flushLocalCandidates()
                _state.value = CallState.Ringing(seq, false, peerDisplayName(), p.roomId)
                startNoAnswerTimer(seq)
            } catch (t: Throwable) {
                if (t is CancellationException && t !is TimeoutCancellationException) throw t
                if (attempt == myAttempt) fail(t)
            } finally {
                publishingOffer = false
                // Docs held back while publishing (our own echo, an answer
                // that beat the transaction result, or the peer's ring in
                // glare / after a failed publish) are replayed now.
                lastDoc?.takeIf { it.seq >= currentSeq }?.let { handleDocument(it) }
            }
        }
    }

    /** Try Again from NoAnswer or a recoverable Error: a brand-new call. */
    fun onReRing() = startCall()

    fun answerCall() {
        val s = _state.value as? CallState.Ringing ?: return
        if (!s.isIncoming || answering) return
        val p = pair ?: return
        answering = true
        CallAudioManager.stop()
        CallForegroundService.dismiss(app)
        noAnswerJob?.cancel(); noAnswerJob = null
        val myAttempt = attempt

        viewModelScope.launch {
            try {
                val doc = lastDoc?.takeIf { it.seq == s.seq && it.offer != null }
                    ?: signaling.fetchCall(p.roomId).getOrNull()
                        ?.takeIf { it.seq == s.seq && it.status == "RINGING" }
                    ?: throw CallNoLongerRingingException()
                val offer = doc.offer ?: throw CallNoLongerRingingException()
                if (attempt != myAttempt) return@launch

                val client = newClient()
                if (!client.setRemoteDescription(offer)) {
                    throw IllegalStateException("Could not read the call. Try again.")
                }
                feedRemoteCandidates(doc)
                val answer = withTimeout(SETUP_TIMEOUT_MS) { client.createAnswer() }
                withTimeout(SETUP_TIMEOUT_MS) {
                    signaling.publishAnswer(p.roomId, s.seq, answer.sdp).getOrThrow()
                }
                WebRtcLog.transition("ANSWER published")
                if (attempt != myAttempt || rtc !== client) {
                    signaling.finishCall(p.roomId, s.seq, "ENDED")
                    return@launch
                }
                localSdpPublished = true
                flushLocalCandidates()
                val now = _state.value
                if (now is CallState.Ringing && now.seq == s.seq) goConnected(s.seq)
            } catch (t: CallNoLongerRingingException) {
                if (attempt == myAttempt) {
                    teardownMedia()
                    commitTerminal(CallState.Ended(EndReason.MISSED))
                }
            } catch (t: Throwable) {
                if (t is CancellationException && t !is TimeoutCancellationException) throw t
                if (attempt == myAttempt) fail(t)
            } finally {
                if (attempt == myAttempt) answering = false
            }
        }
    }

    fun declineCall() {
        val s = _state.value as? CallState.Ringing ?: return
        if (!s.isIncoming) return
        val p = pair
        CallAudioManager.stop()
        CallForegroundService.dismiss(app)
        if (p != null) viewModelScope.launch { signaling.finishCall(p.roomId, s.seq, "DECLINED") }
        teardownMedia()
        commitTerminal(CallState.Declined)
    }

    /** Hang Up / Stop / back gesture. Safe from any state. */
    fun endCall() {
        val s = _state.value
        val p = pair
        when (s) {
            is CallState.Ringing -> if (s.isIncoming) {
                declineCall()
                return
            }
            else -> Unit
        }
        if (p != null && (s is CallState.Ringing || s is CallState.Connected)) {
            val seq = currentSeq
            viewModelScope.launch { signaling.finishCall(p.roomId, seq, "ENDED") }
        }
        teardownMedia()
        when (s) {
            is CallState.Ringing, is CallState.Connected ->
                commitTerminal(CallState.Ended(EndReason.LOCAL_HANGUP))
            else -> {
                autoDismissJob?.cancel()
                _state.value = CallState.Idle
            }
        }
    }

    fun clearError() {
        if (_state.value is CallState.Error) {
            teardownMedia()
            _state.value = CallState.Idle
        }
    }

    fun onToggleCamera() {
        val on = !_isCameraOn.value
        _isCameraOn.value = on
        rtc?.setCameraEnabled(on)
    }

    fun onToggleMic() {
        val on = !_isMicOn.value
        _isMicOn.value = on
        rtc?.setMicEnabled(on)
    }

    fun onSwitchCamera() {
        rtc?.switchCamera()
    }

    /** Screen locked / app backgrounded: camera pauses, call continues. */
    fun onUiHidden() {
        rtc?.setCameraEnabled(false)
    }

    /** Back in front: camera returns only if the kid had it on. */
    fun onUiVisible() {
        rtc?.setCameraEnabled(_isCameraOn.value)
    }

    fun sendGameData(json: String): Boolean = rtc?.sendGameData(json) ?: false

    override fun onCleared() {
        val s = _state.value
        pair?.let { p ->
            if (s is CallState.Connected ||
                (s is CallState.Ringing && !s.isIncoming)) {
                signaling.finishCallDetached(p.roomId, "ENDED")
            }
        }
        teardownMedia()
        runCatching { eglBase.release() }
        super.onCleared()
    }

    // -------- room pipeline --------

    private fun onPairChanged(p: FamilyPair?) {
        roomJob?.cancel()
        if (_state.value.isLive) {
            teardownMedia()
            _state.value = CallState.Idle
        }
        pair = p
        lastDoc = null
        currentSeq = 0
        _isPaired.value = p != null
        if (p == null) return
        roomJob = viewModelScope.launch {
            var backoffMs = 2_000L
            while (isActive) {
                try {
                    awaitingFirstSnapshot = true
                    signaling.observeCall(p.roomId).collect { doc ->
                        backoffMs = 2_000L
                        handleDocument(doc)
                        if (!doc.isFromCache) awaitingFirstSnapshot = false
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    WebRtcLog.transition("Room listener failed; retrying")
                    if (_state.value.isLive) fail(t)
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private fun handleDocument(doc: CallDocument) {
        val p = pair ?: return
        if (doc.callId != p.roomId) return
        lastDoc = doc
        if (doc.status == "IDLE") return

        val parties = setOf(doc.callerUid, doc.calleeUid)
        if (parties != setOf(p.ownUid, p.peerUid)) {
            WebRtcLog.transition("Foreign parties on room ignored")
            return
        }
        val mineAsCaller = doc.callerUid == p.ownUid
        // While our offer is in flight, only a NEWER generation from the
        // peer matters (glare); everything else waits for the replay.
        if (publishingOffer && (mineAsCaller || doc.seq <= currentSeq)) return

        when {
            doc.seq < currentSeq -> Unit
            doc.seq > currentSeq -> onNewGeneration(doc, p, mineAsCaller)
            else -> onSameGeneration(doc)
        }
    }

    private fun onNewGeneration(doc: CallDocument, p: FamilyPair, mineAsCaller: Boolean) {
        // New generations are only acted on from server truth: a cached
        // snapshot may be hours old, and advancing currentSeq on it would
        // swallow the real server snapshot that follows.
        if (doc.isFromCache) return
        val s = _state.value
        // A change delivered live by the listener is fresh by definition.
        // Only the first snapshot after (re)subscribing can be an old,
        // abandoned ring; only then does the timestamp decide.
        val fresh = !awaitingFirstSnapshot ||
            CallRoom.isFreshRing(doc.updatedAtMs, System.currentTimeMillis())

        if (doc.status == "RINGING" && !mineAsCaller) {
            if (publishingOffer) return
            if (!fresh || doc.offer == null) {
                currentSeq = doc.seq
                return
            }
            val glare = s is CallState.Ringing && !s.isIncoming
            if (s.isLive) teardownMedia()
            currentSeq = doc.seq
            beginGeneration(asCaller = false)
            autoDismissJob?.cancel()
            _state.value = CallState.Ringing(doc.seq, true, callerDisplayName(), p.roomId)
            WebRtcLog.transition("Remote ring observed")
            if (glare) {
                WebRtcLog.transition("Glare: both called, auto-answering")
                answerCall()
            } else {
                CallAudioManager.startRinging(app)
                startIncomingTimeout(doc.seq)
            }
            return
        }

        currentSeq = doc.seq
        val orphaned = doc.status == "CONNECTED" ||
            (doc.status == "RINGING" && mineAsCaller)
        when {
            s.isLive -> {
                teardownMedia()
                commitTerminal(CallState.Ended(EndReason.REMOTE_HANGUP))
            }
            orphaned -> {
                WebRtcLog.transition("Orphaned live room closed")
                viewModelScope.launch { signaling.finishCall(p.roomId, doc.seq, "ENDED") }
            }
        }
    }

    private fun onSameGeneration(doc: CallDocument) {
        val s = _state.value
        if (rtc != null) feedRemoteCandidates(doc)

        when (s) {
            is CallState.Ringing -> when {
                !s.isIncoming && doc.status == "CONNECTED" -> applyAnswerAndConnect(doc)
                !s.isIncoming && doc.status == "DECLINED" -> {
                    teardownMedia()
                    commitTerminal(CallState.Declined)
                }
                doc.status == "ENDED" || doc.status == "DECLINED" -> {
                    teardownMedia()
                    commitTerminal(
                        CallState.Ended(if (s.isIncoming) EndReason.MISSED else EndReason.REMOTE_HANGUP)
                    )
                }
                else -> Unit
            }
            is CallState.Connected ->
                if (doc.status == "ENDED" || doc.status == "DECLINED") {
                    teardownMedia()
                    commitTerminal(CallState.Ended(EndReason.REMOTE_HANGUP))
                }
            else -> Unit
        }
    }

    private fun applyAnswerAndConnect(doc: CallDocument) {
        val answer = doc.answer ?: return
        val client = rtc ?: return
        if (answerAppliedSeq == doc.seq) return
        answerAppliedSeq = doc.seq
        viewModelScope.launch {
            if (client.setRemoteDescription(answer) && rtc === client) {
                lastDoc?.takeIf { it.seq == doc.seq }?.let { feedRemoteCandidates(it) }
                val now = _state.value
                if (now is CallState.Ringing && now.seq == doc.seq) goConnected(doc.seq)
            } else if (rtc === client) {
                fail(IllegalStateException("Could not connect the call. Tap to try again."))
            }
        }
    }

    private fun feedRemoteCandidates(doc: CallDocument) {
        val client = rtc ?: return
        val remote = if (amCaller) doc.calleeCandidates else doc.callerCandidates
        remote.forEach { candidate ->
            if (appliedRemoteCandidates.add(candidate.sdpCandidate)) {
                client.addRemoteIceCandidate(candidate)
            }
        }
    }

    // -------- media --------

    private fun newClient(): WebRTCClient {
        lateinit var client: WebRTCClient
        client = WebRTCClient(
            context = app,
            eglBase = eglBase,
            onLocalIceCandidate = { candidate ->
                viewModelScope.launch { onLocalCandidate(client, candidate) }
            },
            onRemoteVideoTrack = { track ->
                if (rtc === client) _remoteVideoTrack.value = track
            }
        )
        rtcFlow.value = client
        client.start(cameraEnabled = _isCameraOn.value)
        client.setMicEnabled(_isMicOn.value)
        _localVideoTrack.value = client.localVideoTrack
        return client
    }

    private fun onLocalCandidate(client: WebRTCClient, candidate: IceCandidate) {
        if (rtc !== client) return
        if (!localSdpPublished) {
            pendingLocalCandidates.add(candidate)
            return
        }
        sendLocalCandidate(candidate)
    }

    private fun flushLocalCandidates() {
        val batch = pendingLocalCandidates.toList()
        pendingLocalCandidates.clear()
        batch.forEach { sendLocalCandidate(it) }
    }

    private fun sendLocalCandidate(candidate: IceCandidate) {
        val p = pair ?: return
        val byCaller = amCaller
        viewModelScope.launch {
            signaling.addIceCandidate(p.roomId, candidate, byCaller)
        }
    }

    /** The ONE exit path for media: ringers off, jobs off, UI detached, native freed. */
    private fun teardownMedia() {
        attempt += 1
        answering = false
        CallAudioManager.stop()
        noAnswerJob?.cancel(); noAnswerJob = null
        elapsedJob?.cancel(); elapsedJob = null
        connectionWatchJob?.cancel(); connectionWatchJob = null
        val old = rtcFlow.value
        rtcFlow.value = null
        _remoteVideoTrack.value = null
        _localVideoTrack.value = null
        old?.dispose()
    }

    private fun beginGeneration(asCaller: Boolean) {
        attempt += 1
        amCaller = asCaller
        localSdpPublished = false
        pendingLocalCandidates.clear()
        appliedRemoteCandidates.clear()
        answerAppliedSeq = -1
        _isCameraOn.value = true
        _isMicOn.value = true
        _elapsedSeconds.value = 0
    }

    // -------- state helpers --------

    private fun goConnected(seq: Int) {
        CallAudioManager.stop()
        noAnswerJob?.cancel(); noAnswerJob = null
        _state.value = CallState.Connected(seq)
        startElapsedTimer()
        startConnectionWatch()
    }

    private fun commitTerminal(next: CallState) {
        if (!CallState.canTransition(_state.value, next)) {
            _state.value = CallState.Idle
            return
        }
        _state.value = next
        autoDismissJob?.cancel()
        autoDismissJob = viewModelScope.launch {
            delay(AUTO_DISMISS_MS)
            if (_state.value == next) _state.value = CallState.Idle
        }
    }

    private fun startNoAnswerTimer(seq: Int) {
        noAnswerJob?.cancel()
        noAnswerJob = viewModelScope.launch {
            delay(CallRoom.NO_ANSWER_MS)
            val s = _state.value
            if (s is CallState.Ringing && !s.isIncoming && s.seq == seq) {
                WebRtcLog.transition("No answer")
                pair?.let { p ->
                    viewModelScope.launch { signaling.finishCall(p.roomId, seq, "ENDED") }
                }
                teardownMedia()
                _state.value = CallState.NoAnswer(seq)
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

    /**
     * A ring nobody ends (the caller's phone died or went offline, so its
     * no-answer write never landed) stops ringing here after a minute.
     */
    private fun startIncomingTimeout(seq: Int) {
        noAnswerJob?.cancel()
        noAnswerJob = viewModelScope.launch {
            delay(INCOMING_RING_MS)
            val s = _state.value
            if (s is CallState.Ringing && s.isIncoming && s.seq == seq && !answering) {
                WebRtcLog.transition("Incoming ring expired")
                pair?.let { p ->
                    viewModelScope.launch { signaling.finishCall(p.roomId, seq, "ENDED") }
                }
                CallForegroundService.dismiss(app)
                teardownMedia()
                commitTerminal(CallState.Ended(EndReason.MISSED))
            }
        }
    }

    /**
     * Ends a call nobody is really on: media never connected within
     * [FIRST_MEDIA_GRACE_MS] of the answer (dead peer, blocked network), or
     * the connection stayed LOST for [LOST_GRACE_MS] (crash, dead battery).
     */
    private fun startConnectionWatch() {
        connectionWatchJob?.cancel()
        val client = rtc ?: return
        connectionWatchJob = viewModelScope.launch {
            val connectedAt = System.currentTimeMillis()
            var lostSince = 0L
            while (isActive) {
                delay(1_000)
                val now = System.currentTimeMillis()
                val neverConnected = !client.iceEverConnected.value &&
                    now - connectedAt >= FIRST_MEDIA_GRACE_MS
                lostSince = when {
                    connectionHealth.value != ConnectionHealth.LOST -> 0L
                    lostSince == 0L -> now
                    else -> lostSince
                }
                val lostTooLong = lostSince != 0L && now - lostSince >= LOST_GRACE_MS
                if (neverConnected || lostTooLong) {
                    WebRtcLog.transition("Connection lost: ending call")
                    val seq = currentSeq
                    pair?.let { p ->
                        viewModelScope.launch { signaling.finishCall(p.roomId, seq, "ENDED") }
                    }
                    teardownMedia()
                    commitTerminal(CallState.Ended(EndReason.NETWORK_FAILURE))
                    return@launch
                }
            }
        }
    }

    private fun fail(t: Throwable) {
        val (kind, message) = when (t) {
            is TimeoutCancellationException ->
                CallErrorKind.WEBRTC_FAILED to "Setup timed out. Tap to try again."
            is FirebaseFirestoreException -> when (t.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    CallErrorKind.LISTENER_DISCONNECTED to "No internet connection. Check Wi-Fi."
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    CallErrorKind.PERMISSION_DENIED to
                        "Calling is not allowed right now. Ask a grown-up to pair the phones again."
                else ->
                    CallErrorKind.SIGNALING_FAILED to "Something went wrong. Tap to try again."
            }
            else -> CallErrorKind.UNKNOWN to (t.message ?: "Something went wrong. Tap to try again.")
        }
        WebRtcLog.transition("Call failed: ${kind.name}")
        val s = _state.value
        val seq = currentSeq
        pair?.let { p ->
            if (s is CallState.Connected || (s is CallState.Ringing && !s.isIncoming)) {
                viewModelScope.launch { signaling.finishCall(p.roomId, seq, "ENDED") }
            }
        }
        teardownMedia()
        _state.value = CallState.Error(kind = kind, message = message, seq = seq)
    }

    private fun peerDisplayName(): String =
        app.getString(
            if (BuildConfig.APP_THEME == "blue") R.string.parent_peer_name
            else R.string.child_peer_name
        )

    /**
     * Name shown on the INCOMING overlay: the caller's side, i.e. the
     * opposite flavor's label (parent sees "Mama", child sees "Dad").
     */
    private fun callerDisplayName(): String =
        app.getString(
            if (BuildConfig.APP_THEME == "blue") R.string.child_peer_name
            else R.string.parent_peer_name
        )

    private companion object {
        const val SETUP_TIMEOUT_MS = 10_000L
        const val AUTO_DISMISS_MS = 2_000L
        const val LOST_GRACE_MS = 20_000L
        const val FIRST_MEDIA_GRACE_MS = 25_000L
        const val INCOMING_RING_MS = 60_000L
    }
}

/** Formats elapsed seconds as mm:ss for the CallScreen timer. */
fun formatElapsed(totalSeconds: Int): String = String.format(
    Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60
)
