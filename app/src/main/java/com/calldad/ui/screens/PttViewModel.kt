// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/PttViewModel.kt
// Location: app/src/main/java/com/calldad/ui/screens/PttViewModel.kt
package com.calldad.ui.screens

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PttUiState(
    val isTransmitting: Boolean = false
) {
    val isListening: Boolean get() = !isTransmitting
}

class PttViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PttUiState())
    val uiState: StateFlow<PttUiState> = _uiState.asStateFlow()

    /** PHASE 2: start microphone capture + open the PTT audio channel here. */
    fun onPressStart() {
        _uiState.update { it.copy(isTransmitting = true) }
    }

    /** PHASE 2: stop capture + release the channel here. */
    fun onPressEnd() {
        _uiState.update { it.copy(isTransmitting = false) }
    }
}
