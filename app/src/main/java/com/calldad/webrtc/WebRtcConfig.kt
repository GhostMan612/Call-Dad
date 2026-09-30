// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// webrtc/WebRtcConfig.kt — Phase 4: structured ICE config with TURN sentinel
// Location: app/src/main/java/com/calldad/webrtc/WebRtcConfig.kt
package com.calldad.webrtc

import com.calldad.BuildConfig

data class IceServerConfig(
    val url: String,
    val username: String? = null,
    val credential: String? = null
)

object WebRtcConfig {
    const val LOCAL_STREAM_ID = "calldad_stream"
    const val AUDIO_TRACK_ID = "calldad_audio"
    const val VIDEO_TRACK_ID = "calldad_video"

    const val VIDEO_WIDTH = 640
    const val VIDEO_HEIGHT = 480
    const val VIDEO_FPS = 24

    /**
     * ICE servers.
     *
     * STUN resolves addresses but cannot carry media through a NAT. Without a
     * TURN relay, a call works on the same Wi-Fi and silently fails the moment
     * either phone is on mobile data or a symmetric NAT -- which is to say, a
     * call from a car or a cafe. That failure looks like a network problem to
     * the operator and like "Dad isn't answering" to the child.
     *
     * Open Relay is the default (K8, operator decision 2026-09-30): a public
     * TURN service run for open-source video, credentials already published, no
     * account and no billing. The tradeoff is deliberate and must stay recorded:
     * a third party sits in the media path, sees the ciphertext, and its
     * availability is not ours. For a two-person family app that is an
     * acceptable trade; it is NOT acceptable for anything involving real child
     * media over an untrusted network, and the credential is long-lived and
     * extractable from the APK.
     *
     * Overriding TURN_URLS / TURN_USER / TURN_PASS in local.properties
     * (gitignored) replaces all of this with a private relay. That is the path
     * to a real deployment.
     *
     * Credentials reach the app via local.properties -> BuildConfig and are
     * never committed. DO NOT log these values anywhere, ever (guardrail §G) --
     * they are the keys to the media path.
     */
    val iceServers: List<IceServerConfig>
        get() = buildList {
            add(IceServerConfig(url = "stun:stun.l.google.com:19302"))
            add(IceServerConfig(url = "stun:stun1.l.google.com:19302"))

            val urls = BuildConfig.TURN_URLS
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val user = BuildConfig.TURN_USER
            val pass = BuildConfig.TURN_PASS

            val relay = if (urls.isEmpty()) defaultTurnUrls else urls
            val relayUser = if (user.isBlank()) DEFAULT_TURN_USER else user
            val relayPass = if (pass.isBlank()) DEFAULT_TURN_PASS else pass

            if (relay.isNotEmpty() && relayUser.isNotBlank() && relayPass.isNotBlank()) {
                // org.webrtc IceServer.Builder takes ONE URL per call.
                // Multiple TURN URLs require multiple IceServer objects.
                relay.forEach { url ->
                    add(IceServerConfig(url = url, username = relayUser, credential = relayPass))
                }
            }
        }

    /** Parsed once; the default is a constant, so this is not a hot path. */
    private val defaultTurnUrls: List<String>
        get() = DEFAULT_TURN.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * True when a relay is actually configured. Surfaced so the app can say so
     * instead of letting a mobile-data call fail with no explanation -- a
     * connection that only works on the sofa is a surprise waiting to happen.
     */
    val hasTurnRelay: Boolean
        get() = iceServers.any { it.username != null }

    const val DEFAULT_TURN = "turn:openrelay.metered.ca:80," +
        "turn:openrelay.metered.ca:443," +
        "turn:openrelay.metered.ca:443?transport=tcp"
    const val DEFAULT_TURN_USER = "openrelayproject"
    const val DEFAULT_TURN_PASS = "openrelayproject"
}
