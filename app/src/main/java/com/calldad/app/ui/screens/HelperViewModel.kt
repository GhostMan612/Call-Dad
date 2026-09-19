// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HelperViewModel.kt
// Location: app/src/main/java/com/calldad/app/ui/screens/HelperViewModel.kt
package com.calldad.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long,
    val text: String,
    val fromChild: Boolean
)

class HelperViewModel : ViewModel() {

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = 0L,
                text = "Hi! I'm Helper. Tap a button and I'll answer!",
                fromChild = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _quickAsks = MutableStateFlow(
        listOf(
            "How do I play?",
            "Tell me a joke",
            "What is a fun fact?",
            "Tell me a story"
        )
    )
    val quickAsks: StateFlow<List<String>> = _quickAsks.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private var nextId = 1L

    fun onDraftChange(value: String) {
        _draft.value = value
    }

    fun onAsk(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _isThinking.value) return

        val childMessageId = nextId++
        _draft.value = ""
        _messages.update { it + ChatMessage(childMessageId, trimmed, fromChild = true) }
        _isThinking.value = true

        // PHASE 2: replace `cannedReply` with the real LLM call.
        viewModelScope.launch {
            delay(800)
            val replyId = nextId++
            _messages.update { it + ChatMessage(replyId, cannedReply(trimmed), fromChild = false) }
            _isThinking.value = false
        }
    }

    private fun cannedReply(question: String): String = when {
        question.contains("joke", ignoreCase = true) ->
            "Why did the teddy bear say no to dessert? Because she was stuffed!"
        question.contains("play", ignoreCase = true) ->
            "Tap the blue Play Games card on the home screen. Tap Home to come back!"
        question.contains("fact", ignoreCase = true) ->
            "Octopuses have three hearts. That's three times as many as you!"
        question.contains("story", ignoreCase = true) ->
            "Once upon a time, a tiny robot learned to say hello…"
        else -> "That's a great question! Let's find out together."
    }
}
