// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// webrtc/WebRTCClient.kt
// Location: app/src/main/java/com/calldad/webrtc/WebRTCClient.kt
package com.calldad.webrtc

import android.content.Context
import com.calldad.data.signaling.IceCandidate as DomainIceCandidate
import com.calldad.data.signaling.SdpType
import com.calldad.data.signaling.SessionDescription as DomainSessionDescription
import kotlinx.coroutines.CompletableDeferred
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

class WebRTCClient(
    context: Context,
    private val iceServers: List<IceServerConfig> = WebRtcConfig.iceServers,
    private val onLocalIceCandidate: (DomainIceCandidate) -> Unit,
    private val onRemoteVideoTrack: (VideoTrack) -> Unit,
    private val onConnectionStateChanged: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val eglBase: EglBase = EglBase.create()

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    private var micEnabled = true
    private var cameraEnabled = true

    /**
     * The EGL context the renderer MUST init with. Passed into
     * VideoRenderer(eglContext = ...). Do not create a second EglBase.
     */
    val eglContext: EglBase.Context
        get() = eglBase.eglBaseContext

    /** Local camera track. Non-null after createPeerConnection(). */
    val localVideoTrack: VideoTrack?
        get() = videoTrack

    // -------- lifecycle --------

    fun initialize() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val encoder = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()
        WebRtcLog.transition("PeerConnectionFactory initialized")
    }

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

    fun createPeerConnection() {
        if (peerConnection != null) return
        val f = factory ?: error("initialize() must be called first")
        val rtcConfig = buildRtcConfig()
        peerConnection = f.createPeerConnection(rtcConfig, observer)
            ?: error("createPeerConnection returned null")
        attachLocalTracks()
        WebRtcLog.transition("PeerConnection created")
    }

    private fun attachLocalTracks() {
        val f = factory ?: return
        val pc = peerConnection ?: return

        audioSource = f.createAudioSource(MediaConstraints())
        audioTrack = f.createAudioTrack(WebRtcConfig.AUDIO_TRACK_ID, audioSource).also {
            pc.addTrack(it, listOf(WebRtcConfig.LOCAL_STREAM_ID))
        }

        val capturer = createCameraCapturer() ?: run {
            WebRtcLog.transition("No camera available — audio-only mode")
            return
        }
        videoCapturer = capturer
        surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        videoSource = f.createVideoSource(capturer.isScreencast)
        capturer.initialize(surfaceHelper, appContext, videoSource!!.capturerObserver)
        videoTrack = f.createVideoTrack(WebRtcConfig.VIDEO_TRACK_ID, videoSource).also {
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

    fun setRemoteDescription(remote: DomainSessionDescription) {
        val pc = peerConnection ?: return
        val type = when (remote.type) {
            SdpType.OFFER -> RtcSessionDescription.Type.OFFER
            SdpType.ANSWER -> RtcSessionDescription.Type.ANSWER
        }
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() { WebRtcLog.transition("Remote description applied") }
            override fun onSetFailure(err: String?) { WebRtcLog.transition("Remote description failed") }
            override fun onCreateSuccess(p0: RtcSessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, RtcSessionDescription(type, remote.sdp))
    }

    fun addRemoteIceCandidate(candidate: DomainIceCandidate) {
        peerConnection?.addIceCandidate(
            RtcIceCandidate(
                candidate.sdpMid,
                candidate.sdpMLineIndex ?: 0,
                candidate.sdpCandidate
            )
        )
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

    // -------- media controls --------

    fun startCapture() {
        videoCapturer?.startCapture(
            WebRtcConfig.VIDEO_WIDTH,
            WebRtcConfig.VIDEO_HEIGHT,
            WebRtcConfig.VIDEO_FPS
        )
        WebRtcLog.transition("Camera capture started")
    }

    fun stopCapture() {
        videoCapturer?.stopCapture()
        WebRtcLog.transition("Camera capture stopped")
    }

    fun toggleCamera() {
        cameraEnabled = !cameraEnabled
        videoTrack?.setEnabled(cameraEnabled)
        WebRtcLog.transition(if (cameraEnabled) "Camera enabled" else "Camera disabled")
    }

    fun toggleMic() {
        micEnabled = !micEnabled
        audioTrack?.setEnabled(micEnabled)
        WebRtcLog.transition(if (micEnabled) "Mic enabled" else "Mic muted")
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
        WebRtcLog.transition("Camera switched")
    }

    // -------- teardown --------

    private var disposed = false

    /**
     * Idempotent: endCall() and onCleared() both call this (hangup pops the
     * nav destination, clearing the VM). A second eglBase.release() throws —
     * that was the post-hangup crash (executor fix, device-proven).
     */
    fun dispose() {
        if (disposed) return
        disposed = true
        runCatching { videoCapturer?.stopCapture() }
        videoCapturer?.dispose(); videoCapturer = null
        surfaceHelper?.dispose(); surfaceHelper = null
        videoSource?.dispose(); videoSource = null
        videoTrack?.dispose(); videoTrack = null
        audioSource?.dispose(); audioSource = null
        audioTrack?.dispose(); audioTrack = null
        peerConnection?.close(); peerConnection = null
        factory?.dispose(); factory = null
        eglBase.release()
        WebRtcLog.transition("WebRTCClient disposed")
    }

    // -------- observer --------

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
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            WebRtcLog.transition("ICE gathering state: $state")
        }

        override fun onAddStream(stream: MediaStream?) {
            stream?.videoTracks?.firstOrNull()?.let { handleRemoteTrack(it) }
        }

        override fun onRemoveStream(stream: MediaStream?) {}

        override fun onDataChannel(channel: DataChannel?) {}

        override fun onRenegotiationNeeded() {
            WebRtcLog.transition("Renegotiation needed")
        }

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            (receiver?.track() as? VideoTrack)?.let { handleRemoteTrack(it) }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            (transceiver?.receiver?.track() as? VideoTrack)?.let { handleRemoteTrack(it) }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            WebRtcLog.transition("Peer connection state: $newState")
            onConnectionStateChanged(newState?.name ?: "UNKNOWN")
        }
    }

    private var remoteTrackDelivered = false
    private fun handleRemoteTrack(track: VideoTrack) {
        if (remoteTrackDelivered) return
        remoteTrackDelivered = true
        onRemoteVideoTrack(track)
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
