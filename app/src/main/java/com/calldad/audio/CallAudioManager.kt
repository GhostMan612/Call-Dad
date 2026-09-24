// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// audio/CallAudioManager.kt — incoming ring, vibration, outgoing ringback
// Location: app/src/main/java/com/calldad/audio/CallAudioManager.kt
package com.calldad.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.calldad.webrtc.WebRtcLog

/**
 * Process-wide SINGLE ringer. The in-app call screen and the killed-app
 * foreground service both drive it; every call is idempotent, so the two
 * paths can overlap without double-ringing (the "single TING" class of bug
 * came from two independent ringers).
 *
 *   startRinging()  incoming ring: looping ringtone + repeating vibration
 *   startRingback() outgoing "calling…" tone for the caller
 *   stop()          stops whichever is playing
 */
object CallAudioManager {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var ringback: ToneGenerator? = null

    @Synchronized
    fun startRinging(context: Context) {
        if (ringtone != null || vibrator != null) return
        stopRingbackLocked()
        val app = context.applicationContext

        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(app, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
            WebRtcLog.transition("Ringtone started")
        }.onFailure { WebRtcLog.transition("Ringtone start failed") }

        runCatching {
            val v = resolveVibrator(app) ?: return@runCatching
            if (!v.hasVibrator()) return@runCatching
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0L, 800L, 600L), intArrayOf(0, 180, 0), 0
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect)
            }
            vibrator = v
        }.onFailure { WebRtcLog.transition("Vibration start failed") }
    }

    @Synchronized
    fun startRingback() {
        if (ringback != null) return
        stopRingingLocked()
        runCatching {
            ringback = ToneGenerator(AudioManager.STREAM_VOICE_CALL, RINGBACK_VOLUME).also {
                it.startTone(ToneGenerator.TONE_SUP_RINGTONE)
            }
        }.onFailure {
            ringback = null
            WebRtcLog.transition("Ringback start failed")
        }
    }

    @Synchronized
    fun stop() {
        stopRingingLocked()
        stopRingbackLocked()
    }

    private fun stopRingingLocked() {
        if (ringtone == null && vibrator == null) return
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        WebRtcLog.transition("Ringtone and vibration stopped")
    }

    private fun stopRingbackLocked() {
        val tg = ringback ?: return
        runCatching { tg.stopTone() }
        runCatching { tg.release() }
        ringback = null
    }

    private fun resolveVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private const val RINGBACK_VOLUME = 60
}
