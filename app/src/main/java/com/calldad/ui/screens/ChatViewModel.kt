// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/ChatViewModel.kt — SPEC_SHEET §2.3, BP-03
// Location: app/src/main/java/com/calldad/ui/screens/ChatViewModel.kt
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
import com.calldad.chat.ChatClient
import com.calldad.chat.ChatMessage
import com.calldad.chat.ChatText
import com.calldad.chat.Receipt
import com.calldad.consent.ConsentScope
import com.calldad.consent.ConsentStore
import com.calldad.data.session.FamilyPair
import com.calldad.data.session.FamilySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val justSent: Boolean = false,
    val problem: String? = null,
    /** True once the pair is known, so the screen can say "not paired" honestly. */
    val isPaired: Boolean = false,
    /** The scopes the child currently holds; empty denies, per ADR-017. */
    val scopes: Set<ConsentScope> = emptySet(),
    val ownUid: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val client = ChatClient()

    /**
     * Consent is read LIVE, not fetched once.
     *
     * This was originally missing, and the omission made the entire chat feature
     * unreachable: `scopes` stayed `emptySet()`, so `ChatScreen`'s
     * `ConsentScope.TEXT !in state.scopes` was permanently true and the only thing
     * Messages ever rendered was "Messaging is turned off right now." — a
     * complete, reviewed, green-gated feature that could not be used. `PhotoViewModel`
     * had the same wiring from the start, which is the only reason the bug was
     * in one file and not two.
     */
    private val consent = ConsentStore()

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var startedFor: String? = null

    init {
        consent.start(application, viewModelScope)
        viewModelScope.launch {
            // A parent can withdraw the TEXT grant while the child is mid-
            // sentence, and the thread must close on its own. Reactive, not
            // fetched once at open.
            consent.scopes.collect { held ->
                _state.value = _state.value.copy(scopes = held)
            }
        }
        viewModelScope.launch {
            client.messages.collect { rows ->
                _state.value = _state.value.copy(messages = rows)
            }
        }
        viewModelScope.launch {
            client.problem.collect { p ->
                _state.value = _state.value.copy(problem = p?.message)
            }
        }
        // Pairing is read reactively: a child can be paired while this screen is
        // already open, and a parent can withdraw the TEXT grant while the child
        // is mid-sentence. Neither may need a restart.
        viewModelScope.launch {
            FamilySession.pair(getApplication()).collect { pair -> onPair(pair) }
        }
    }

    private suspend fun onPair(pair: FamilyPair?) {
        if (pair == null) {
            client.stop()
            startedFor = null
            _state.value = _state.value.copy(isPaired = false, ownUid = "", messages = emptyList())
            return
        }
        _state.value = _state.value.copy(isPaired = true, ownUid = pair.ownUid)
        if (startedFor != pair.roomId) {
            startedFor = pair.roomId
            client.start(pair, viewModelScope)
        }
        // DELIBERATELY NOT `markVisibleAsRead()` here.
        //
        // This collector fires on pairing change, which happens on ANY screen —
        // including the Call screen, while the child is in a video call. Marking
        // read from there ticks messages the child never looked at, and the other
        // phone then shows "Seen" for words they never saw. A receipt that lies
        // is worse than no receipt, which is the entire reason receipts here are
        // stamped by the RECEIVER and not on write. Only `ChatScreen` marks read,
        // and only while it is the current destination.
    }

    /** Called when the Chat destination becomes the current destination. */
    fun markVisibleAsRead() {
        client.markVisibleAsRead()
    }

    fun onDraftChanged(value: String) {
        // Cap the field itself, so the send button's failure is never the first
        // time a child learns the limit.
        _state.value = _state.value.copy(
            draft = if (value.length > ChatText.MAX_LENGTH) value.take(ChatText.MAX_LENGTH) else value
        )
    }

    fun onSend() {
        val s = _state.value
        if (s.isSending || s.draft.isBlank()) return
        // One decision: consent and body are validated together, so there is no
        // path that sends a string which never passed the kid-safety check.
        val verdict = ChatText.validate(s.draft, s.scopes)
        when (verdict) {
            is ChatText.Verdict.Rejected -> {
                _state.value = s.copy(
                    problem = verdict.reason.kidMessage,
                    // NOT_ALLOWED means a grown-up withdrew the grant. Clear the
                    // draft: a child repeatedly retyping a message that will
                    // never be allowed is the dead end this app must not have.
                    draft = if (verdict.reason == ChatText.Reason.NOT_ALLOWED) "" else s.draft
                )
            }
            is ChatText.Verdict.Ok -> {
                _state.value = s.copy(isSending = true, problem = null, draft = "")
                viewModelScope.launch {
                    val result = client.send(verdict.text, s.scopes)
                    _state.value = _state.value.copy(
                        isSending = false,
                        justSent = result.isSuccess,
                        problem = null
                    )
                }
            }
        }
    }

    fun clearProblem() {
        client.clearProblem()
        _state.value = _state.value.copy(problem = null)
    }

    /** The tick shown beside a sent message. Never shows "delivered" falsely. */
    fun receiptLabel(m: ChatMessage): String? {
        if (!m.isFromMe(_state.value.ownUid)) return null
        return when (m.receipt) {
            Receipt.SENT -> "Sending"
            Receipt.DELIVERED -> "Got there"
            Receipt.READ -> "Seen"
        }
    }

    override fun onCleared() {
        client.stop()
        super.onCleared()
    }
}

class ChatViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(application) as T
}

/**
 * Activity-scoped, same reasoning as [rememberPttViewModel] and `callViewModel`:
 * a NavBackStackEntry-scoped instance would be a private copy, so the listener
 * would be registered twice and two Firestore snapshots would race to tick the
 * same messages.
 */
@Composable
fun rememberChatViewModel(): ChatViewModel {
    val activity = LocalContext.current as ComponentActivity
    return viewModel(
        viewModelStoreOwner = activity,
        factory = ChatViewModelFactory(activity.application)
    )
}
