// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/GameScreen.kt — Phase 8: game + PiP video card
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import com.calldad.game.GameWebRtcBridge
import com.calldad.ui.components.GiantButton
import com.calldad.ui.components.VideoRenderer
import com.calldad.ui.theme.GameBlue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GameScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = callViewModel()
) {
    BackHandler(onBack = onBackHome)

    val webrtc = viewModel.webrtcClientOrNull()
    val eglContext by viewModel.eglContext.collectAsStateWithLifecycle()
    val localTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val callState by viewModel.state.collectAsStateWithLifecycle()
    val currentOnBackHome by rememberUpdatedState(onBackHome)

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

    // Push role to the JS layer once the WebView and call state are ready.
    // The caller is authoritative in the Tic-Tac-Toe protocol.
    LaunchedEffect(webViewRef.value, callState) {
        val wv = webViewRef.value ?: return@LaunchedEffect
        val role = when (val s = callState) {
            is CallState.InCall -> if (s.role == CallRole.CALLER) "caller" else "callee"
            else -> return@LaunchedEffect
        }
        wv.evaluateJavascript("window.setGameRole && window.setGameRole('$role')", null)
    }

    // Inbound: WebRTC → JS.
    DisposableEffect(webrtc, webViewRef.value) {
        val wv = webViewRef.value
        val client = webrtc
        if (wv == null || client == null) return@DisposableEffect onDispose { }

        val scope = CoroutineScope(Dispatchers.Main)
        val job = scope.launch {
            client.gameSyncMessages.collect { json ->
                // JSONObject.quote produces a correctly escaped JS string
                // literal. Do NOT hand-roll quote escaping.
                val quoted = JSONObject.quote(json)
                wv.evaluateJavascript(
                    "window.receiveRemoteGameState && window.receiveRemoteGameState($quoted)",
                    null
                )
            }
        }
        onDispose { job.cancel() }
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
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? =
                            assetLoader.shouldInterceptRequest(request.url)

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean =
                            request.url.host != "appassets.androidplatform.net"
                    }
                    webrtc?.let {
                        addJavascriptInterface(GameWebRtcBridge(it), "AndroidRTC")
                    }
                    loadUrl("https://appassets.androidplatform.net/assets/game.html")
                    webViewRef.value = this
                }
            },
            onRelease = { view ->
                view.removeJavascriptInterface("AndroidRTC")
                view.destroy()
                webViewRef.value = null
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
        Card(
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
                    eglContext = eglContext,
                    mirror = false,
                    modifier = Modifier.fillMaxSize()
                )
                // Local as a small self-view in the corner.
                VideoRenderer(
                    track = localTrack,
                    eglContext = eglContext,
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
            label = "Back to Home",
            icon = Icons.Filled.Home,
            containerColor = Color.White,
            contentColor = GameBlue,
            minHeight = 100.dp,
            onClick = { currentOnBackHome() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        )
    }
}
