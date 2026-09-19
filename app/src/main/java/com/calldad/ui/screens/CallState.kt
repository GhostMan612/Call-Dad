// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallState.kt
// Location: app/src/main/java/com/calldad/ui/screens/CallState.kt
package com.calldad.ui.screens

/** Which side of the OFFER/ANSWER exchange this device is playing. */
enum class CallRole { CALLER, CALLEE }

/**
 * The complete lifecycle of one call, as far as the UI is concerned.
 *
 *  Idle ──startCall()/answerCall()──> Connecting ──remote SDP──> InCall
 *    ^                                     │                        │
 *    └─────────────────endCall()───────────┴────────────────────────┘
 *                                          │
 *                                          └──> Error
 */
sealed interface CallState {

    /** Nothing happening. Initial state; returns here after hang-up. */
    data object Idle : CallState

    /** Signaling in progress: exchanging SDP via Firestore. */
    data object Connecting : CallState

    /** Remote SDP received; the peer connection is (about to be) live. */
    data class InCall(
        val role: CallRole,
        val startedAtMillis: Long
    ) : CallState

    /** Recoverable failure surfaced to the child as a single big Retry button. */
    data class Error(
        val kind: com.calldad.data.signaling.SignalingErrorKind,
        val message: String
    ) : CallState
}
