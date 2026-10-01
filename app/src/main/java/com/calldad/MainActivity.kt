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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.calldad.fcm.AppVisibility
import com.calldad.fcm.CallForegroundService
import com.calldad.navigation.AppNavHost
import com.calldad.photos.PhotoPolicy
import com.calldad.ui.screens.rememberPhotoViewModel
import com.calldad.ui.theme.CallDadTheme
import com.calldad.webrtc.WebRtcLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /**
     * Photo picking (SPEC_SHEET §2.4).
     *
     * `PickVisualMedia` is the system photo picker, so the app is granted a
     * ONE-OFF read of exactly the picture a person chose and holds no gallery
     * permission, no MediaStore handle, and no `READ_MEDIA_IMAGES`. On a child's
     * phone that is the whole point: a 6-year-old cannot browse the camera roll
     * from inside this app, and nothing here can hand the gallery a path to.
     *
     * The permissionless picker is also why there is no `READ_MEDIA_IMAGES` in
     * the manifest — asking for it would be a way to see every photo on the
     * device, which is the exact capability this app must not have.
     */
    /**
     * Bumped when the Photo screen asks for a picture; the launcher below watches
     * it. A counter rather than a boolean, so two taps cannot collapse into one
     * launch.
     */
    private val photoPickRequests = mutableIntStateOf(0)

    /**
     * Decoded, downscaled to the policy's cap, and the original released.
     *
     * `inSampleSize` is the part worth explaining: it must be a power of two, so
     * the sample rounds DOWN and leaves the bitmap slightly LARGER than the
     * policy cap. `createScaledBitmap` then does the exact resize, so the
     * oversample is thrown away rather than being an upscale. Sampling UP would
     * discard real pixels the encoder would then be stretching.
     */
    private suspend fun decodePicked(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching null
            // inSampleSize must be a power of two, so round DOWN: a 1.3x
            // oversample is harmless, a 1.3x undersample loses real pixels the
            // WEBP encoder would then be scaling up.
            val sample = generateSequence(1) { it * 2 }
                .first { maxOf(bounds.outWidth / it, bounds.outHeight / it) <= PhotoPolicy.MAX_EDGE * 2 }
            val full = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                })
            } ?: return@runCatching null
            val (tw, th) = PhotoPolicy.targetSize(full.width, full.height)
            val out = Bitmap.createScaledBitmap(full, tw, th, true)
            if (out !== full) full.recycle()
            out
        }.getOrNull()
    }

    private var incomingCallRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // K21, operator-found: while a call rang, the app kept drawing over the
        // lock screen and the grown-up could not get past it to unlock their own
        // phone. An activity that opts into showWhenLocked/turnScreenOn keeps
        // showing OVER the keyguard once launched, so we never opt in and let
        // the platform handle a locked screen. API 27+ against a minSdk of 26,
        // hence the guard -- lint flagged the unguarded form as a crash on 8.0.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
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
                    val photoVm = rememberPhotoViewModel()
                    val pickRequests by photoPickRequests

                    val picker = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickVisualMedia()
                    ) { uri ->
                        if (uri == null) return@rememberLauncherForActivityResult
                        // Off the main thread: a 12MP JPEG is ~200ms and a child
                        // is watching this screen.
                        lifecycleScope.launch {
                            val bmp = decodePicked(uri)
                            if (bmp != null) photoVm.send(bmp)
                        }
                    }

                    // Watch the counter rather than passing a launcher through the
                    // nav graph: the nav graph should not know how a picture gets
                    // chosen, only that the child asked for one.
                    LaunchedEffect(pickRequests) {
                        if (pickRequests > 0) {
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    }

                    AppNavHost(
                        incomingCallRequest = incomingCallRequest,
                        onPickPhoto = { photoPickRequests.value++ }
                    )
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
