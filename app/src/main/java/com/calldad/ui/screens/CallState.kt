// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallState.kt
// Location: app/src/main/java/com/calldad/ui/screens/CallState.kt
package com.calldad.ui.screens

/**
 * The seven-state call machine. This is the ONLY state type the UI
 * observes. CallViewModel maps every room document to one of these via
 * [fromDocument] and commits it only when [canTransition] allows.
 *
 * TRANSITION TABLE:
 *
 *   Idle      -> Ringing                       (user call / fresh doc ring)
 *   Ringing   -> Connected | Declined | NoAnswer | Ended | Error | Ringing
 *   Connected -> Ringing (new generation) | Ended | Error
 *   NoAnswer  -> Ringing (try again) | Ended | Idle
 *   Declined  -> Idle | Ringing
 *   Ended     -> Idle | Ringing
 *   Error     -> Idle | Ringing
 *
 * Every state other than Idle has a way out, so the kid can never be
 * stranded on a screen: terminal states auto-dismiss, Error has Dismiss.
 */
sealed interface CallState {

    data object Idle : CallState

    data class Ringing(
        val seq: Int,
        val isIncoming: Boolean,
        val peerName: String,
        val callId: String
    ) : CallState

    data class Connected(val seq: Int) : CallState

    data object Declined : CallState

    data class NoAnswer(val seq: Int) : CallState

    data class Ended(val reason: EndReason) : CallState

    data class Error(
        val kind: CallErrorKind,
        val message: String,
        val seq: Int? = null
    ) : CallState

    companion object {
        fun fromDocument(
            status: String,
            seq: Int,
            isIncoming: Boolean,
            peerName: String,
            callId: String
        ): CallState = when (status) {
            "RINGING"   -> Ringing(seq, isIncoming, peerName, callId)
            "CONNECTED" -> Connected(seq)
            "DECLINED"  -> Declined
            "ENDED"     -> Ended(EndReason.REMOTE_HANGUP)
            "IDLE"      -> Idle
            else        -> Error(CallErrorKind.MALFORMED, "Unknown status.", seq)
        }

        fun canTransition(from: CallState, to: CallState): Boolean {
            if (from::class == to::class) return true
            return when (from) {
                is Idle -> to is Ringing
                is Ringing ->
                    to is Connected || to is Declined || to is NoAnswer ||
                        to is Ended || to is Error
                is Connected -> to is Ringing || to is Ended || to is Error
                is NoAnswer -> to is Ringing || to is Ended || to is Idle
                is Declined -> to is Idle || to is Ringing
                is Ended -> to is Idle || to is Ringing
                is Error -> to is Idle || to is Ringing
            }
        }
    }
}

/** True while media should be live (ringing either way, or in the call). */
val CallState.isLive: Boolean
    get() = this is CallState.Ringing || this is CallState.Connected

enum class EndReason {
    LOCAL_HANGUP,
    REMOTE_HANGUP,
    NETWORK_FAILURE,
    MISSED
}

enum class CallErrorKind {
    LISTENER_DISCONNECTED,
    SIGNALING_FAILED,
    WEBRTC_FAILED,
    PERMISSION_DENIED,
    NOT_PAIRED,
    MALFORMED,
    UNKNOWN;

    /** Recoverable kinds get a Try Again button. */
    val isRecoverable: Boolean
        get() = this == LISTENER_DISCONNECTED || this == SIGNALING_FAILED ||
            this == WEBRTC_FAILED || this == UNKNOWN
}
