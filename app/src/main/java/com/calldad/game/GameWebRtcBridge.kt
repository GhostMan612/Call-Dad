// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// game/GameWebRtcBridge.kt — Phase 7: JS → Kotlin game-sync bridge
// Location: app/src/main/java/com/calldad/game/GameWebRtcBridge.kt
package com.calldad.game

import android.webkit.JavascriptInterface
import com.calldad.webrtc.WebRTCClient
import com.calldad.webrtc.WebRtcLog

/**
 * JS → Kotlin bridge for the mini-game WebView.
 *
 * EXPOSED AS: window.AndroidRTC
 *
 * SECURITY NOTE: addJavascriptInterface injects this object into EVERY
 * frame in the WebView, including iframes. The WebView is locked down to
 * a single local asset (see GameScreen.kt), so no third-party frames can
 * load. If that ever changes, this bridge must be replaced with
 * WebViewCompat.postWebMessage.
 */
class GameWebRtcBridge(private val webrtc: WebRTCClient) {

    /**
     * Called from game.html as:
     *     window.AndroidRTC.sendGameStateToDad(JSON.stringify(state))
     */
    @JavascriptInterface
    fun sendGameStateToDad(gameStateJson: String) {
        val sent = webrtc.sendGameData(gameStateJson)
        // Log presence, not content. Never log the JSON payload.
        WebRtcLog.transition(if (sent) "Game state TX" else "Game state dropped")
    }
}
