// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// audio/CallAudioManager.kt — Phase 9: incoming-call ringtone + vibration
// Location: app/src/main/java/com/calldad/audio/CallAudioManager.kt
package com.calldad.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.calldad.webrtc.WebRtcLog

/**
 * Owns the incoming-call ringtone and vibration.
 *
 * LIFECYCLE:
 *   start() on CallState.Incoming.
 *   stop()  on InCall, Declined, Ended, or any transition out of Incoming.
 *
 * PRECONDITION: AudioManager.mode must be MODE_NORMAL. If a prior WebRTC
 * call left it in MODE_IN_COMMUNICATION, the ringtone plays as a single
 * short tone and vibration is suppressed by the system. The reset in
 * WebRTCClient.dispose() guarantees this precondition.
 *
 * SINGLE OWNER: the Phase 4 overlay-local ringtone was removed — this is
 * now the only ringer. Two ringers caused the "single TING" class of bug.
 */
class CallAudioManager(private val context: Context) {

    private var ringtone: Ringtone? = null
    private var isPlaying = false

    fun start() {
        if (isPlaying) return
        isPlaying = true

        // ---- ringtone ----
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
            WebRtcLog.transition("Ringtone started")
        } catch (t: Throwable) {
            WebRtcLog.transition("Ringtone start failed")
        }

        // ---- vibration ----
        // Repeating waveform. Index 0 means "repeat from the start".
        // Cancelled by stop().
        try {
            val vibrator = resolveVibrator() ?: return
            if (!vibrator.hasVibrator()) return

            val timings = longArrayOf(0L, 800L, 600L)
            val amplitudes = intArrayOf(0, 180, 0)
            val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(
                        VibrationAttributes.USAGE_RINGTONE
                    )
                )
            } else {
                vibrator.vibrate(effect)
            }
            WebRtcLog.transition("Vibration started")
        } catch (t: Throwable) {
            WebRtcLog.transition("Vibration start failed")
        }
    }

    fun stop() {
        if (!isPlaying) return
        isPlaying = false

        try {
            ringtone?.stop()
        } catch (_: Throwable) { /* ignore */ }
        ringtone = null

        try {
            resolveVibrator()?.cancel()
        } catch (_: Throwable) { /* ignore */ }

        WebRtcLog.transition("Ringtone and vibration stopped")
    }

    private fun resolveVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
