// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// webrtc/WebRTCClient.kt — one instance per call attempt (ADR-015)
// Location: app/src/main/java/com/calldad/webrtc/WebRTCClient.kt
package com.calldad.webrtc

import android.content.Context
import android.media.AudioManager
import com.calldad.data.signaling.IceCandidate as DomainIceCandidate
import com.calldad.data.signaling.SdpType
import com.calldad.data.signaling.SessionDescription as DomainSessionDescription
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate as RtcIceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription as RtcSessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer

/**
 * One call attempt's media stack: factory, peer connection, local tracks,
 * camera, game data channel.
 *
 * SINGLE USE: construct → [start] → offer/answer → [dispose]. A retry or
 * a new generation gets a NEW instance; nothing here is ever re-armed
 * after dispose (the old reuse path built on released native objects).
 *
 * The [eglBase] is owned by the caller (CallViewModel) and outlives every
 * instance, so renderers initialised with it never see a released context.
 */
class WebRTCClient(
    context: Context,
    private val eglBase: EglBase,
    private val onLocalIceCandidate: (DomainIceCandidate) -> Unit,
    private val onRemoteVideoTrack: (VideoTrack) -> Unit,
    private val iceServers: List<IceServerConfig> = WebRtcConfig.iceServers
) {
    private val appContext = context.applicationContext
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null
    private var gameChannel: DataChannel? = null

    private var capturing = false
    private var disposed = false

    private var savedAudioMode: Int? = null
    private var savedSpeakerOn: Boolean? = null

    private val remoteLock = Any()
    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = mutableListOf<RtcIceCandidate>()

    private val _iceEverConnected = MutableStateFlow(false)
    /** True once ICE reached CONNECTED/COMPLETED at least once. */
    val iceEverConnected: StateFlow<Boolean> = _iceEverConnected.asStateFlow()

    private val channelLock = Any()

    private val _connectionHealth = MutableStateFlow(ConnectionHealth.HEALTHY)
    val connectionHealth: StateFlow<ConnectionHealth> =
        _connectionHealth.asStateFlow()

    private var disconnectionDebounceJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _gameMessages = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Inbound game-sync messages, copied out of the native buffer. */
    val gameSyncMessages: Flow<String> = _gameMessages.asSharedFlow()

    /** Local camera track. Non-null after [start] on a device with a camera. */
    val localVideoTrack: VideoTrack?
        get() = videoTrack

    // -------- lifecycle --------

    /**
     * Builds factory, peer connection and local media, then starts the
     * camera. Switches audio into call mode (speakerphone on: a
     * 6-year-old never holds the phone to her ear); [dispose] restores
     * whatever mode and route were active before.
     */
    fun start(cameraEnabled: Boolean) {
        check(!disposed) { "WebRTCClient is single-use" }
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val f = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        factory = f

        val pc = f.createPeerConnection(buildRtcConfig(), observer)
            ?: error("createPeerConnection returned null")
        peerConnection = pc

        enterCallAudioMode()
        createGameDataChannel(pc)
        attachLocalTracks(f, pc)
        videoTrack?.setEnabled(cameraEnabled)
        startCapture()
        WebRtcLog.transition("WebRTCClient started")
    }

    /**
     * Idempotent teardown. Order matters (the hangup SIGSEGV):
     *  1. peer connection dispose — disposes its transceivers, and with them
     *     the Java wrapper of the remote track, detaching every renderer
     *     sink cleanly. A later removeSink on that track is a no-op.
     *  2. local tracks/sources/capturer, each via its Java dispose().
     *  3. factory last.
     * The shared EglBase is NOT released here.
     */
    fun dispose() {
        if (disposed) return
        disposed = true
        disconnectionDebounceJob?.cancel()
        scope.cancel()
        stopCapture()

        synchronized(channelLock) {
            gameChannel?.let { ch ->
                runCatching { ch.unregisterObserver() }
                runCatching { ch.close() }
                runCatching { ch.dispose() }
            }
            gameChannel = null
        }

        peerConnection?.let { pc ->
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }
        peerConnection = null

        runCatching { videoTrack?.dispose() }; videoTrack = null
        runCatching { audioTrack?.dispose() }; audioTrack = null
        runCatching { videoSource?.dispose() }; videoSource = null
        runCatching { audioSource?.dispose() }; audioSource = null
        runCatching { videoCapturer?.dispose() }; videoCapturer = null
        runCatching { surfaceHelper?.dispose() }; surfaceHelper = null
        runCatching { factory?.dispose() }; factory = null

        restoreAudioMode()
        WebRtcLog.transition("WebRTCClient disposed")
    }

    // -------- signaling operations --------

    suspend fun createOffer(): DomainSessionDescription {
        val sdp = setLocalAndAwait(RtcSessionDescription.Type.OFFER)
        WebRtcLog.transition("Local OFFER created")
        return sdp
    }

    suspend fun createAnswer(): DomainSessionDescription {
        val sdp = setLocalAndAwait(RtcSessionDescription.Type.ANSWER)
        WebRtcLog.transition("Local ANSWER created")
        return sdp
    }

    /**
     * Applies the peer's SDP and, on success, drains candidates that
     * arrived before it (a candidate added before the remote description
     * is rejected by libwebrtc and would be lost for good).
     */
    suspend fun setRemoteDescription(remote: DomainSessionDescription): Boolean {
        val pc = peerConnection ?: return false
        val type = when (remote.type) {
            SdpType.OFFER -> RtcSessionDescription.Type.OFFER
            SdpType.ANSWER -> RtcSessionDescription.Type.ANSWER
        }
        val done = CompletableDeferred<Boolean>()
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { done.complete(true) }
            override fun onSetFailure(err: String?) { done.complete(false) }
            override fun onCreateSuccess(p0: RtcSessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, RtcSessionDescription(type, remote.sdp))
        val ok = done.await()
        if (!ok) {
            WebRtcLog.transition("Remote description failed")
            return false
        }
        WebRtcLog.transition("Remote description applied")
        val drained = synchronized(remoteLock) {
            remoteDescriptionSet = true
            pendingRemoteCandidates.toList().also { pendingRemoteCandidates.clear() }
        }
        drained.forEach { runCatching { peerConnection?.addIceCandidate(it) } }
        return true
    }

    fun addRemoteIceCandidate(candidate: DomainIceCandidate) {
        if (disposed) return
        val rtc = RtcIceCandidate(
            candidate.sdpMid,
            candidate.sdpMLineIndex ?: 0,
            candidate.sdpCandidate
        )
        val applyNow = synchronized(remoteLock) {
            if (!remoteDescriptionSet) pendingRemoteCandidates.add(rtc)
            remoteDescriptionSet
        }
        if (applyNow) runCatching { peerConnection?.addIceCandidate(rtc) }
    }

    // -------- media controls --------

    fun setCameraEnabled(enabled: Boolean) {
        videoTrack?.setEnabled(enabled)
        WebRtcLog.transition(if (enabled) "Camera enabled" else "Camera disabled")
    }

    fun setMicEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
        WebRtcLog.transition(if (enabled) "Mic enabled" else "Mic muted")
    }

    fun switchCamera() {
        runCatching { videoCapturer?.switchCamera(null) }
        WebRtcLog.transition("Camera switched")
    }

    fun startCapture() {
        if (capturing || disposed) return
        val capturer = videoCapturer ?: return
        runCatching {
            capturer.startCapture(
                WebRtcConfig.VIDEO_WIDTH,
                WebRtcConfig.VIDEO_HEIGHT,
                WebRtcConfig.VIDEO_FPS
            )
            capturing = true
            WebRtcLog.transition("Camera capture started")
        }
    }

    fun stopCapture() {
        if (!capturing) return
        runCatching { videoCapturer?.stopCapture() }
        capturing = false
        WebRtcLog.transition("Camera capture stopped")
    }

    /**
     * Sends a JSON string over "game_sync". False if not open or over 1 KB.
     * Called from the WebView's JS-bridge thread: locked against dispose().
     */
    fun sendGameData(json: String): Boolean {
        val bytes = json.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_GAME_MESSAGE_BYTES) {
            WebRtcLog.transition("Game message rejected: exceeds 1 KB cap")
            return false
        }
        synchronized(channelLock) {
            if (disposed) return false
            val channel = gameChannel ?: return false
            if (channel.state() != DataChannel.State.OPEN) return false
            return channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
        }
    }

    // -------- internals --------

    private fun buildRtcConfig(): PeerConnection.RTCConfiguration {
        val rtcIceServers = iceServers.map { cfg ->
            PeerConnection.IceServer.builder(cfg.url).apply {
                cfg.username?.let { setUsername(it) }
                cfg.credential?.let { setPassword(it) }
            }.createIceServer()
        }
        return PeerConnection.RTCConfiguration(rtcIceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        }
    }

    @Suppress("DEPRECATION")
    private fun enterCallAudioMode() {
        savedAudioMode = audioManager.mode
        savedSpeakerOn = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
    }

    @Suppress("DEPRECATION")
    private fun restoreAudioMode() {
        val mode = savedAudioMode ?: return
        runCatching {
            audioManager.mode =
                if (mode == AudioManager.MODE_IN_COMMUNICATION) AudioManager.MODE_NORMAL else mode
            audioManager.isSpeakerphoneOn = savedSpeakerOn ?: false
        }
        savedAudioMode = null
        savedSpeakerOn = null
    }

    /**
     * Pre-negotiated channel (same id on both sides). Each side creating an
     * in-band channel and closing the "duplicate" closed BOTH ends in most
     * timings; a negotiated channel is one channel, no onDataChannel dance.
     */
    private fun createGameDataChannel(pc: PeerConnection) {
        if (gameChannel != null) return
        val init = DataChannel.Init().apply {
            ordered = true
            maxRetransmits = -1
            maxRetransmitTimeMs = -1
            protocol = ""
            negotiated = true
            id = GAME_CHANNEL_ID
        }
        gameChannel = pc.createDataChannel(GAME_CHANNEL_LABEL, init)?.also {
            it.registerObserver(gameChannelObserver)
        }
    }

    private fun attachLocalTracks(f: PeerConnectionFactory, pc: PeerConnection) {
        audioSource = f.createAudioSource(MediaConstraints())
        audioTrack = f.createAudioTrack(WebRtcConfig.AUDIO_TRACK_ID, audioSource).also {
            pc.addTrack(it, listOf(WebRtcConfig.LOCAL_STREAM_ID))
        }

        val capturer = createCameraCapturer() ?: run {
            WebRtcLog.transition("No camera available — audio-only mode")
            return
        }
        videoCapturer = capturer
        val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        surfaceHelper = helper
        val source = f.createVideoSource(capturer.isScreencast)
        videoSource = source
        capturer.initialize(helper, appContext, source.capturerObserver)
        videoTrack = f.createVideoTrack(WebRtcConfig.VIDEO_TRACK_ID, source).also {
            pc.addTrack(it, listOf(WebRtcConfig.LOCAL_STREAM_ID))
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? = try {
        val enumerator = Camera2Enumerator(appContext)
        val names = enumerator.deviceNames
        if (names.isEmpty()) null
        else {
            val front = names.firstOrNull { enumerator.isFrontFacing(it) }
            enumerator.createCapturer(front ?: names.first(), null)
        }
    } catch (t: Throwable) {
        WebRtcLog.transition("Camera capturer creation failed")
        null
    }

    private suspend fun setLocalAndAwait(
        type: RtcSessionDescription.Type
    ): DomainSessionDescription {
        val pc = peerConnection ?: error("No PeerConnection")
        val deferred = CompletableDeferred<DomainSessionDescription>()
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        val createObserver = object : SdpObserver {
            override fun onCreateSuccess(created: RtcSessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        val local = pc.localDescription ?: created
                        deferred.complete(local.toDomain(type))
                    }
                    override fun onSetFailure(err: String?) {
                        deferred.completeExceptionally(
                            IllegalStateException("setLocalDescription failed")
                        )
                    }
                    override fun onCreateSuccess(p0: RtcSessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, created)
            }
            override fun onCreateFailure(err: String?) {
                deferred.completeExceptionally(IllegalStateException("createSdp failed"))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }
        when (type) {
            RtcSessionDescription.Type.OFFER -> pc.createOffer(createObserver, constraints)
            RtcSessionDescription.Type.ANSWER -> pc.createAnswer(createObserver, constraints)
            else -> deferred.completeExceptionally(
                IllegalArgumentException("Unsupported SDP type")
            )
        }
        return deferred.await()
    }

    private val gameChannelObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) {}

        override fun onStateChange() {
            WebRtcLog.transition("DataChannel state: ${gameChannel?.state()}")
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            // Copy before returning: the native buffer is freed afterwards.
            val remaining = buffer.data.remaining()
            if (remaining > MAX_GAME_MESSAGE_BYTES) return
            val bytes = ByteArray(remaining)
            buffer.data.get(bytes)
            _gameMessages.tryEmit(String(bytes, Charsets.UTF_8))
        }
    }

    private var remoteTrackDelivered = false

    private fun handleRemoteTrack(track: VideoTrack) {
        if (remoteTrackDelivered || disposed) return
        remoteTrackDelivered = true
        scope.launch { if (!disposed) onRemoteVideoTrack(track) }
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: RtcIceCandidate?) {
            candidate ?: return
            onLocalIceCandidate(
                DomainIceCandidate(
                    sdpCandidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
            )
        }

        override fun onIceCandidatesRemoved(candidates: Array<out RtcIceCandidate>?) {}

        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            WebRtcLog.transition("Signaling state: $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            WebRtcLog.transition("ICE connection state: $state")
            scope.launch { onIceState(state) }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            WebRtcLog.transition("ICE gathering state: $state")
        }

        override fun onAddStream(stream: MediaStream?) {
            stream?.videoTracks?.firstOrNull()?.let { handleRemoteTrack(it) }
        }

        override fun onRemoveStream(stream: MediaStream?) {}

        override fun onDataChannel(channel: DataChannel?) {
            WebRtcLog.transition("Unexpected in-band DataChannel ignored")
        }

        override fun onRenegotiationNeeded() {}

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            (receiver?.track() as? VideoTrack)?.let { handleRemoteTrack(it) }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            (transceiver?.receiver?.track() as? VideoTrack)?.let { handleRemoteTrack(it) }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            WebRtcLog.transition("Peer connection state: $newState")
        }
    }

    private fun onIceState(state: PeerConnection.IceConnectionState?) {
        if (disposed) return
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                disconnectionDebounceJob?.cancel()
                disconnectionDebounceJob = null
                _iceEverConnected.value = true
                _connectionHealth.value = ConnectionHealth.HEALTHY
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                if (disconnectionDebounceJob?.isActive == true) return
                _connectionHealth.value = ConnectionHealth.DEGRADED
                disconnectionDebounceJob = scope.launch {
                    delay(DISCONNECTED_DEBOUNCE_MS)
                    if (_connectionHealth.value == ConnectionHealth.DEGRADED) {
                        _connectionHealth.value = ConnectionHealth.LOST
                        WebRtcLog.transition("ICE debounce expired: LOST")
                    }
                }
            }
            PeerConnection.IceConnectionState.FAILED -> {
                disconnectionDebounceJob?.cancel()
                _connectionHealth.value = ConnectionHealth.LOST
            }
            else -> Unit
        }
    }

    private companion object {
        const val GAME_CHANNEL_LABEL = "game_sync"
        const val GAME_CHANNEL_ID = 0
        const val MAX_GAME_MESSAGE_BYTES = 1024
        const val DISCONNECTED_DEBOUNCE_MS = 3_000L
    }
}

private fun RtcSessionDescription.toDomain(
    type: RtcSessionDescription.Type
): DomainSessionDescription {
    val domainType = when (type) {
        RtcSessionDescription.Type.OFFER -> SdpType.OFFER
        else -> SdpType.ANSWER
    }
    return DomainSessionDescription(domainType, description)
}
