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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.calldad.fcm.CallForegroundService
import com.calldad.navigation.AppNavHost
import com.calldad.ui.theme.CallDadTheme

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        checkFullScreenIntentAccess()
        // Killed-app entry: FSI carries the action only (no callId anywhere
        // in Phase 11 — the static room ID is the route). The overlay
        // validates the room itself and bounces home if nothing is ringing.
        val incomingCall = intent?.action == CallForegroundService.ACTION_INCOMING_CALL
        setContent {
            CallDadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost(incomingCall = incomingCall)
                }
            }
        }
    }

    /**
     * Executor addition (not in architect prompt, required by it): the
     * killed-app path posts a notification on API 33+, which needs a runtime
     * grant. One-shot ask, never blocking startup.
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
     * Full-screen intents can be denied on Android 14+ for apps that don't
     * qualify as "calling or alarm". Informational only — the app still
     * works without FSI permission; it just won't wake a locked screen.
     * Do not block startup on it.
     */
    private fun checkFullScreenIntentAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !nm.canUseFullScreenIntent()
        ) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:$packageName"))
            )
        }
    }
}
