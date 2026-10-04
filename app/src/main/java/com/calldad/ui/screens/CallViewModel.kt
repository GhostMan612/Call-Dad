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
import com.calldad.consent.ConsentDecision
import com.calldad.consent.ConsentStore
import com.calldad.data.session.FamilyPair
import com.calldad.data.session.FamilySession
import com.calldad.history.CallLogStore
import com.calldad.history.CallOutcome
import com.calldad.history.CallRecord
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
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.withContext
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

    /**
     * Highest renegotiation round whose OFFER this device has applied (callee
     * side). Zero is the original handshake, so a live round 1 offer applies.
     */
    private var appliedOfferRound = 0

    /**
     * Highest round whose ANSWER this device has applied (caller side).
     *
     * This is the field whose absence made ICE restart a no-op: the caller
     * applied the original answer for seq N during setup, and a seq-keyed guard
     * therefore skipped every subsequent answer for that same generation. A
     * reconnect published its offer, waited for an answer, and threw it away.
     */
    private var appliedAnswerRound = 0

    /**
     * The next round to PUBLISH. Never advances on failure, so a failed attempt
     * is retried with the same round rather than racing ahead of a peer that is
     * still answering an earlier one.
     */
    private var nextNegotiationRound = 1

    /**
     * True between publishing a restart offer and receiving its answer. Held
     * across the whole round trip, not just the publish: releasing it
     * immediately let a second LOST tick publish a SECOND offer for a call whose
     * first answer had not arrived, and the two overwrote each other.
     */
    private var restartInFlight = false

    /**
     * Watchdog for a published restart whose answer never came.
     *
     * `restartInFlight` is released in exactly three places: this timeout, a
     * failed publish, and the answer arriving. Without this one it was released
     * in two, so a restart whose answer was lost — the callee's app backgrounded,
     * its listener stalled, its write rejected — held the latch for the rest of
     * the call. The KDoc promised "a handful of offers" over the grace window and
     * the code delivered exactly ONE, ever, which is indistinguishable from the
     * feature not existing.
     */
    private var restartTimeoutJob: Job? = null

    /** Wall-clock of the last restart publish, for the cooldown. */
    private var lastRestartAtMs = 0L

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

    /**
     * The call log (SPEC_SHEET §2.5, BP-05 §4). Fed from exactly ONE place —
     * [commitTerminal] — because that is the single funnel every exit path goes
     * through: local hangup, decline, no-answer, lost peer, and transport
     * failure. Recording at each call site instead is how a history ends up
     * missing the case nobody thought of, and the missed-call callback card is
     * only as good as this log's completeness.
     */
    private val callLog = CallLogStore(getApplication())

    /**
     * The CALL grant, observed live (ADR-017).
     *
     * Calling is the feature the consent model exists to protect, and it was
     * the one feature reading nothing at all: neither [startCall] nor
     * [answerCall] consulted a scope, so "Turn everything off" closed Messages
     * and Pictures and left the one thing a 6-year-old uses most wide open. The
     * scope list is named [call, text, photo] precisely so CALL is a first-class
     * grant, and `firestore.rules` cannot help here — the call room is written by
     * pair membership alone, so an unenforced client is the only enforcement
     * there is.
     */
    private val consent = ConsentStore()

    /** Null until the first decision arrives; treated as DENIED meanwhile. */
    private val _callConsent = MutableStateFlow<ConsentDecision?>(null)
    private val callConsent: StateFlow<ConsentDecision?> = _callConsent.asStateFlow()

    /**
     * Whether THIS attempt ever reached CONNECTED. Reset when a record is
     * written, and set by the same code path that starts the elapsed timer, so
     * the two can never disagree about whether a call happened.
     */
    private var everConnected = false

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
        consent.start(app, viewModelScope)
        viewModelScope.launch {
            FamilySession.pair(app).collectLatest { p ->
                onPairChanged(p)
            }
        }
        viewModelScope.launch {
            consent.decision.collect { d -> onConsentChanged(d) }
        }
    }

    // -------- consent --------

    /**
     * The CALL grant, live.
     *
     * A parent can turn calling off mid-ring, so this is observed rather than
     * checked once. Ending a call the grown-up has just revoked is the entire
     * point of a kill switch: the child must not keep talking because the
     * permission was live when the button was pressed.
     *
     * Absence DENIES. Before the first decision arrives there is no evidence the
     * call is allowed, and an allowlist app that fails open on a cold start is
     * not an allowlist app.
     */
    private fun onConsentChanged(decision: ConsentDecision?) {
        _callConsent.value = decision
        if (decision?.isGranted == true) return
        val live = _state.value
        val inCall = live is CallState.Ringing || live is CallState.Connected
        if (!inCall) return
        WebRtcLog.transition("CALL consent withdrawn; ending the call")
        // `endCall`, not a bespoke teardown: it is the one path proven to stop
        // the ringtone, dismiss the foreground service, publish ENDED so the
        // peer's phone does not go on ringing, and record the outcome. Calling
        // anything else here would leave the other phone ringing at nobody.
        endCall()
    }

    private fun callAllowed(): Boolean = _callConsent.value?.isGranted == true

    /**
     * Uses [CallErrorKind.PERMISSION_DENIED], which is deliberately NOT in
     * [CallErrorKind.isRecoverable] — so a child is not offered "Try Again" for
     * a thing that will not change until a grown-up acts. Re-prompting a kid to
     * retry a denied permission is how you train them to keep tapping.
     */
    private fun refuseCall(reason: String) {
        _state.value = CallState.Error(CallErrorKind.PERMISSION_DENIED, reason)
    }

    // -------- public API --------

    fun startCall() {
        val s = _state.value
        val canStart = s is CallState.Idle || s is CallState.NoAnswer ||
            s is CallState.Declined || s is CallState.Ended || s is CallState.Error
        if (!canStart || publishingOffer || answering) return
        // An INCOMING ring that this device has not yet observed as `Ringing`
        // must not be replaced by "calling…". The ownership fence in
        // startRingback is correct, but it was reading a precondition that
        // teardownMedia() had already destroyed three lines earlier:
        // teardownMedia -> CallAudioManager.stop() -> ringOwner = null, so the
        // fence never matched, AND the real incoming ring was cut dead while the
        // notification still read "Incoming call / Tap to answer".
        //
        // The window is real and it is the catch-up case, not steady state: a
        // locked phone suppresses the full-screen intent so nothing navigates, the
        // room listener's own first snapshot can be slow, and on API 34+ a
        // high-priority push may not be granted a full-screen intent at all. The
        // child opens the app from the launcher (no INCOMING_CALL action, so no
        // pull-in), sees the giant button, and taps it — while the service is
        // still ringing.
        //
        // So refuse BEFORE tearing anything down. An incoming ring that does
        // reach the state machine makes `canStart` false on its own.
        if (CallForegroundService.isRunning) {
            WebRtcLog.transition("Call suppressed: incoming ring in progress")
            return
        }
        val p = pair ?: run {
            _state.value = CallState.Error(
                CallErrorKind.NOT_PAIRED,
                "Not paired yet. Ask a grown-up to pair the phones."
            )
            return
        }
        // AFTER the pairing check, so an unpaired phone gets the pairing message
        // rather than a consent one, and BEFORE any offer is published.
        if (!callAllowed()) {
            refuseCall("Calling is turned off right now. Ask a grown-up to turn it on.")
            return
        }

        publishingOffer = true
        autoDismissJob?.cancel()
        teardownMedia()
        beginGeneration(asCaller = true)
        // The placeholder state names this attempt, but its seq is deliberately
        // the seq from BEFORE this generation (0 on a first-ever call). It is
        // replaced with the real one at :320, after the publish resolves.
        //
        // THAT MEANS `onCleared` CANNOT TRUST IT. It used to, and the sequence
        // went like this: the kid taps Call Dad; the publish transaction is in
        // flight; the parent swipes the app out of Recents, which cancels
        // viewModelScope and then calls onCleared; the teardown write is stamped
        // with a seq that does not exist yet, so finishCall's generation check
        // declines to write; and because `kotlinx.coroutines.tasks.await()`
        // resumes with a CancellationException WITHOUT cancelling the Task, the
        // queued transaction still commits. Net result: a room left RINGING with
        // nobody to answer it, and the child's phone ringing at full volume for
        // the full 45s no-answer timeout, with no error on either device.
        //
        // The fix is that the compensating write — "cancel the ring we just
        // started" — must not be skipped by cancellation, so it lives in a
        // `finally` under NonCancellable. It knows the real seq, because it is
        // declared INSIDE the publish. See below.
        _state.value = CallState.Ringing(currentSeq, false, peerDisplayName(), p.roomId)
        // Honoured, not discarded. The fence exists so an incoming ring keeps the
        // audio; if it refuses, this device is not the owner of an incoming ring
        // and must not make noise at all rather than starting a tone over the top
        // of one.
        if (!CallAudioManager.startRingback()) {
            WebRtcLog.transition("Call abandoned: ringback refused, audio owned elsewhere")
            return
        }
        val myAttempt = attempt

        viewModelScope.launch {
            var publishedSeq: Int? = null
            try {
                val client = newClient()
                val offer = withTimeout(SETUP_TIMEOUT_MS) { client.createOffer() }
                WebRtcLog.transition("OFFER publish started")
                val seq = withTimeout(SETUP_TIMEOUT_MS) {
                    signaling.publishOffer(p.roomId, offer.sdp, p.ownUid, p.peerUid).getOrThrow()
                }
                // Recorded the moment it is known, so the `finally` below can
                // always name the generation it created.
                publishedSeq = seq
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
                // The one write that owns the ring must survive scope
                // cancellation. `withContext(NonCancellable)` because
                // `viewModelScope` is already cancelled by the time onCleared's
                // sibling runs, and a cancelled coroutine cannot suspend — so
                // without this the compensating write is precisely the code that
                // gets skipped when it is most needed.
                val seq = publishedSeq
                val abandoned = _state.value.let {
                    !(it is CallState.Ringing && !it.isIncoming)
                } || attempt != myAttempt
                if (seq != null && abandoned && localSdpPublished.not()) {
                    withContext(NonCancellable) {
                        runCatching { signaling.finishCall(p.roomId, seq, "ENDED") }
                    }
                }
                // Docs held back while publishing (our own echo, an answer
                // that beat the transaction result, or the peer's ring in
                // glare / after a failed publish) are replayed now.
                lastDoc?.takeIf { it.seq >= currentSeq }?.let { handleDocument(it) }
            }
        }
    }

    /**
     * Try Again from NoAnswer or a recoverable Error: a brand-new call.
     *
     * Re-checks the grant rather than delegating blind. `startCall` already
     * refuses, so this is belt-and-braces for the case where the retry button is
     * reachable while consent is absent — and it keeps the reason visible rather
     * than letting a child hammer "Try Again" against a permission that will not
     * change until a grown-up acts. [CallErrorKind.PERMISSION_DENIED] is not
     * recoverable, so this screen does not normally offer it; the guard is here
     * because that coupling lives in the UI, not here, and can drift.
     */
    fun onReRing() {
        if (!callAllowed()) {
            refuseCall("Calling is turned off right now. Ask a grown-up to turn it on.")
            return
        }
        startCall()
    }

    fun answerCall() {
        val s = _state.value as? CallState.Ringing ?: return
        if (!s.isIncoming || answering) return
        val p = pair ?: return
        // Answering is a CALL action too. Gating only the outgoing path would
        // leave a parent who revoked calling unable to stop a call already
        // ringing on the child's phone.
        if (!callAllowed()) {
            // Decline through the normal path so the peer's phone stops ringing
            // and the call is logged, then explain. Publishing ENDED is the
            // important half: otherwise a revoked child's phone declines
            // silently and the parent's phone rings out for nothing.
            declineCall()
            refuseCall("Calling is turned off right now. Ask a grown-up to turn it on.")
            return
        }
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
        // Releases the CAMERA, not just the outgoing frames. `setEnabled(false)`
        // only stops sending — upstream it reaches `nativeSetEnabled` and never
        // touches the capturer — so the Camera2 session and the SurfaceTextureHelper
        // capture thread stayed live for the whole call with the app in the
        // background: the camera indicator on, the battery draining, for a
        // six-year-old who put the phone face down.
        rtc?.onUiHidden()
    }

    /** Back in front: camera returns only if the kid had it on. */
    fun onUiVisible() {
        rtc?.onUiVisible(_isCameraOn.value)
    }

    fun sendGameData(json: String): Boolean = rtc?.sendGameData(json) ?: false

    override fun onCleared() {
        val s = _state.value
        pair?.let { p ->
            if (s is CallState.Connected ||
                (s is CallState.Ringing && !s.isIncoming)
            ) {
                // Only when this device knows the generation it created.
                // `currentSeq` still holds the PREVIOUS generation's value while
                // an offer is still publishing, and stamping a teardown with it
                // means finishCall's generation check declines to write — which
                // is safe but useless, because the publish transaction is already
                // queued and WILL commit. The publish path's own `finally` owns
                // that write (under NonCancellable) precisely because it is the
                // only place the real seq exists. `localSdpPublished` is the
                // signal: it is set only once a seq is genuinely known.
                if (localSdpPublished) {
                    signaling.finishCallDetached(p.roomId, currentSeq, "ENDED")
                }
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
            // Tell the room we are leaving. This used to go straight to Idle
            // and write nothing, so the peer kept ringing to its own no-answer
            // timeout with no idea what happened, while any startCall still in
            // flight wrote a teardown to the OLD room id captured earlier.
            val s = _state.value
            val seq = currentSeq
            if (s is CallState.Connected || (s is CallState.Ringing && !s.isIncoming)) {
                pair?.let { old -> signaling.finishCallDetached(old.roomId, seq, "ENDED") }
            }
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
            // An incoming ring is a CALL action, and a revoked CALL grant must
            // refuse it HERE — at the point the phone would start ringing — not
            // only when the child taps Answer. Otherwise a parent who has turned
            // calling off still gets their own phone ringing at full volume, and
            // the fix in `answerCall` never runs because nobody pressed anything.
            //
            // The room document is marked ENDED rather than ignored: the peer is
            // mid-publish, and leaving it RINGING means their phone rings out for
            // a call this side silently refused.
            if (!callAllowed()) {
                WebRtcLog.transition("Incoming ring refused: no CALL grant")
                currentSeq = doc.seq
                viewModelScope.launch { signaling.finishCall(p.roomId, doc.seq, "ENDED") }
                return
            }
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
                } else {
                    // Same-generation renegotiation. The terminal checks above
                    // still win, so a reconnect arriving alongside a hangup can
                    // never resurrect the call.
                    //
                    // BOTH SIDES need a branch here. The callee answers the
                    // caller's restart offer; the caller must APPLY that answer.
                    // Previously this method returned early for the caller, which
                    // meant the restart was published and then ignored — ICE
                    // re-gathered on the caller's own new offer but the peer
                    // connection never got the matching answer, so the feature
                    // was a no-op that cost a round trip to prove it.
                    if (amCaller) {
                        applyRenegotiationAnswer(doc)
                    } else {
                        maybeApplyRenegotiation(doc)
                    }
                }
            else -> Unit
        }
    }

    /**
     * Applies a peer's ICE-restart OFFER while the call is already up. Never
     * publishes anything itself — only the side that noticed the dead
     * connection offers, so both sides cannot renegotiate simultaneously and
     * overwrite each other (which would leave the peer waiting on an SDP that
     * has already been replaced).
     *
     * Idempotent per [negotiationRound], and the round is reset on rejection so
     * a retry of the SAME round is allowed. Resetting a `Boolean` "already did
     * this" flag instead is what made repeated restarts impossible even once the
     * caller side was fixed.
     */
    private fun maybeApplyRenegotiation(doc: CallDocument) {
        val client = rtc ?: return
        if (!doc.renegotiating) return
        if (doc.negotiationRound <= appliedOfferRound) return
        val offer = doc.offer ?: return
        val s = _state.value
        if (s !is CallState.Connected || s.seq != doc.seq) return

        appliedOfferRound = doc.negotiationRound
        viewModelScope.launch {
            if (!client.setRemoteDescription(offer)) {
                WebRtcLog.transition("Renegotiation: remote offer rejected")
                // Un-apply so a later snapshot (or a fresh restart) can retry.
                if (appliedOfferRound == doc.negotiationRound) {
                    appliedOfferRound = doc.negotiationRound - 1
                }
                return@launch
            }
            lastDoc?.takeIf { it.seq == doc.seq }?.let { feedRemoteCandidates(it) }
            if (rtc !== client) return@launch
            runCatching {
                val answer = withTimeout(SETUP_TIMEOUT_MS) { client.createAnswer() }
                pair?.let { p ->
                    signaling.publishRenegotiationAnswer(
                        p.roomId, doc.seq, answer.sdp, doc.negotiationRound
                    )
                }
            }.onFailure {
                WebRtcLog.transition("Renegotiation: answer failed")
            }
        }
    }

    /**
     * The caller's half of the restart: apply the answer to its own restart
     * offer.
     *
     * Guarded on [appliedAnswerRound] rather than on `seq`, because the call
     * already applied the ORIGINAL answer for this very same seq during
     * setup. A seq-guarded apply is therefore a permanent skip, and the restart
     * can never complete no matter how many times it is offered.
     */
    private fun applyRenegotiationAnswer(doc: CallDocument) {
        val client = rtc ?: return
        val answer = doc.answer ?: return
        // A document that still says `renegotiating` is one where our own offer is
        // outstanding. It may still carry an answer from BEFORE the restart (a
        // cache snapshot, or a peer that has not answered yet), and applying that
        // would re-assert the pre-restart ufrag — the exact failure the restart
        // exists to escape. Belt-and-braces with the `answer` delete in
        // `publishRenegotiation`, because either alone leaves a window.
        if (doc.renegotiating) return
        if (doc.negotiationRound <= appliedAnswerRound) return
        val s = _state.value
        if (s !is CallState.Connected || s.seq != doc.seq) return

        appliedAnswerRound = doc.negotiationRound
        // The restart completed: release the in-flight latch so a LATER episode
        // can start a new round, and cancel the watchdog that would otherwise
        // release it again harmlessly.
        restartTimeoutJob?.cancel()
        restartTimeoutJob = null
        restartInFlight = false
        WebRtcLog.transition("ICE restart answered (round ${doc.negotiationRound})")
        viewModelScope.launch {
            if (!client.setRemoteDescription(answer)) {
                WebRtcLog.transition("ICE restart: answer rejected")
                if (appliedAnswerRound == doc.negotiationRound) {
                    appliedAnswerRound = doc.negotiationRound - 1
                }
            }
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
        val myAttempt = attempt
        val myClient = rtcFlow.value
        viewModelScope.launch {
            // Generation guard. teardownMedia() bumps `attempt`; without this
            // check a candidate gathered for generation n is arrayUnion-ed
            // into generation n+1's freshly-reset arrays AFTER the peer's
            // connection is already built, and libwebrtc rejects the mismatched
            // ufrag with no visible cause.
            if (attempt != myAttempt || rtcFlow.value !== myClient) return@launch
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
        appliedOfferRound = 0
        appliedAnswerRound = 0
        nextNegotiationRound = 1
        restartInFlight = false
        // `everConnected` resets HERE, at the new-attempt boundary, not only in
        // `recordCallOutcome`. Two exit paths (a listener `fail()`, and a pairing
        // change) write a terminal state without going through that funnel, so a
        // reset confined to the funnel let a call that DID connect leave the flag
        // true, and the next call — which rang out — was logged as ANSWERED with
        // zero duration. The child's history then claimed a call that never
        // happened and suppressed the callback card for the miss that did.
        everConnected = false
        restartTimeoutJob?.cancel()
        restartTimeoutJob = null
        lastRestartAtMs = 0L
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
        recordCallOutcome(next)
        _state.value = next
        autoDismissJob?.cancel()
        autoDismissJob = viewModelScope.launch {
            delay(AUTO_DISMISS_MS)
            if (_state.value == next) _state.value = CallState.Idle
        }
    }

    /**
     * Appends one row to the call log for a call that just reached a terminal
     * state. Fire-and-forget: a logging failure must never be able to interrupt a
     * hangup, which is the one interaction that must always work.
     *
     * "Did it CONNECT" is the honest signal, not [EndReason]: a REMOTE_HANGUP
     * after a good call is a perfectly normal answered call, and classifying it
     * as a failure would put a "Dad didn't answer" card in front of a child who
     * just had a lovely chat. `everConnected` is the only fact that separates
     * "it worked" from "it didn't", so it is what this keys on.
     */
    private fun recordCallOutcome(terminal: CallState) {
        val outcome = when {
            everConnected -> CallOutcome.ANSWERED
            terminal is CallState.Declined -> CallOutcome.DECLINED
            terminal is CallState.NoAnswer -> CallOutcome.MISSED
            else -> CallOutcome.FAILED
        }
        val wasOutgoing = amCaller
        val durationMs = _elapsedSeconds.value.toLong() * 1_000L
        val record = CallRecord(
            id = "call-$currentSeq-${System.currentTimeMillis()}",
            startedAtMs = System.currentTimeMillis() - durationMs,
            durationMs = durationMs,
            outcome = outcome,
            wasOutgoing = wasOutgoing
        )
        everConnected = false
        viewModelScope.launch { callLog.record(record) }
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
                // Through the FUNNEL, not a direct state write. This line used
                // `_state.value = NoAnswer(seq)`, which is the single most
                // important exit path in the app -- the child tapped the giant
                // button and nobody answered -- and it was the one path that never
                // reached the call log. So `CallOutcome.MISSED` was unreachable
                // dead code and the missed-call callback card could never appear,
                // which is the entire reason `CallLog` exists.
                commitTerminal(CallState.NoAnswer(seq))
            }
        }
    }

    private fun startElapsedTimer() {
        elapsedJob?.cancel()
        _elapsedSeconds.value = 0
        // Same line that zeroes the duration, so "this call connected" and
        // "this call had a duration" can never disagree. The call log reads
        // `everConnected` to tell a good call from a lost one, and this is the
        // only place that knows.
        everConnected = true
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
                // Offer the restart BEFORE deciding to give up, and while the grace
                // window is still open. Attempting it after the give-up branch
                // would be dead code: the call is already ended by then.
                if (lostSince != 0L) attemptIceRestart()
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

    /**
     * Publishes an ICE-restart OFFER once per LOST episode, caller side only.
     *
     * LOST is reached either because the network changed under us (wifi dropped,
     * NAT rebound, the TURN relay went away) or because the phone's radio is
     * mid-flap. Both are recoverable, and both are invisible to ICE as a
     * permanent failure: without a restart the peer connection keeps trying the
     * candidate set that just died, so the call would ride out the full
     * [LOST_GRACE_MS] and then end, even though the network came back two
     * seconds later. This is the difference between a kid calling across town
     * and the call dying because dad stepped outside for a moment.
     *
     * Why caller-only: both sides restarting at once would each overwrite the
     * other's SDP and leave one waiting on an offer that has already been
     * replaced. The peer answers via [maybeApplyRenegotiation]. The callee
     * cannot restart the call it is in, so on a lossy link the callee waits for
     * the caller to restart -- which is correct, because the caller's view of
     * the path is the one that failed.
     *
     * [LOST_RESTART_COOLDOWN_MS] bounds the cost: one restart attempt per
     * cooldown, so a phone in a dead zone publishes a handful of offers rather
     * than one per second for the whole grace window.
     */
    private fun attemptIceRestart() {
        val s = _state.value
        if (s !is CallState.Connected || !amCaller) return
        if (restartInFlight) return
        val now = System.currentTimeMillis()
        if (now - lastRestartAtMs < LOST_RESTART_COOLDOWN_MS) return
        val client = rtc ?: return
        val p = pair ?: return
        val seq = s.seq
        val round = nextNegotiationRound

        restartInFlight = true
        lastRestartAtMs = now
        WebRtcLog.transition("LOST: publishing ICE restart (round $round)")
        viewModelScope.launch {
            runCatching {
                val offer = withTimeout(SETUP_TIMEOUT_MS) { client.createOffer(iceRestart = true) }
                signaling.publishRenegotiation(p.roomId, seq, offer.sdp, round).getOrThrow()
            }.onSuccess {
                // Only advance the round on a CONFIRMED publish. A failed write
                // must be retryable with the same round, or the peer's answer to
                // round N would arrive and be compared against N+1 forever.
                nextNegotiationRound = round + 1
                lastDoc?.takeIf { it.seq == seq }?.let { feedRemoteCandidates(it) }
                // The answer may never come. Hold the latch only until it has had
                // a fair chance, then let the next cooldown tick try again.
                restartTimeoutJob?.cancel()
                restartTimeoutJob = viewModelScope.launch {
                    delay(RESTART_ANSWER_TIMEOUT_MS)
                    // Guarded on the round: if the answer landed and a NEWER
                    // round is in flight, this must not stomp on it.
                    if (restartInFlight && appliedAnswerRound < round) {
                        WebRtcLog.transition("ICE restart unanswered; releasing latch")
                        restartInFlight = false
                    }
                }
            }.onFailure {
                WebRtcLog.transition("ICE restart failed")
                // Release the latch so the next cooldown tick can retry.
                restartInFlight = false
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
            // Never surface a raw throwable message. A java.lang.* or
            // Firestore string on a 6-year-old's Error card is a UX failure
            // and a small information leak; the guardrail covers the logs,
            // not the screen.
            else -> CallErrorKind.UNKNOWN to "Something went wrong. Tap to try again."
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
        // Through the FUNNEL. A transport failure is the case where a parent most
        // needs to know the call did not happen, and this path wrote the state
        // directly, so `CallOutcome.FAILED` was unreachable and a failed call left
        // no row at all. `canTransition` already permits RINGING/Connected -> Error;
        // if it ever stops, `commitTerminal` falls back to Idle and the child is
        // not left staring at a dead Error card.
        commitTerminal(CallState.Error(kind = kind, message = message, seq = seq))
    }

    /**
     * The name of the OTHER person on the other end, from this device's point of
     * view.
     *
     * The mapping is uniform across every call site in the app: a parent device
     * (`APP_THEME == "blue"`) shows the CHILD's name, a child device shows the
     * GROWN-UP's name. It used to be inverted here relative to ChatScreen and the
     * PTT banner, so the outgoing call screen and the message thread named the
     * same person two different ways.
     */
    private fun peerDisplayName(): String =
        app.getString(
            if (BuildConfig.APP_THEME == "blue") R.string.name_of_child
            else R.string.name_of_grown_up
        )

    /**
     * The name of the CALLER, shown on the incoming overlay.
     *
     * Same uniform mapping as [peerDisplayName]: the person being named is
     * whichever human is on the other end, not whichever flavor this build is.
     */
    private fun callerDisplayName(): String = peerDisplayName()

    private companion object {
        const val SETUP_TIMEOUT_MS = 10_000L
        const val AUTO_DISMISS_MS = 2_000L
        const val LOST_GRACE_MS = 20_000L

        /**
         * Minimum gap between ICE-restart attempts. One per 5s over a 20s grace
         * window is 4 attempts, enough to survive a radio flap or a NAT rebind
         * without turning a dead zone into a publish loop.
         */
        const val LOST_RESTART_COOLDOWN_MS = 5_000L

        /**
         * How long a published restart waits for its answer before the in-flight
         * latch is released so the next cooldown tick may try again.
         *
         * Comfortably longer than a Firestore round trip on mobile data, and
         * comfortably shorter than [LOST_GRACE_MS] so a lost answer still leaves
         * time for a real retry inside the same grace window. Four restarts fit
         * in 20s, which is the "a handful, not one" the KDoc promises.
         */
        const val RESTART_ANSWER_TIMEOUT_MS = 8_000L
        const val FIRST_MEDIA_GRACE_MS = 25_000L
        const val INCOMING_RING_MS = 60_000L
    }
}

/** Formats elapsed seconds as mm:ss for the CallScreen timer. */
fun formatElapsed(totalSeconds: Int): String = String.format(
    Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60
)
