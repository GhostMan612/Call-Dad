// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// webrtc/ConnectionState.kt — Phase 8: coarse connection health
// Location: app/src/main/java/com/calldad/webrtc/ConnectionState.kt
package com.calldad.webrtc

/**
 * Coarse-grained connection health, derived from the finer-grained
 * PeerConnection.IceConnectionState. The debouncing logic lives in
 * WebRTCClient; this enum is what the UI observes.
 */
enum class ConnectionHealth {
    /** ICE CONNECTED or COMPLETED. All good. */
    HEALTHY,
    /** ICE DISCONNECTED, within the debounce window. UI shows nothing. */
    DEGRADED,
    /** ICE DISCONNECTED past the debounce window, or FAILED. UI shows
     *  "Connection Lost. Reconnecting…". */
    LOST,
    /** ICE restart offer published; awaiting peer. */
    RECONNECTING
}
