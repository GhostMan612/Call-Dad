// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/GameScreen.kt — Phase 7: hardened WebView + data-channel sync
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.calldad.game.GameWebRtcBridge
import com.calldad.ui.components.GiantButton
import com.calldad.ui.theme.GameBlue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Mini-game host with WebRTC data-channel sync.
 *
 * WEBVIEW HARDENING (all four are load-bearing):
 *   - WebViewAssetLoader serves assets over https://appassets.androidplatform.net
 *     instead of file://. This eliminates the file-scheme cross-origin class
 *     of vulnerabilities flagged by the setAllowFileAccessFromFileURLs
 *     deprecation.
 *   - allowFileAccess = false
 *   - allowContentAccess = false
 *   - mixedContentMode = MIXED_CONTENT_NEVER_ALLOW
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GameScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CallViewModel = callViewModel()
) {
    BackHandler(onBack = onBackHome)

    val webrtc = viewModel.webrtcClientOrNull()
    val currentOnBackHome by rememberUpdatedState(onBackHome)

    // Asset loader is created once per composition. The domain MUST match
    // the loadUrl host below.
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

    // Inbound: WebRTC → JS. Copy is safe; JSONObject.quote is the canonical
    // JS string literal encoder — do NOT use manual quote escaping.
    DisposableEffect(webrtc) {
        val wv = webViewRef.value
        val client = webrtc
        if (wv == null || client == null) return@DisposableEffect onDispose { }

        val handle = CoroutineScope(Dispatchers.Main).launch {
            client.gameSyncMessages.collect { json ->
                val quoted = JSONObject.quote(json)
                wv.evaluateJavascript(
                    "window.receiveRemoteGameState($quoted)",
                    null
                )
            }
        }
        onDispose { handle.cancel() }
    }

    Box(modifier = modifier.fillMaxSize()) {
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
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? =
                            assetLoader.shouldInterceptRequest(request.url)

                        // Block any navigation away from the asset host.
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

        GiantButton(
            label = "Back to Home",
            icon = Icons.Filled.Home,
            containerColor = Color.White,
            contentColor = GameBlue,
            minHeight = 120.dp,
            onClick = { currentOnBackHome() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        )
    }
}
