// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HelperScreen.kt
// Location: app/src/main/java/com/calldad/ui/screens/HelperScreen.kt
package com.calldad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.ui.components.GiantIconButton
import com.calldad.ui.theme.CallDadTheme
import com.calldad.ui.theme.HelperPurple
import com.calldad.ui.theme.HelperPurpleLight
import com.calldad.ui.theme.InkBlack

@Composable
fun HelperScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HelperViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val quickAsks by viewModel.quickAsks.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()

    HelperContent(
        messages = messages,
        quickAsks = quickAsks,
        draft = draft,
        isThinking = isThinking,
        onDraftChange = viewModel::onDraftChange,
        onAsk = viewModel::onAsk,
        onBackHome = onBackHome,
        modifier = modifier
    )
}

/**
 * LLM BOT PLACEHOLDER.
 *
 * Typing is minimised by design: the primary input path is a wall of
 * >= 72dp "Quick Ask" chips. The text field exists only for a parent
 * co-piloting the conversation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HelperContent(
    messages: List<ChatMessage>,
    quickAsks: List<String>,
    draft: String,
    isThinking: Boolean,
    onDraftChange: (String) -> Unit,
    onAsk: (String) -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HelperPurpleLight)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- HEADER ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GiantIconButton(
                icon = Icons.Filled.Home,
                contentDescription = "Back to home",
                containerColor = HelperPurple,
                contentColor = Color.White,
                size = 100.dp,
                onClick = onBackHome
            )
            Spacer(Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = HelperPurple
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Ask Helper",
                style = MaterialTheme.typography.headlineMedium,
                color = HelperPurple
            )
        }

        // ---------- TRANSCRIPT ----------
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(items = messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }
            if (isThinking) {
                item(key = "thinking") {
                    Text(
                        text = "Helper is thinking…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HelperPurple.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        // ---------- QUICK ASK CHIPS ----------
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            quickAsks.forEach { question ->
                QuickAskChip(
                    text = question,
                    enabled = !isThinking,
                    onClick = { onAsk(question) }
                )
            }
        }

        // ---------- OPTIONAL KEYBOARD FALLBACK ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 72.dp),
                placeholder = {
                    Text("Type here…", style = MaterialTheme.typography.bodyLarge)
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            GiantIconButton(
                icon = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send question",
                containerColor = HelperPurple,
                contentColor = Color.White,
                size = 100.dp,
                onClick = { onAsk(draft) }
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isFromChild = message.fromChild
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromChild) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (isFromChild) HelperPurple else Color.White)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFromChild) Color.White else InkBlack
            )
        }
    }
}

@Composable
private fun QuickAskChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(36.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(Color.White)
            .border(
                width = 3.dp,
                color = if (enabled) HelperPurple else HelperPurple.copy(alpha = 0.4f),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) HelperPurple else HelperPurple.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun HelperContentPreview() {
    CallDadTheme {
        HelperContent(
            messages = listOf(
                ChatMessage(0L, "Hi! I'm Helper. Tap a button and I'll answer!", false),
                ChatMessage(1L, "Tell me a joke", true),
                ChatMessage(2L, "Why did the teddy bear say no to dessert?", false)
            ),
            quickAsks = listOf("How do I play?", "Tell me a joke", "What is a fun fact?"),
            draft = "",
            isThinking = false,
            onDraftChange = {},
            onAsk = {},
            onBackHome = {}
        )
    }
}
