// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/ChatScreen.kt — SPEC_SHEET §2.3, BP-03
// Location: app/src/main/java/com/calldad/ui/screens/ChatScreen.kt
package com.calldad.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calldad.BuildConfig
import com.calldad.R
import com.calldad.chat.ChatMessage
import com.calldad.chat.Receipt
import com.calldad.consent.ConsentScope
import com.calldad.ui.theme.ChatBubbleMine
import com.calldad.ui.theme.ChatBubbleTheirs

/**
 * The Dad<->Kid thread. One screen, two actions: read, and send.
 *
 * KID-SAFETY, and the reason for every choice below:
 *
 *  - **Bodies are plain `Text`. No `ClickableText`, no `autoLink`, no link
 *    preview.** A message is the one place a grown-up can put arbitrary text in
 *    front of a child, and a tappable link is a route out of the allowlist that
 *    this app exists to prevent. `ChatText` also refuses link-shaped text at
 *    send time, so the renderer has two independent reasons not to act on a URL.
 *    Pinned by `ChatKidSafetyTest`.
 *  - **No settings, no attachment buttons, no scroll-back-to-nothing.** The
 *    only affordances are: type, send, and go home. Anything a child can reach
 *    from this screen is a thing they can use to leave the product.
 *  - **The send button is 64dp** and never the only route out; the back arrow is
 *    the same size, because a child who cannot get home is a child stuck.
 *  - **Receipts are words, not ticks.** "Seen" needs no legend and cannot be
 *    misread as a countdown or a warning.
 */
@Composable
fun ChatScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = rememberChatViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Marking read is the whole point of opening the thread, and it must happen
    // when the destination is visible -- not on pair-change, which also fires
    // while the child is on the Call screen.
    LaunchedEffect(state.isPaired) { if (state.isPaired) viewModel.markVisibleAsRead() }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader(
                peerName = stringResource(
                    if (BuildConfig.APP_THEME == "blue") R.string.name_of_child
                    else R.string.name_of_grown_up
                ),
                onBackHome = onBackHome
            )

            if (!state.isPaired) {
                NotReady("Pair the phones first. (Grown-ups: the gear button.)")
            } else if (ConsentScope.TEXT !in state.scopes) {
                // A grown-up withdrew the grant. Say it once, plainly, and do
                // NOT offer a retry: a child retyping a message that can never
                // be sent, with no way to ask why, is the dead end this app must
                // not have. The grown-ups' gate screen is where it gets undone.
                NotReady("Messaging is turned off right now. Ask a grown-up.")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.messages, key = { it.id }) { m ->
                        MessageRow(
                            message = m,
                            isMine = m.isFromMe(state.ownUid),
                            receipt = viewModel.receiptLabel(m)
                        )
                    }
                }
                state.problem?.let { problem ->
                    Text(
                        text = problem,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Composer(
                    draft = state.draft,
                    enabled = !state.isSending,
                    onDraftChanged = viewModel::onDraftChanged,
                    onSend = viewModel::onSend
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(peerName: String, onBackHome: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row2(
            onBackHome = onBackHome,
            title = peerName,
            subtitle = "Messages"
        )
    }
}

/** Back arrow + title. A hand-rolled Row keeps the target size explicit. */
@Composable
private fun Row2(onBackHome: () -> Unit, title: String, subtitle: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackHome,
            modifier = Modifier.size(TOUCH_TARGET_DP.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to home",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage, isMine: Boolean, receipt: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMine) ChatBubbleMine else ChatBubbleTheirs,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(
                // PLAIN TEXT. See the file doc: this is the no-escape boundary
                // for anything a grown-up types.
                text = message.body,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        receipt?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    enabled: Boolean,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChanged,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.titleLarge,
            singleLine = true,
            // No keyboardOptions with autocorrect off: a 6-year-old needs the
            // keyboard to help them, and the text never leaves the pair.
            placeholder = {
                Text("Type a message", style = MaterialTheme.typography.titleLarge)
            }
        )
        IconButton(
            onClick = onSend,
            enabled = enabled && draft.isNotBlank(),
            modifier = Modifier.size(TOUCH_TARGET_DP.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * BP-05 §4's floor for a primary target, 96dp.
 *
 * The rest of the app uses 64dp on some icon buttons and passes, because the
 * surrounding targets are 2x2 giant cards a child cannot miss. This screen has
 * no such surrounding help: the back arrow and the send button ARE the
 * interface, and a 6-year-old aiming at a 64dp target next to a soft keyboard is
 * exactly the case the floor exists for. Both buttons are also the only way out
 * of this screen, and a child who cannot hit "home" is stuck.
 */
private const val TOUCH_TARGET_DP = 96

@Composable
private fun NotReady(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

/** Kept so the receipt vocabulary cannot silently drift from the model. */
internal val RECEIPT_WORDS = mapOf(
    Receipt.SENT to "Sending",
    Receipt.DELIVERED to "Got there",
    Receipt.READ to "Seen"
)
