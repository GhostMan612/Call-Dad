// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HelperViewModel.kt — Phase 9: voice helper (STT → bot → TTS)
// Location: app/src/main/java/com/calldad/ui/screens/HelperViewModel.kt
package com.calldad.ui.screens

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calldad.helper.KeywordBot
import com.calldad.webrtc.WebRtcLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

enum class HelperStatus { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

data class HelperUiState(
    val status: HelperStatus = HelperStatus.IDLE,
    val lastHeard: String? = null,
    val lastSpoken: String? = null,
    val error: String? = null
)

class HelperViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext

    private val _state = MutableStateFlow(HelperUiState())
    val state: StateFlow<HelperUiState> = _state.asStateFlow()

    // ---- TTS ----
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(utteranceListener)
                ttsReady = true
                WebRtcLog.transition("TTS ready")
            } else {
                WebRtcLog.transition("TTS init failed")
            }
        }
    }

    // ---- STT ----
    private var recognizer: SpeechRecognizer? = null
    private var usingOnDevice = false
    private var onDeviceUnavailable = false
    private var lastIntent: Intent? = null

    /**
     * Builds the recognizer, preferring the on-device engine when available.
     * On API 31+ with a device that supports it, this runs fully offline.
     * Otherwise it falls back to the network recognizer with
     * EXTRA_PREFER_OFFLINE set — which is best-effort, not guaranteed.
     */
    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val ctx = appContext
        val r = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            && !onDeviceUnavailable
            && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
            WebRtcLog.transition("STT: using on-device recognizer")
            usingOnDevice = true
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        } else {
            usingOnDevice = false
            WebRtcLog.transition("STT: using default recognizer")
            SpeechRecognizer.createSpeechRecognizer(ctx)
        }
        r.setRecognitionListener(recognitionListener)
        recognizer = r
        return r
    }

    // ---- public API ----

    /** Called when the child taps the giant mic button. */
    fun onTapToSpeak() {
        val current = _state.value.status
        if (current == HelperStatus.LISTENING ||
            current == HelperStatus.SPEAKING) return

        val r = ensureRecognizer() ?: run {
            _state.update { it.copy(
                status = HelperStatus.ERROR,
                error = "Voice isn't available on this device."
            ) }
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Best-effort hint. Ignored by many OEMs on API 33+. The
            // on-device recognizer path above is the real guarantee.
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        _state.update { it.copy(
            status = HelperStatus.LISTENING,
            error = null,
            lastHeard = null
        ) }
        lastIntent = intent
        try {
            r.startListening(intent)
        } catch (t: Throwable) {
            _state.update { it.copy(
                status = HelperStatus.ERROR,
                error = "Couldn't start listening."
            ) }
        }
    }

    fun clearError() {
        _state.update { it.copy(status = HelperStatus.IDLE, error = null) }
    }

    /** Leaving the screen: mic off, voice off, back to a tappable button. */
    fun stopAll() {
        try { recognizer?.cancel() } catch (_: Throwable) {}
        try { tts?.stop() } catch (_: Throwable) {}
        _state.update { it.copy(status = HelperStatus.IDLE, error = null) }
    }

    override fun onCleared() {
        try { recognizer?.stopListening() } catch (_: Throwable) {}
        try { recognizer?.cancel() } catch (_: Throwable) {}
        // MUST be called on the main thread. onCleared() runs on the main
        // thread by contract, so this is safe.
        try { recognizer?.destroy() } catch (_: Throwable) {}
        recognizer = null

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {}
        tts = null
        super.onCleared()
    }

    // ---- listeners ----

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            WebRtcLog.transition("STT: ready")
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _state.update { it.copy(status = HelperStatus.THINKING) }
        }

        override fun onError(error: Int) {
            WebRtcLog.transition("STT error code: $error")
            val languageMissing =
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
            val retryIntent = lastIntent
            if (usingOnDevice && languageMissing && retryIntent != null) {
                WebRtcLog.transition("STT: on-device language missing, using default")
                onDeviceUnavailable = true
                try { recognizer?.destroy() } catch (_: Throwable) {}
                recognizer = null
                val fallback = ensureRecognizer()
                if (fallback != null) {
                    try {
                        fallback.startListening(retryIntent)
                        return
                    } catch (_: Throwable) {}
                }
            }
            _state.update { it.copy(
                status = HelperStatus.ERROR,
                error = "I didn't hear you. Tap and try again."
            ) }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (text.isNullOrEmpty()) {
                _state.update { it.copy(
                    status = HelperStatus.ERROR,
                    error = "I didn't hear you. Tap and try again."
                ) }
                return
            }
            val response = KeywordBot.getResponse(text)
            _state.update { it.copy(
                status = HelperStatus.SPEAKING,
                lastHeard = text,
                lastSpoken = response
            ) }
            speak(response)
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {
            _state.update { it.copy(status = HelperStatus.IDLE) }
        }
        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            _state.update { it.copy(status = HelperStatus.IDLE) }
        }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            _state.update { it.copy(
                status = HelperStatus.ERROR,
                error = "My voice got stuck. Tap and try again."
            ) }
        }
    }

    private fun speak(text: String) {
        val engine = tts
        if (!ttsReady || engine == null) {
            _state.update { it.copy(
                status = HelperStatus.ERROR,
                error = "My voice isn't ready yet."
            ) }
            return
        }
        val id = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result != TextToSpeech.SUCCESS) {
            _state.update { it.copy(
                status = HelperStatus.ERROR,
                error = "My voice got stuck."
            ) }
        }
    }
}

/** Manual factory: AndroidViewModel has no zero-arg constructor. */
class HelperViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HelperViewModel(application) as T
    }
}

/**
 * Shared ACTIVITY-scoped accessor (same doctrine as rememberPttViewModel):
 * the default viewModel() factory cannot build an AndroidViewModel, and
 * per-destination instances would split state. Single definition, all
 * callers share it. See ADR-008.
 */
@Composable
fun rememberHelperViewModel(): HelperViewModel {
    // LocalContext cast, NOT LocalActivity (unresolved in activity-compose
    // 1.9.3 — device-proven in Phase 6). Same shared activity scope.
    val activity = LocalContext.current as ComponentActivity
    return viewModel(
        viewModelStoreOwner = activity,
        factory = HelperViewModelFactory(activity.application)
    )
}
