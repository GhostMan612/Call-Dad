// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HomeViewModel.kt — the doors out of Home, and the callback card
// Location: app/src/main/java/com/calldad/ui/screens/HomeViewModel.kt
package com.calldad.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.history.CallLogStore
import com.calldad.history.CallRecord
import com.calldad.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The doors out of Home, plus the missed-call callback card.
 *
 * The card is the whole reason this is an AndroidViewModel now. BP-05 §4 lists
 * "missed-call callback card" as a criterion and the kid-UX audit had it as an
 * open FAIL; it needs somewhere to remember that a call was missed, and on this
 * app that is `CallLogStore` (DataStore — see ADR-018 for why not Room).
 *
 * The card is derived, not stored: it is recomputed from the log whenever the log
 * changes, so there is no second copy of the same fact to fall out of sync.
 */
enum class HomeDestination(val route: String, val label: String) {
    CALL(Routes.CALL, "Call Dad"),
    CHAT(Routes.CHAT, "Messages"),
    PHOTO(Routes.PHOTO, "Pictures"),
    PTT(Routes.PTT, "Walkie Talkie"),
    GAME(Routes.GAME, "Play Games"),
    HELPER(Routes.HELPER, "Ask Helper")
}

/**
 * Home's own state is its doors and its callback. Incoming rings are NOT watched
 * here: the activity-scoped CallViewModel listens on every screen and AppNavHost
 * pulls the kid to the ring screen from wherever they are.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val log = CallLogStore(application)

    private val _destinations = MutableStateFlow(HomeDestination.entries.toList())
    val destinations: StateFlow<List<HomeDestination>> = _destinations.asStateFlow()

    private val _callback = MutableStateFlow<CallRecord?>(null)
    val callback: StateFlow<CallRecord?> = _callback.asStateFlow()

    init {
        viewModelScope.launch {
            log.observe().collect { rows ->
                _callback.value = com.calldad.history.CallLog.callbackCard(
                    rows,
                    System.currentTimeMillis()
                )
            }
        }
    }

    /** The child acted on the card. History stays; only the prompt goes. */
    fun onCallbackTapped() {
        viewModelScope.launch { log.dismissCallback(System.currentTimeMillis()) }
    }
}

class HomeViewModelFactory(private val application: Application) :
    androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(application) as T
}
