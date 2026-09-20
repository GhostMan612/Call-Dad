// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// webrtc/WebRtcConfig.kt
// Location: app/src/main/java/com/calldad/webrtc/WebRtcConfig.kt
package com.calldad.webrtc

object WebRtcConfig {
    val ICE_SERVERS = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302"
    )

    const val LOCAL_STREAM_ID = "calldad_stream"
    const val AUDIO_TRACK_ID = "calldad_audio"
    const val VIDEO_TRACK_ID = "calldad_video"

    const val VIDEO_WIDTH = 640
    const val VIDEO_HEIGHT = 480
    const val VIDEO_FPS = 24
}
