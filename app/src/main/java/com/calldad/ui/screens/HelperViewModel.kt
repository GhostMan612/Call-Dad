// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/HelperViewModel.kt — Phase 9: voice helper (STT → bot → TTS)
// Location: app/src/main/java/com/calldad/ui/screens/HelperViewModel.kt
package com.calldad.ui.screens

import android.app.Application
import android.content.Context
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
import com.calldad.consent.ConsentScope
import com.calldad.consent.ConsentStore
import com.calldad.helper.KeywordBot
import com.calldad.webrtc.WebRtcLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    /**
     * The consent gate for the microphone (ADR-017, [ConsentScope.VOICE]).
     *
     * The Helper is the one feature that was entirely outside the consent model:
     * no `ConsentStore` anywhere in this file, no scope for the mic, and the tile
     * unconditionally on the child's Home grid. So a parent who pressed "Turn
     * everything off" got a screen that told the child nothing is allowed and,
     * one tile over, a working microphone.
     *
     * Absence DENIES, exactly as everywhere else: a fresh install has no
     * `ConsentScope.VOICE` and the Helper says so instead of listening.
     */
    private val consent = ConsentStore()

    private var voiceAllowed = false

    fun start(context: Context, scope: CoroutineScope) {
        consent.start(context, scope)
        scope.launch {
            consent.decisionFor(ConsentScope.VOICE).collect { d ->
                val allowed = d?.isGranted == true
                voiceAllowed = allowed
                if (!allowed) {
                    // Same rule as every other feature: revocation tears down
                    // what is in flight rather than waiting for the next tap.
                    try { recognizer?.cancel() } catch (_: Throwable) {}
                    try { tts?.stop() } catch (_: Throwable) {}
                    _state.update {
                        it.copy(status = HelperStatus.IDLE, error = null)
                    }
                }
            }
        }
    }

    /**
 * Builds the recognizer, ON-DEVICE ONLY, and refuses rather than falling back.
 *
 * This used to prefer the on-device engine and otherwise fall back to
 * `createSpeechRecognizer`, which is the NETWORK recognizer: on a budget phone
 * without an on-device model — a realistic case on both devices in the fleet —
 * a six-year-old's speech was captured and transmitted to the OEM's speech
 * service. Nothing in `consent/` could reach it, no screen said so, and RULES
 * §1.7a records an operator-signed exception for ML Kit (a barcode model fetch)
 * but nothing at all for this. A silent mic upload is exactly the class of thing
 * that sheet exists to prevent.
 *
 * `EXTRA_PREFER_OFFLINE` was not the guarantee it looked like either: it is
 * explicitly ignored by many OEMs from API 33, so the flag below is a hint and
 * the engine choice is the guarantee. There is no network recognizer here at all.
 * A device without on-device recognition gets an honest message instead of a
 * working feature that phones home.
 */
private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val ctx = appContext
        // `isOnDeviceRecognitionAvailable` is API 31+ and minSdk is 26 (ADR-004),
        // so on API 26-30 there is no on-device engine to ask about and therefore
        // nothing we are willing to use. Refusing is the honest answer; the guard
        // is a version check rather than a @RequiresApi because the alternative
        // would be reaching for the network recognizer we are refusing to use.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            WebRtcLog.transition("STT: on-device recognizer needs API 31; refusing mic")
            return null
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
            WebRtcLog.transition("STT: no on-device recognizer; refusing mic")
            return null
        }
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        r.setRecognitionListener(recognitionListener)
        recognizer = r
        usingOnDevice = true
        WebRtcLog.transition("STT: using on-device recognizer")
        return r
    }

    // ---- public API ----

    /** Called when the child taps the giant mic button. */
    fun onTapToSpeak() {
        val current = _state.value.status
        if (current == HelperStatus.LISTENING ||
            current == HelperStatus.SPEAKING) return

        // The kill switch, at the point of use. Checked on the synchronous path
        // because a press can land in the same frame a revocation arrives.
        if (!voiceAllowed) {
            _state.update { it.copy(
                status = HelperStatus.ERROR,
                error = "Ask Helper is turned off right now."
            ) }
            return
        }

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
            // The recognizer is on-device (see [ensureRecognizer]); this is a hint
            // in the same direction, and is a hint only.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        _state.update { it.copy(
            status = HelperStatus.LISTENING,
            error = null,
            lastHeard = null
        ) }
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
                // BOUNDED. This string is the only path by which anything the
                // microphone hears reaches the UI, and it is stored in state,
                // rendered, and passed to TTS. `maxSpeechInputLength` already caps
                // it, but a recognizer is a pluggable OEM component and the cap is
                // a hint, not a contract — an unbounded transcript in a
                // `StateFlow` held by an activity-scoped ViewModel is a memory
                // problem at best and a very large `speak()` argument at worst.
                ?.take(MAX_TRANSCRIPT_CHARS)
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

/**
 * Hard ceiling on a recognised transcript.
 *
 * Generous enough that no real child utterance is ever truncated — a six-year-old
 * speaking continuously runs out of breath well before this — while keeping the
 * worst case bounded. `maxSpeechInputLength` already exists, but the recognizer
 * is an OEM-supplied component and that cap is a request, not a guarantee, and
 * this string is the only path by which anything the microphone hears reaches the
 * UI, the retained ViewModel state, and TTS.
 */
private const val MAX_TRANSCRIPT_CHARS = 2_000

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
