// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/GameScreen.kt — game hub: solo, or synced over the live call
// Location: app/src/main/java/com/calldad/ui/screens/GameScreen.kt
package com.calldad.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import com.calldad.BuildConfig
import com.calldad.game.GameWebRtcBridge
import com.calldad.ui.components.GiantButton
import com.calldad.ui.components.VideoRenderer
import com.calldad.ui.theme.GameBlue
import org.json.JSONObject
import java.io.ByteArrayInputStream

private const val ASSET_HOST = "appassets.androidplatform.net"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GameScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = callViewModel()
) {
    BackHandler(onBack = onBack)

    val localTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val inCall by viewModel.canPlayTogether.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)

    val context = LocalContext.current
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(context)
            )
            .build()
    }

    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }

    // Role: in a call the parent flavor is the authority ("caller" in the
    // game protocol); with no call both sides play solo, pass-and-play.
    // Re-sent whenever the page (re)loads or the call state flips.
    LaunchedEffect(webViewRef.value, pageReady, inCall) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        val role = when {
            !inCall -> "solo"
            BuildConfig.APP_THEME == "blue" -> "caller"
            else -> "callee"
        }
        wv.evaluateJavascript("window.setGameRole && window.setGameRole('$role')", null)
    }

    // Inbound: WebRTC data channel → JS. Follows whichever call is live.
    LaunchedEffect(webViewRef.value, pageReady) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        viewModel.gameMessages.collect { json ->
            // JSONObject.quote produces a correctly escaped JS string literal.
            val quoted = JSONObject.quote(json)
            wv.evaluateJavascript(
                "window.receiveRemoteGameState && window.receiveRemoteGameState($quoted)",
                null
            )
        }
    }

    Box(modifier = modifier.fillMaxSize().background(GameBlue)) {

        // ------ Layer 1: the game WebView ------
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        setSupportZoom(false)
                        builtInZoomControls = false
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode =
                            WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    webViewClient = object : WebViewClient() {
                        // Local assets only. Anything else (any other host,
                        // any scheme) gets an empty 403: the game can never
                        // reach the network.
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse =
                            assetLoader.shouldInterceptRequest(request.url)
                                ?: WebResourceResponse(
                                    "text/plain", "utf-8", 403, "Forbidden",
                                    emptyMap(), ByteArrayInputStream(ByteArray(0))
                                )

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean = request.url.host != ASSET_HOST

                        override fun onPageFinished(view: WebView, url: String?) {
                            pageReady = true
                        }
                    }
                    addJavascriptInterface(
                        GameWebRtcBridge { json -> viewModel.sendGameData(json) },
                        "AndroidRTC"
                    )
                    loadUrl("https://$ASSET_HOST/assets/game.html")
                    webViewRef.value = this
                }
            },
            onRelease = { view ->
                view.removeJavascriptInterface("AndroidRTC")
                view.destroy()
                webViewRef.value = null
                pageReady = false
            }
        )

        // ------ Layer 2: floating PiP video card ------
        //
        // CRITICAL: VideoRenderer wraps SurfaceViewRenderer. On Android,
        // SurfaceView lives in a separate compositor layer, so Compose
        // Box ordering does NOT determine z-order — SurfaceFlinger does.
        // VideoRenderer calls setZOrderMediaOverlay(true) internally,
        // which keeps the video above the WebView's surface but still
        // inside the window's view hierarchy. setZOrderOnTop(true) would
        // put it above dialogs and the status bar; do not use that.
        if (inCall) Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(12.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Box(modifier = Modifier.size(width = 120.dp, height = 160.dp)) {
                // Remote fills the card.
                VideoRenderer(
                    track = remoteTrack,
                    eglContext = viewModel.eglContext,
                    mirror = false,
                    modifier = Modifier.fillMaxSize()
                )
                // Local as a small self-view in the corner.
                VideoRenderer(
                    track = localTrack,
                    eglContext = viewModel.eglContext,
                    mirror = true,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(width = 44.dp, height = 60.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
        }

        // ------ Layer 3: persistent escape hatch ------
        GiantButton(
            label = if (inCall) "Back to Call" else "Back to Home",
            icon = if (inCall) Icons.Filled.Call else Icons.Filled.Home,
            containerColor = Color.White,
            contentColor = GameBlue,
            minHeight = 100.dp,
            onClick = { currentOnBack() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        )
    }
}
