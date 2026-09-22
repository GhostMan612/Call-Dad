// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HomeViewModel.kt — navigation doors + incoming-call event
// Location: app/src/main/java/com/calldad/ui/screens/HomeViewModel.kt
package com.calldad.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.data.signaling.SignalingClient
import com.calldad.navigation.Routes
import com.google.firebase.auth.FirebaseAuth
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
     * Fires once per NEW live ring while Home is visible.
     *
     * Home-scoped by construction: this VM dies when Home is left, so the
     * listener stops off-Home. Own ringback, foreign rings, and stale
     * generations are skipped (never ring ourselves, never ring for the
     * dead). Killed-app wakeup is FCM.
     */
    private val _incomingCall = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val incomingCall: SharedFlow<Unit> = _incomingCall.asSharedFlow()

    private var seenSeq: Int = -1

    init {
        viewModelScope.launch {
            signaling.observeCall(CallViewModel.STATIC_ROOM_ID)
                .catch { /* offline: stay silent, retry on next Home entry */ }
                .collect { doc ->
                    if (doc.status != "RINGING" || doc.offer == null) return@collect
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                        ?: return@collect
                    if (doc.callerUid.isEmpty() || doc.callerUid == uid) return@collect
                    if (doc.calleeUid != uid) return@collect
                    if (doc.seq <= seenSeq) return@collect
                    seenSeq = doc.seq
                    _incomingCall.tryEmit(Unit)
                }
        }
    }
}
