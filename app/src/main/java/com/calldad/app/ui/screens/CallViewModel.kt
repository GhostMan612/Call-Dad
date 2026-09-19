// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/CallViewModel.kt
// Location: app/src/main/java/com/calldad/app/ui/screens/CallViewModel.kt
package com.calldad.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class CallStatus { CONNECTING, CONNECTED, ENDED }

data class CallUiState(
    val status: CallStatus = CallStatus.CONNECTING,
    val isCameraOn: Boolean = true,
    val elapsedSeconds: Int = 0
) {
    val timerLabel: String
        get() = String.format(
            Locale.US,
            "%02d:%02d",
            elapsedSeconds / 60,
            elapsedSeconds % 60
        )
}

class CallViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        // PHASE 1 PLACEHOLDER — simulates the connect handshake so the UI
        // exercises every visual state.
        // PHASE 2: replace with a real signalling / WebRTC state flow.
        viewModelScope.launch {
            delay(1_500)
            if (_uiState.value.status == CallStatus.CONNECTING) {
                _uiState.update { it.copy(status = CallStatus.CONNECTED) }
                startTimer()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    fun onToggleCamera() {
        _uiState.update { it.copy(isCameraOn = !it.isCameraOn) }
    }

    fun onHangUp() {
        timerJob?.cancel()
        _uiState.update { it.copy(status = CallStatus.ENDED) }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }
}
