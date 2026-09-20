// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// webrtc/WebRtcConfig.kt — Phase 4: structured ICE config with TURN sentinel
// Location: app/src/main/java/com/calldad/webrtc/WebRtcConfig.kt
package com.calldad.webrtc

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
     * ICE servers. STUN-only by default.
     *
     * TODO(Phase 4 / GhostMan612):
     *   Replace TURN_USERNAME / TURN_CREDENTIAL with real values locally.
     *   DO NOT commit real TURN credentials to this file — move them to
     *   local.properties and surface them via BuildConfig before doing so.
     *
     * The TURN entry is only included when its username is no longer the
     * REPLACE_ME sentinel. An unconfigured build therefore falls back to
     * STUN-only with no dead TURN requests and no log noise.
     */
    private const val TURN_URL = "turn:k8-turn.example.com:3478"
    private const val TURN_USERNAME = "REPLACE_ME"
    private const val TURN_CREDENTIAL = "REPLACE_ME"
    private const val REPLACE_SENTINEL = "REPLACE_ME"

    val iceServers: List<IceServerConfig>
        get() = buildList {
            add(IceServerConfig(url = "stun:stun.l.google.com:19302"))
            add(IceServerConfig(url = "stun:stun1.l.google.com:19302"))
            if (TURN_USERNAME != REPLACE_SENTINEL) {
                add(IceServerConfig(
                    url = TURN_URL,
                    username = TURN_USERNAME,
                    credential = TURN_CREDENTIAL
                ))
            }
        }
}
