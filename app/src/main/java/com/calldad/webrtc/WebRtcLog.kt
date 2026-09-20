// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// webrtc/WebRtcLog.kt
// Location: app/src/main/java/com/calldad/webrtc/WebRtcLog.kt
package com.calldad.webrtc

import android.util.Log

/**
 * GUARDRAIL. Every WebRTC-touching class logs through this object only.
 *
 * FORBIDDEN in every call site:
 *   - raw SDP strings (SessionDescription.description)
 *   - raw ICE candidate payloads (IceCandidate.sdp / sdpCandidate)
 *   - IP addresses, server URLs, sdpMid values
 *   - Firebase UIDs or Firestore document IDs
 *
 * The single allowed argument shape is a fixed string or a sealed enum name.
 */
internal object WebRtcLog {
    private const val TAG = "WebRTC"
    fun transition(event: String) {
        Log.d(TAG, event)
    }
}
