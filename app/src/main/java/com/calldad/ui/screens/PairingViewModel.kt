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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object CheckingModule : PairingUiState
    data object Loading : PairingUiState
    data class Ready(val qrBitmap: android.graphics.Bitmap) : PairingUiState
    data class Paired(
        val qrBitmap: android.graphics.Bitmap
    ) : PairingUiState
    data class Error(val message: String) : PairingUiState
}

class PairingViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val store = SecurePeerStore(appContext)
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private val _resetTrigger = MutableStateFlow(0)
    val resetTrigger: StateFlow<Int> = _resetTrigger.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Unit>()
    val navigationEvent: SharedFlow<Unit> = _navigationEvent.asSharedFlow()

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

    @Suppress("DEPRECATION")
    // TODO: Defer to FID-based Admin SDK migration. Requires
    // synchronized Cloud Function rewrite and Firestore schema
    // migration (fcmToken -> fid). Do NOT partial-migrate.
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

    private fun deriveSessionNonce(
        ownQrNonce: String,
        peerQrNonce: String
    ): String {
        val sorted = listOf(ownQrNonce, peerQrNonce).sorted()
        return "${sorted[0]}:${sorted[1]}"
    }

    private suspend fun writeHandshake(
        ownUid: String,
        peerUid: String,
        nonce: String
    ) {
        runCatching {
            firestore.collection("pairings").document(ownUid).delete().await()
        }
        firestore.collection("pairings").document(ownUid).set(
            mapOf(
                "uid" to ownUid,
                "peerUid" to peerUid,
                "sessionNonce" to nonce,
                "role" to if (ownUid < peerUid) "initiator" else "responder",
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(
                    Date(System.currentTimeMillis() + 1_200_000L)
                )
            )
        ).await()
    }

    /**
     * Observes the handshake pair via listener (callbackFlow, not the
     * firestore-ktx `snapshots()` extension — KTX artifacts are banned by
     * Phase-2 tree law). Emits true once both sides hold unexpired docs.
     */
    private fun observeHandshake(
        ownUid: String,
        peerUid: String,
        nonce: String
    ): Flow<Boolean> = callbackFlow {
        val reg = firestore.collection("pairings")
            .whereEqualTo("sessionNonce", nonce)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val docs = snapshot?.documents.orEmpty()
                val ownValid = docs.find { it.id == ownUid }
                    ?.getTimestamp("expiresAt")
                    ?.toDate()?.time?.let { it > now } ?: false
                val peerValid = docs.find { it.id == peerUid }
                    ?.getTimestamp("expiresAt")
                    ?.toDate()?.time?.let { it > now } ?: false
                trySend(ownValid && peerValid)
            }
        awaitClose { reg.remove() }
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
                val peerQrNonce = json.optString("nonce")
                if (peerUid.isBlank() || peerFcm.isBlank() ||
                    peerQrNonce.isBlank()) {
                    _state.value = PairingUiState.Error(
                        "That code didn't work. Try again."
                    )
                    return@launch
                }

                store.storePeer(peerUid, peerFcm)
                WebRtcLog.transition("Pairing: peer stored")

                val ownUid = FirebaseAuth.getInstance()
                    .currentUser?.uid ?: return@launch
                val nonce = deriveSessionNonce(sessionNonce, peerQrNonce)

                writeHandshake(ownUid, peerUid, nonce)

                val peerPresent = withTimeoutOrNull(30_000L) {
                    observeHandshake(ownUid, peerUid, nonce)
                        .first { it }
                } ?: false

                if (peerPresent) {
                    val currentBitmap =
                        (state.value as? PairingUiState.Ready)?.qrBitmap
                    if (currentBitmap != null) {
                        _state.value = PairingUiState.Paired(currentBitmap)
                        delay(1500)
                        _navigationEvent.emit(Unit)
                    }
                } else {
                    _state.value = PairingUiState.Error(
                        "The other device didn't respond. Try again."
                    )
                }
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
