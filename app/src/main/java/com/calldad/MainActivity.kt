// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// MainActivity.kt — Phase 5: FSI routing + notification permission
// Location: app/src/main/java/com/calldad/MainActivity.kt
package com.calldad

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.calldad.fcm.AppVisibility
import com.calldad.fcm.CallForegroundService
import com.calldad.navigation.AppNavHost
import com.calldad.ui.theme.CallDadTheme
import com.calldad.webrtc.WebRtcLog

/**
 * Single-activity host. All UI is Compose; all navigation is Navigation-Compose.
 *
 * Deliberate omissions:
 *  - No dynamic colour  -> a 6-year-old needs a stable, predictable colour language.
 *  - No splash / onboarding -> fewer taps between launch and "Call Dad".
 */
class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var incomingCallRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        checkFullScreenIntentAccessOnce()
        // Killed-app entry (ring notification). Only on a genuine launch:
        // a recreated activity must not replay the old intent.
        if (savedInstanceState == null) consumeIncomingIntent(intent)
        setContent {
            CallDadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost(incomingCallRequest = incomingCallRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isForeground = true
    }

    override fun onStop() {
        AppVisibility.isForeground = false
        super.onStop()
    }

    private fun consumeIncomingIntent(intent: Intent?) {
        // ACTION_RING_NOTIFICATION is the heads-up fallback posted by
        // CallMessagingService when the foreground-service path is
        // unavailable (downgraded priority, or the platform refusing the
        // start). It must route to the ring screen exactly like the real
        // notification does, or the kid taps "Incoming call" and lands on
        // Home with no explanation.
        if (intent?.action == CallForegroundService.ACTION_INCOMING_CALL ||
            intent?.action == ACTION_RING_FALLBACK
        ) {
            incomingCallRequest += 1
        }
    }

    /**
     * The killed-app path posts a notification on API 33+, which needs a
     * runtime grant. One-shot ask, never blocking startup.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Full-screen intents can be denied on Android 14+. Without them a ring
     * still shows as a heads-up notification, it just won't wake a locked
     * screen — so the app degrades gracefully and there is nothing to fix.
     *
     * This used to open system Settings on first launch, which was the ONLY
     * startActivity in the entire app and therefore the only route out of the
     * sandbox: a 6-year-old tapping the launcher icon was dropped into system
     * Settings with no gate and no warning. It is deliberately NOT navigated
     * to. A grown-up who wants the FSI permission sets it from the app's
     * notification settings; the kid never needs to leave the app.
     */
    private fun checkFullScreenIntentAccessOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.canUseFullScreenIntent()) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FSI_ASKED, false)) return
        prefs.edit().putBoolean(KEY_FSI_ASKED, true).apply()
        WebRtcLog.transition("Full-screen intent not granted; using heads-up")
    }

    private companion object {
        const val PREFS = "app_prefs"
        const val KEY_FSI_ASKED = "fsi_asked"

        /** Mirrors CallMessagingService's fallback notification action. */
        const val ACTION_RING_FALLBACK = "com.calldad.RING_NOTIFICATION"
    }
}
