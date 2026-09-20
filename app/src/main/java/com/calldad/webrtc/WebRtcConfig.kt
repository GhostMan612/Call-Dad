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
     * ICE servers. STUN-only unless TURN was provisioned locally.
     *
     * TURN credentials arrive via local.properties → BuildConfig (never
     * committed). Only included when all three are non-blank.
     * DO NOT log these values anywhere, ever (guardrail §G).
     */
    val iceServers: List<IceServerConfig>
        get() = buildList {
            add(IceServerConfig(url = "stun:stun.l.google.com:19302"))
            add(IceServerConfig(url = "stun:stun1.l.google.com:19302"))

            val turnUrl = BuildConfig.TURN_URL
            val turnUser = BuildConfig.TURN_USER
            val turnPass = BuildConfig.TURN_PASS

            // Only include TURN if all three were provisioned locally.
            if (turnUrl.isNotBlank() && turnUser.isNotBlank() && turnPass.isNotBlank()) {
                add(IceServerConfig(
                    url = turnUrl,
                    username = turnUser,
                    credential = turnPass
                ))
            }
        }
}
