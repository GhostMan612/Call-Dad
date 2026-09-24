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
import com.calldad.data.session.FamilySession
import com.calldad.pairing.ModuleAvailabilityCheck
import com.calldad.pairing.PairingPayload
import com.calldad.pairing.QrGenerator
import com.calldad.pairing.SecurePeerStore
import com.calldad.webrtc.WebRtcLog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface PairingUiState {
    data object Idle : PairingUiState
    data object CheckingModule : PairingUiState
    data object Loading : PairingUiState
    data class Ready(val qrBitmap: android.graphics.Bitmap) : PairingUiState
    /** Scanned the other phone; waiting for it to scan ours (mutual). */
    data class Waiting(val qrBitmap: android.graphics.Bitmap) : PairingUiState
    data class Paired(val qrBitmap: android.graphics.Bitmap) : PairingUiState
    data class Error(val message: String) : PairingUiState
}

/**
 * Mutual QR pairing. Each phone shows {uid, nonce} and scans the other's.
 *
 * The peer is stored ONLY after the handshake proves both scans happened:
 * this phone writes pairings/{ownUid} = {peerUid, sessionNonce} and then
 * watches pairings/{peerUid} (a single-document read, never a query)
 * until it names US with the SAME session nonce. A scanned code alone can
 * never replace the contact, and a failed handshake changes nothing.
 */
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

    private val qrNonce: String = UUID.randomUUID().toString()
    private var qrBitmap: android.graphics.Bitmap? = null
    private var handshakeJob: Job? = null

    init {
        initialize()
    }

    private fun initialize() {
        _state.value = PairingUiState.CheckingModule
        viewModelScope.launch {
            val moduleReady = ModuleAvailabilityCheck.ensureBarcodeModule(appContext)
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
            val uid = withTimeoutOrNull(15_000L) {
                FamilySession.authUid().first { it != null }
            }
            if (uid == null) {
                _state.value = PairingUiState.Error(
                    "This phone isn't online yet. Check Wi-Fi and try again."
                )
                return@launch
            }
            val bitmap = withContext(Dispatchers.Default) {
                QrGenerator.generate(PairingPayload(uid, qrNonce).encode())
            }
            qrBitmap = bitmap
            _state.value = PairingUiState.Ready(bitmap)
        }
    }

    fun onQrScanned(raw: String) {
        val bitmap = qrBitmap ?: return
        if (_state.value !is PairingUiState.Ready) return
        val ownUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val peer = when (val parsed = PairingPayload.parse(raw, ownUid)) {
            is PairingPayload.Parsed.Ok -> parsed.payload
            PairingPayload.Parsed.WrongVersion -> {
                _state.value = PairingUiState.Error("This code is from a different version of the app.")
                return
            }
            PairingPayload.Parsed.OwnCode -> {
                _state.value = PairingUiState.Error("That's this phone's own code. Scan the OTHER phone.")
                return
            }
            PairingPayload.Parsed.Invalid -> {
                _state.value = PairingUiState.Error("That code didn't work. Try again.")
                return
            }
        }

        _state.value = PairingUiState.Waiting(bitmap)
        handshakeJob?.cancel()
        handshakeJob = viewModelScope.launch {
            try {
                val session = PairingPayload.sessionNonce(qrNonce, peer.nonce)
                writeHandshake(ownUid, peer.uid, session)
                val confirmed = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) {
                    observePeerHandshake(peer.uid).first { doc ->
                        doc != null &&
                            doc.getString("peerUid") == ownUid &&
                            doc.getString("sessionNonce") == session &&
                            (doc.getTimestamp("expiresAt")?.toDate()?.time ?: 0L) >
                            System.currentTimeMillis()
                    }
                } != null

                if (!confirmed) {
                    _state.value = PairingUiState.Error(
                        "The other phone didn't finish. Make sure it scans this phone's code too."
                    )
                    return@launch
                }

                store.storePeer(peer.uid)
                WebRtcLog.transition("Pairing: mutual handshake confirmed, peer stored")
                _state.value = PairingUiState.Paired(bitmap)
                delay(PAIRED_HOLD_MS)
                _navigationEvent.emit(Unit)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                WebRtcLog.transition("Pairing failed")
                _state.value = PairingUiState.Error("Pairing didn't work. Check Wi-Fi and try again.")
            }
        }
    }

    private suspend fun writeHandshake(ownUid: String, peerUid: String, session: String) {
        firestore.collection("pairings").document(ownUid).set(
            mapOf(
                "uid" to ownUid,
                "peerUid" to peerUid,
                "sessionNonce" to session,
                "createdAt" to FieldValue.serverTimestamp(),
                "expiresAt" to Timestamp(Date(System.currentTimeMillis() + HANDSHAKE_TTL_MS))
            )
        ).await()
    }

    /** Listener on the peer's single pairing document (by id, never a query). */
    private fun observePeerHandshake(peerUid: String): Flow<DocumentSnapshot?> = callbackFlow {
        val reg = firestore.collection("pairings").document(peerUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() })
            }
        awaitClose { reg.remove() }
    }

    fun retryScanner() {
        handshakeJob?.cancel()
        val bitmap = qrBitmap
        if (bitmap != null) {
            _state.value = PairingUiState.Ready(bitmap)
            _resetTrigger.value = _resetTrigger.value + 1
        } else {
            initialize()
        }
    }

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 90_000L
        const val HANDSHAKE_TTL_MS = 10 * 60_000L
        const val PAIRED_HOLD_MS = 3_500L
    }
}
