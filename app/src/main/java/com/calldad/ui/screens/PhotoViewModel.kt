// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/PhotoViewModel.kt — capture/send/view (BP-04, SPEC_SHEET §2.4)
// Location: app/src/main/java/com/calldad/ui/screens/PhotoViewModel.kt
package com.calldad.ui.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.consent.ConsentScope
import com.calldad.consent.ConsentStore
import com.calldad.data.session.FamilyPair
import com.calldad.data.session.FamilySession
import com.calldad.photos.PhotoClient
import com.calldad.photos.PhotoMessage
import com.calldad.webrtc.WebRtcLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PhotoUiState(
    val photos: List<PhotoMessage> = emptyList(),
    val isPaired: Boolean = false,
    val ownUid: String = "",
    val pair: FamilyPair? = null,
    val scopes: Set<ConsentScope> = emptySet(),
    val isSending: Boolean = false,
    val justSent: Boolean = false,
    val problem: String? = null
)

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val client = PhotoClient()
    private val consent = ConsentStore()

    private val _state = MutableStateFlow(PhotoUiState())
    val state: StateFlow<PhotoUiState> = _state.asStateFlow()

    init {
        client.start(application, viewModelScope)
        consent.start(application, viewModelScope)

        viewModelScope.launch {
            client.photos.collect { _state.value = _state.value.copy(photos = it) }
        }
        viewModelScope.launch {
            client.problem.collect { _state.value = _state.value.copy(problem = it) }
        }
        viewModelScope.launch {
            FamilySession.pair(getApplication()).collect { p ->
                _state.value = _state.value.copy(
                    pair = p,
                    isPaired = p != null,
                    ownUid = p?.ownUid.orEmpty()
                )
            }
        }
        viewModelScope.launch {
            // Consent is read reactively so a parent revoking PHOTO closes the
            // screen on its own, mid-view, without the child needing to restart.
            consent.scopes.collect { held ->
                _state.value = _state.value.copy(scopes = held)
                // The RECEIVE half. Without this the client kept downloading
                // photo chunks on a revoked child's phone while the screen showed
                // nothing — invisible to the user, real on the data plan, and
                // exactly the traffic a parent expects to stop when they switch
                // sharing off. Hiding the grid was never the whole gate.
                client.setInboundAllowed(ConsentScope.PHOTO in held)
            }
        }
    }

    /**
     * Sends one picture.
     *
     * The bitmap is RECYCLED here, in a `finally`, because this is the only place
     * that knows it is no longer needed. A 12MP camera bitmap is ~48MB and the
     * GC is not fast enough to keep up with a child tapping send; the previous
     * camera still holding one plus ours is how a cheap phone runs out of memory
     * and the app dies mid-send.
     */
    fun send(bitmap: Bitmap) {
        val s = _state.value
        val p = s.pair
        if (p == null || s.isSending) {
            bitmap.recycle()
            return
        }
        if (ConsentScope.PHOTO !in s.scopes) {
            // Consent is checked BEFORE the encode, so a revoked parent gets an
            // instant answer rather than a 3-second encode followed by a refusal.
            bitmap.recycle()
            _state.value = s.copy(problem = "Pictures are turned off right now. Ask a grown-up.")
            return
        }
        _state.value = s.copy(isSending = true, problem = null, justSent = false)
        viewModelScope.launch {
            try {
                val result = client.send(p, bitmap, s.scopes)
                _state.value = _state.value.copy(
                    isSending = false,
                    justSent = result.isSuccess,
                    problem = if (result.isSuccess) null
                    else "Couldn't send that picture. Try again."
                )
                if (result.isFailure) WebRtcLog.transition("Photo send failed")
            } finally {
                // Recycle WHERE THE WORK ENDS.
                //
                // This used to be `delay(5_000)` on a separate coroutine, with a
                // KDoc claiming it happened "in a `finally`" — there was no
                // `finally`. A fixed timer is not a synchronisation primitive: a
                // 1080px source on a low-end phone can still be inside
                // `createScaledBitmap`/`compress` after 5s under memory pressure,
                // and recycling then throws into `runCatching`, which turns a
                // perfectly good photo into "Couldn't send that picture. Try again."
                // It also leaked: a ViewModel cleared inside the window cancelled
                // the coroutine and the ~6MB bitmap was never recycled at all.
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    fun clearProblem() {
        client.clearProblem()
        _state.value = _state.value.copy(problem = null)
    }

    override fun onCleared() {
        // `close`, not `stop`: the work scope is a root SupervisorJob that only
        // this can complete, and an in-flight 800KB download would otherwise keep
        // running for the life of the process and publish into a dead StateFlow.
        client.close()
        super.onCleared()
    }
}

class PhotoViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        PhotoViewModel(application) as T
}

@Composable
fun rememberPhotoViewModel(): PhotoViewModel {
    val activity = LocalContext.current as ComponentActivity
    return viewModel(
        viewModelStoreOwner = activity,
        factory = PhotoViewModelFactory(activity.application)
    )
}
