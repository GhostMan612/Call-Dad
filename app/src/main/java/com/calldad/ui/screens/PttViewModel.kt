// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/PttViewModel.kt — Phase 6: engine + focus + interlock
// Location: app/src/main/java/com/calldad/ui/screens/PttViewModel.kt
package com.calldad.ui.screens

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.ptt.PttAudioManager
import com.calldad.ptt.PttAudioState
import com.calldad.ptt.PttEngine
import com.calldad.ptt.PttFailure
import com.calldad.ptt.PttFailureKind
import com.calldad.ptt.SimulatedPttEngine
import com.calldad.ptt.SovereignPttAdapter
import com.calldad.webrtc.WebRtcLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PttUiState(
    val isTransmitting: Boolean = false,
    val isReceiving: Boolean = false,
    val lastError: String? = null
)

class PttViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * ENGINE SELECTION.
     *
     * SovereignPttAdapter is constructed first; if Sovereign Mantle is not
     * on the classpath, its startTransmitting() returns ENGINE_UNAVAILABLE
     * and the VM falls back to SimulatedPttEngine on the FIRST failure.
     *
     * This means: on a developer machine without the private module, the
     * UI works. On a production build with the module present, the real
     * engine is used transparently. Same binary, same source, no flags.
     */
    private val audioManager = PttAudioManager(application)

    private val sovereign: PttEngine = SovereignPttAdapter(application)
    private var engine: PttEngine = sovereign
    private var inboundJob: Job? = null

    private val _state = MutableStateFlow(PttUiState())
    val state: StateFlow<PttUiState> = _state.asStateFlow()

    init {
        watchEngine()
    }

    /** (Re)subscribes inbound audio from whichever engine is active. */
    private fun watchEngine() {
        inboundJob?.cancel()
        inboundJob = viewModelScope.launch {
            engine.observeIncomingAudio().collect { inbound ->
                when (inbound) {
                    PttAudioState.Idle ->
                        _state.value = _state.value.copy(isReceiving = false)
                    PttAudioState.Receiving ->
                        _state.value = _state.value.copy(isReceiving = true)
                    is PttAudioState.Error ->
                        _state.value = _state.value.copy(
                            isReceiving = false,
                            lastError = inbound.message
                        )
                }
            }
        }
    }

    // -------- external signals --------

    /** Called by the app when a WebRTC call becomes active. */
    fun onCallStateChanged(active: Boolean) = audioManager.setCallActive(active)

    // -------- gesture handlers --------

    fun onPress() {
        if (_state.value.isTransmitting) return

        val focusFailure = audioManager.requestFocus()
        if (focusFailure != null) {
            _state.value = _state.value.copy(lastError = focusFailure.userMessage)
            return
        }

        audioManager.vibratePress()
        _state.value = _state.value.copy(isTransmitting = true, lastError = null)

        viewModelScope.launch {
            val result = engine.startTransmitting()
            if (result.isFailure) {
                val f = result.exceptionOrNull() as? PttFailure
                if (f?.kind == PttFailureKind.ENGINE_UNAVAILABLE) {
                    WebRtcLog.transition("PTT: falling back to simulated engine")
                    engine = SimulatedPttEngine()
                    watchEngine()
                    engine.startTransmitting()
                } else {
                    _state.value = _state.value.copy(
                        isTransmitting = false,
                        lastError = f?.userMessage ?: "Couldn't start talking."
                    )
                    audioManager.abandonFocus()
                }
            }
        }
    }

    fun onRelease() {
        if (!_state.value.isTransmitting) return
        audioManager.vibrateRelease()
        _state.value = _state.value.copy(isTransmitting = false)

        viewModelScope.launch {
            engine.stopTransmitting()
            audioManager.abandonFocus()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(lastError = null)
    }

    override fun onCleared() {
        inboundJob?.cancel()
        audioManager.abandonFocus()
        engine.release()
        super.onCleared()
    }
}

/** Manual factory: AndroidViewModel has no zero-arg constructor. */
class PttViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PttViewModel(application) as T
    }
}

/**
 * Shared ACTIVITY-scoped accessor — the ONE correct way for two destinations
 * to see the same PttViewModel.
 *
 * The architect prompt's claim (default viewModel() calls share an instance
 * across screens) is wrong twice over: the default factory cannot build an
 * AndroidViewModel at all (instant crash, same bug as Phase 3's CallViewModel),
 * and viewModel() is NavBackStackEntry-scoped, so CallScreen and PttScreen
 * would each get a PRIVATE instance and the mic interlock would silently
 * never fire. Activity scope fixes both. See ADR-008.
 */
@Composable
fun rememberPttViewModel(): PttViewModel {
    val activity = LocalActivity.current
    return viewModel(
        viewModelStoreOwner = activity,
        factory = PttViewModelFactory(activity.application)
    )
}
