// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HomeViewModel.kt — navigation doors + incoming-call event
// Location: app/src/main/java/com/calldad/ui/screens/HomeViewModel.kt
package com.calldad.ui.screens

import androidx.lifecycle.ViewModel
import com.calldad.navigation.Routes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** The four doors out of Home. Order here == order on screen (reading order). */
enum class HomeDestination(val route: String, val label: String) {
    CALL(Routes.CALL, "Call Dad"),
    PTT(Routes.PTT, "Walkie Talkie"),
    GAME(Routes.GAME, "Play Games"),
    HELPER(Routes.HELPER, "Ask Helper")
}

class HomeViewModel : ViewModel() {

    private val _destinations = MutableStateFlow(HomeDestination.entries.toList())
    val destinations: StateFlow<List<HomeDestination>> = _destinations.asStateFlow()

    /**
     * Fires once per live ring while Home is visible. HomeScreen collects
     * this flow, so the public surface is unchanged. The emitter moves to
     * the observeCall pipeline (FCM contract owns the re-feed).
     */
    private val _incomingCall = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val incomingCall: SharedFlow<Unit> = _incomingCall.asSharedFlow()
}
