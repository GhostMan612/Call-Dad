// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HomeViewModel.kt — Phase 5: ring-pointer auto-popup
// Location: app/src/main/java/com/calldad/ui/screens/HomeViewModel.kt
package com.calldad.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.data.signaling.SignalingClient
import com.calldad.navigation.Routes
import com.calldad.webrtc.WebRtcLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** The four doors out of Home. Order here == order on screen (reading order). */
enum class HomeDestination(val route: String, val label: String) {
    CALL(Routes.CALL, "Call Dad"),
    PTT(Routes.PTT, "Walkie Talkie"),
    GAME(Routes.GAME, "Play Games"),
    HELPER(Routes.HELPER, "Ask Helper")
}

class HomeViewModel(
    private val signaling: SignalingClient = SignalingClient()
) : ViewModel() {

    private val _destinations = MutableStateFlow(HomeDestination.entries.toList())
    val destinations: StateFlow<List<HomeDestination>> = _destinations.asStateFlow()

    /**
     * Fires once per NEW ring while Home is visible, carrying its callId.
     *
     * Home-scoped by construction: this VM dies when Home is left, so the
     * listener stops off-Home (no background drain, no yanking the child
     * out of another screen). Killed-app wakeup is FCM (Phase 5 core).
     * Deduped by callId; own + stale rings filtered inside the client.
     */
    private val _incomingCall = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val incomingCall: SharedFlow<String> = _incomingCall.asSharedFlow()

    private var seenCallId: String? = null

    init {
        viewModelScope.launch {
            signaling.observeRing()
                .catch { /* offline: stay silent, retry on next Home entry */ }
                .collect { ring ->
                    if (ring.callId != seenCallId) {
                        seenCallId = ring.callId
                        // Literal only: proves receipt without leaking the id.
                        WebRtcLog.transition("Ring observed")
                        _incomingCall.tryEmit(ring.callId)
                    }
                }
        }
    }
}
