// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/PairingViewModel.kt
// Location: app/src/main/java/com/calldad/ui/screens/PairingViewModel.kt
package com.calldad.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calldad.pairing.ModuleAvailabilityCheck
import com.calldad.pairing.QrGenerator
import com.calldad.pairing.SecurePeerStore
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object CheckingModule : PairingUiState
    data object Loading : PairingUiState
    data class Ready(val qrBitmap: android.graphics.Bitmap) : PairingUiState
    data object Paired : PairingUiState
    data class Error(val message: String) : PairingUiState
}

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val store = SecurePeerStore(appContext)

    private val _state = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private val _resetTrigger = MutableStateFlow(0)
    val resetTrigger: StateFlow<Int> = _resetTrigger.asStateFlow()

    private val sessionNonce: String = UUID.randomUUID().toString()

    init {
        initialize()
    }

    private fun initialize() {
        _state.value = PairingUiState.CheckingModule
        viewModelScope.launch {
            val moduleReady =
                ModuleAvailabilityCheck.ensureBarcodeModule(appContext)
            if (!moduleReady) {
                _state.value = PairingUiState.Error(
                    "The scanner isn't ready. Check that Google Play " +
                    "Services is installed and you have internet."
                )
                return@launch
            }
            generateQr()
        }
    }

    private fun generateQr() {
        _state.value = PairingUiState.Loading
        viewModelScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                    ?: throw IllegalStateException("Not signed in yet.")
                val fcm = FirebaseMessaging.getInstance().token.await()
                if (fcm.isBlank()) {
                    throw IllegalStateException("FCM token not ready.")
                }

                val payload = JSONObject().apply {
                    put("v", 1)
                    put("uid", uid)
                    put("fcm", fcm)
                    put("nonce", sessionNonce)
                }.toString()

                val bitmap = QrGenerator.generate(payload)
                _state.value = PairingUiState.Ready(bitmap)
            } catch (t: Throwable) {
                _state.value = PairingUiState.Error(
                    t.message ?: "Could not generate QR code."
                )
            }
        }
    }

    fun onQrScanned(payload: String) {
        viewModelScope.launch {
            try {
                val json = JSONObject(payload)
                val version = json.optInt("v", 0)
                if (version != 1) {
                    _state.value = PairingUiState.Error(
                        "This code is from a different version."
                    )
                    return@launch
                }
                val peerUid = json.optString("uid")
                val peerFcm = json.optString("fcm")
                if (peerUid.isBlank() || peerFcm.isBlank()) {
                    _state.value = PairingUiState.Error(
                        "That code didn't work. Try again."
                    )
                    return@launch
                }

                store.storePeer(peerUid, peerFcm)
                WebRtcLog.transition("Pairing: peer stored")
                _state.value = PairingUiState.Paired
            } catch (t: Throwable) {
                WebRtcLog.transition("Pairing failed")
                _state.value = PairingUiState.Error(
                    "That code didn't work. Try again."
                )
            }
        }
    }

    fun retryScanner() {
        val currentBitmap =
            (state.value as? PairingUiState.Ready)?.qrBitmap
        if (currentBitmap != null) {
            _state.value = PairingUiState.Ready(currentBitmap)
            _resetTrigger.value = _resetTrigger.value + 1
        } else {
            initialize()
        }
    }
}
