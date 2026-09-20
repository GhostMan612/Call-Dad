// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ptt/PttAudioManager.kt — Phase 6: audio focus + haptics owner
// Location: app/src/main/java/com/calldad/ptt/PttAudioManager.kt
package com.calldad.ptt

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.calldad.webrtc.WebRtcLog

/**
 * Owns Android's audio focus and hardware feedback for PTT.
 *
 * AUDIO FOCUS:
 *   AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE — we want the system to pause
 *   notifications and other media for the duration of a transmission.
 *   This is the correct hint for PTT; AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
 *   would let other apps keep playing quietly, which is wrong for a
 *   walkie-talkie.
 *
 * INTERLOCK:
 *   The ViewModel calls `setCallActive(true)` when CallState enters InCall.
 *   While set, requestFocus() returns PttFailureKind.AUDIO_CONTENDED
 *   without touching the AudioManager. WebRTC owns the mic during a call;
 *   PTT must yield.
 *
 * HAPTICS:
 *   Short 40ms vibration on press, 25ms on release. The AudioManager chirp
 *   is left as a Phase 7 TODO (requires a bundled raw resource).
 */
class PttAudioManager(private val context: Context) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile private var callActive: Boolean = false
    @Volatile private var focusRequest: AudioFocusRequest? = null
    @Volatile private var focusGranted: Boolean = false

    /** Called by PttViewModel on every CallState change. */
    fun setCallActive(active: Boolean) {
        callActive = active
        if (active && focusGranted) {
            // Defensive: if a call started mid-transmission, drop focus.
            abandonFocus()
        }
    }

    /**
     * Requests transient-exclusive audio focus.
     *
     * @return null on success, or a PttFailure describing why focus was
     *         not granted.
     */
    fun requestFocus(): PttFailure? {
        if (callActive) {
            return PttFailure(
                PttFailureKind.AUDIO_CONTENDED,
                "You're on a call. Finish the call first."
            )
        }
        if (focusGranted) return null

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val request = AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
        )
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { change ->
                WebRtcLog.transition("PTT audio focus change: $change")
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    // The VM is responsible for actually stopping TX.
                    // We only flip our internal flag so the next request
                    // re-acquires cleanly.
                    focusGranted = false
                }
            }
            .build()

        val result = audioManager.requestAudioFocus(request)
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequest = request
            focusGranted = true
            WebRtcLog.transition("PTT audio focus granted")
            null
        } else {
            focusGranted = false
            WebRtcLog.transition("PTT audio focus denied")
            PttFailure(
                PttFailureKind.AUDIO_FOCUS_LOST,
                "Something else is using the speaker. Try again."
            )
        }
    }

    fun abandonFocus() {
        val request = focusRequest ?: return
        audioManager.abandonAudioFocusRequest(request)
        focusRequest = null
        focusGranted = false
        WebRtcLog.transition("PTT audio focus abandoned")
    }

    // -------- haptics --------

    fun vibratePress() = vibrate(40L)
    fun vibrateRelease() = vibrate(25L)

    private fun vibrate(durationMs: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}
