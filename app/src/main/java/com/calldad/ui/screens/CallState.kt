// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallState.kt
// Location: app/src/main/java/com/calldad/ui/screens/CallState.kt
package com.calldad.ui.screens

/**
 * The seven-state call machine. This is the ONLY state type the UI
 * observes. The ViewModel maps every Firestore document change to exactly
 * one of these states via CallState.fromDocument(...).
 *
 * TRANSITION TABLE (enforced in CallViewModel via canTransition):
 *
 *   Idle           + USER_CALL       -> Ringing(isIncoming=false)
 *   Idle           + DOC_RINGING     -> Ringing(isIncoming=true)
 *
 *   Ringing(false) + DOC_ANSWER      -> Connected
 *   Ringing(false) + TIMEOUT_15S     -> NoAnswer(seq)
 *   Ringing(false) + DOC_DECLINED    -> Declined
 *   Ringing(false) + USER_HANGUP     -> Ended(LOCAL_HANGUP)
 *
 *   Ringing(true)  + USER_ANSWER     -> Connected
 *   Ringing(true)  + USER_DECLINE    -> Declined
 *   Ringing(true)  + DOC_ENDED       -> Ended(REMOTE_HANGUP)
 *
 *   Connected      + USER_HANGUP     -> Ended(LOCAL_HANGUP)
 *   Connected      + DOC_ENDED       -> Ended(REMOTE_HANGUP)
 *   Connected      + DOC_SEQ_CHANGED -> Ringing(isIncoming=true)
 *
 *   NoAnswer(seq)  + USER_RERING     -> Ringing(isIncoming=false)
 *   NoAnswer(seq)  + USER_HANGUP     -> Ended(LOCAL_HANGUP)
 *
 *   Declined       + AUTO_DISMISS    -> Idle
 *   Ended          + AUTO_DISMISS    -> Idle
 *   Error          + USER_DISMISS    -> Idle
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
    }
}

enum class EndReason {
    LOCAL_HANGUP,
    REMOTE_HANGUP,
    NETWORK_FAILURE,
    TIMEOUT
}

enum class CallErrorKind {
    LISTENER_DISCONNECTED,
    SIGNALING_FAILED,
    TRANSACTION_EXHAUSTED,
    WEBRTC_FAILED,
    PERMISSION_DENIED,
    PEER_BUSY,
    MALFORMED,
    UNKNOWN
}
