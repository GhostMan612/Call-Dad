// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HomeViewModel.kt — Phase 4: incoming-call auto-popup
// Location: app/src/main/java/com/calldad/ui/screens/HomeViewModel.kt
package com.calldad.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.data.signaling.OwnOfferRegistry
import com.calldad.data.signaling.SdpType
import com.calldad.data.signaling.SignalingClient
import com.calldad.navigation.Routes
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
     * Fires once per NEW incoming OFFER while Home is visible.
     *
     * Home-scoped by construction: this VM dies when Home is left, so the
     * Firestore listener stops off-Home (no background drain, no yanking the
     * child out of another screen). Killed-app wakeup is Phase 5 (FCM).
     * Deduped by SDP hash so rotations/re-subscribes don't double-ring.
     */
    private val _incomingCall = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val incomingCall: SharedFlow<Unit> = _incomingCall.asSharedFlow()

    private var seenOfferHash: Int? = null

    init {
        viewModelScope.launch {
            signaling.observeRemoteDescription(SdpType.OFFER)
                .catch { /* offline: stay silent, retry on next Home entry */ }
                .collect { remote ->
                    // Our own ringback (we called, hung up, called again):
                    // never ring ourselves. See OwnOfferRegistry.
                    if (OwnOfferRegistry.isOwn(remote.sdp)) return@collect
                    // Abandoned ring (caller vanished without teardown):
                    // never ring for the dead. See OFFER_STALE_MS.
                    if (remote.isStale()) return@collect
                    val hash = remote.sdp.hashCode()
                    if (hash != seenOfferHash) {
                        seenOfferHash = hash
                        _incomingCall.tryEmit(Unit)
                    }
                }
        }
    }
}
