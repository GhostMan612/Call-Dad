// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/PttViewModel.kt — Phase 6: engine + focus + interlock
// Location: app/src/main/java/com/calldad/ui/screens/PttViewModel.kt
package com.calldad.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.ptt.PttAudioManager
import com.calldad.ptt.PttAudioState
import com.calldad.ptt.PttEngine
import com.calldad.ptt.PttFailure
import com.calldad.ptt.VoiceClipPttEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PttUiState(
    val isTransmitting: Boolean = false,
    val isReceiving: Boolean = false,
    val justSent: Boolean = false,
    val lastError: String? = null
)

class PttViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * ENGINE: [VoiceClipPttEngine] — hold to record, release to send over
     * the pair's private room; the peer plays clips on any screen (ADR-016).
     * The Sovereign Mantle adapter was a placeholder for a module that was
     * never on this app's classpath; it is retired.
     */
    private val audioManager = PttAudioManager(application) {
        viewModelScope.launch { onRelease() }
    }

    private val voiceClips = VoiceClipPttEngine(application, viewModelScope)
    private val engine: PttEngine = voiceClips
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

    /** Called by the app when a WebRTC call becomes active: the call owns the mic. */
    fun onCallStateChanged(active: Boolean) {
        if (active) onRelease()
        audioManager.setCallActive(active)
        voiceClips.setPlaybackBlocked(active)
    }

    // -------- gesture handlers --------

    fun onPress() {
        if (_state.value.isTransmitting) return

        val focusFailure = audioManager.requestFocus()
        if (focusFailure != null) {
            _state.value = _state.value.copy(lastError = focusFailure.userMessage)
            return
        }

        audioManager.vibratePress()
        _state.value = _state.value.copy(isTransmitting = true, justSent = false, lastError = null)

        viewModelScope.launch {
            val result = engine.startTransmitting()
            if (result.isFailure) {
                val f = result.exceptionOrNull() as? PttFailure
                _state.value = _state.value.copy(
                    isTransmitting = false,
                    lastError = f?.userMessage ?: "Couldn't start talking."
                )
                audioManager.abandonFocus()
            }
        }
    }

    fun onRelease() {
        if (!_state.value.isTransmitting) return
        audioManager.vibrateRelease()
        _state.value = _state.value.copy(isTransmitting = false)

        viewModelScope.launch {
            val result = engine.stopTransmitting()
            audioManager.abandonFocus()
            val f = result.exceptionOrNull() as? PttFailure
            _state.value = if (result.isSuccess) {
                _state.value.copy(justSent = true, lastError = null)
            } else {
                _state.value.copy(lastError = f?.userMessage ?: "Couldn't send. Try again.")
            }
            if (result.isSuccess) {
                delay(SENT_FLASH_MS)
                _state.value = _state.value.copy(justSent = false)
            }
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

private const val SENT_FLASH_MS = 2_000L

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
    val activity = LocalContext.current as ComponentActivity
    return viewModel(
        viewModelStoreOwner = activity,
        factory = PttViewModelFactory(activity.application)
    )
}
