// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/screens/GameScreen.kt
// Location: app/src/main/java/com/calldad/app/ui/screens/GameScreen.kt
package com.calldad.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.calldad.app.ui.components.GiantButton
import com.calldad.app.ui.theme.CallDadTheme
import com.calldad.app.ui.theme.GameBlue

/**
 * Full-screen HTML5 game host — Phase 1 placeholder.
 *
 * Two independent escape hatches (deliberate redundancy, because a child
 * WILL get stuck otherwise):
 *   1. The persistent floating "Home" button, pinned bottom-centre, above
 *      whatever the WebView will eventually render.
 *   2. [BackHandler] — the system back gesture also routes home instead of
 *      silently popping to an unexpected screen.
 */
@Composable
fun GameScreen(
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackHome)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GameBlue)
    ) {
        // ------------------------------------------------------------------
        // PHASE 2 HOOK — replace this placeholder Column with the game host:
        //
        // AndroidView(
        //     factory = { context ->
        //         WebView(context).apply {
        //             settings.javaScriptEnabled = true
        //             settings.domStorageEnabled = true
        //             settings.mediaPlaybackRequiresUserGesture = false
        //             webViewClient = WebViewClient()
        //             loadUrl(GAME_URL)
        //         }
        //     },
        //     modifier = Modifier.fillMaxSize()
        // )
        //
        // NOTE: keep `settings.setSupportZoom(false)` and disable overscroll
        // so the child cannot pinch/scroll their way into a broken viewport.
        // ------------------------------------------------------------------
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.SportsEsports,
                contentDescription = null,
                modifier = Modifier.size(140.dp),
                tint = Color.White
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Game Time!",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "The game will appear here",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }

        // ---------- PERSISTENT ESCAPE HATCH ----------
        GiantButton(
            label = "Back to Home",
            icon = Icons.Filled.Home,
            containerColor = Color.White,
            contentColor = GameBlue,
            minHeight = 120.dp,
            onClick = onBackHome,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun GameScreenPreview() {
    CallDadTheme {
        GameScreen(onBackHome = {})
    }
}
