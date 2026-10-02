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
import com.calldad.consent.ConsentDecision
import com.calldad.consent.ConsentStore
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
    val isSending: Boolean = false,
    val isReceiving: Boolean = false,
    val justSent: Boolean = false,
    val lastError: String? = null,
    /**
     * False when the PTT grant is absent, so the screen can SAY so.
     *
     * A separate field from [lastError] on purpose: a consent denial is not an
     * error, it is the correct result of a permission decision. Routing it
     * through `lastError` meant an unrelated audio failure clearing the field
     * would silently wipe "ask a grown-up" and leave a button that does nothing
     * when pressed — which is the one state a child cannot act on.
     *
     * Defaults to FALSE so a cold start is dead until a grant arrives. Absence
     * denies; an allowlist app that fails open is not an allowlist app.
     */
    val isAllowed: Boolean = false
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

    /**
 * The PTT grant, observed live (ADR-017).
 *
 * The walkie talkie had no consent check at all — it is the second-oldest
 * feature and predates the consent model, so it shipped "ALREADY SHIPPED
 * without a cert" and nobody went back. That left the kill switch covering
 * messages and photos while the child could still talk to their grown-up
 * indefinitely. `firestore.rules` cannot close this: the PTT room is written by
 * pair membership, so an unenforced client is the only enforcement there is.
 */
private val consent = ConsentStore()

init {
        consent.start(application, viewModelScope)
        watchEngine()
        viewModelScope.launch {
            consent.decision.collect { d -> onConsentChanged(d) }
        }
    }

    /**
     * A revoked PTT grant stops the button AND tears down anything in flight.
     */
    private fun onConsentChanged(decision: ConsentDecision?) {
        val allowed = decision?.isGranted == true
        pttAllowed = allowed
        _state.value = _state.value.copy(isAllowed = allowed)
        // The INBOUND half. Gating `onPress` stops a child sending, but without
        // this a revoked child's phone still played every clip in the room,
        // unprompted, on whatever screen it was on. A kill switch that leaves
        // the microphone-to-speaker path open is not a kill switch.
        voiceClips.setInboundAllowed(allowed)
        if (allowed) return
        // Tear down anything in flight. A parent who hits the kill switch
        // mid-clip must not be left with the child's recording still uploading
        // after the button has gone dead.
        if (_state.value.isTransmitting || _state.value.isSending) {
            onRelease()
        }
    }

    /**
     * Mirrors [PttUiState.isAllowed] for the synchronous check in [onPress].
     *
     * The state field alone cannot gate the press: a press can land in the same
     * frame the revocation arrives, and the handler must refuse on the value it
     * can read without waiting for recomposition.
     */
    private var pttAllowed = false

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
        if (_state.value.isTransmitting || _state.value.isSending) return
        if (!pttAllowed) {
            _state.value = _state.value.copy(
                lastError = "The walkie talkie is turned off right now. Ask a grown-up to turn it on."
            )
            return
        }

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

        // isTransmitting clears only AFTER the send resolves, so a fast
        // press-release-press cannot record nothing and then ship the PREVIOUS
        // clip under the new press's confirmation.
        _state.value = _state.value.copy(isSending = true)

        viewModelScope.launch {
            val result = engine.stopTransmitting()
            audioManager.abandonFocus()
            val f = result.exceptionOrNull() as? PttFailure
            _state.value = _state.value.copy(
                isTransmitting = false,
                isSending = false,
                justSent = result.isSuccess,
                lastError = if (result.isSuccess) null else f?.userMessage ?: "Couldn't send. Try again."
            )
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
