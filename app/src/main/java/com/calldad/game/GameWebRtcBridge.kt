// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// game/GameWebRtcBridge.kt — JS → Kotlin game-sync bridge
// Location: app/src/main/java/com/calldad/game/GameWebRtcBridge.kt
package com.calldad.game

import android.webkit.JavascriptInterface
import com.calldad.webrtc.WebRtcLog

/**
 * JS → Kotlin bridge for the mini-game WebView, exposed as window.AndroidRTC.
 *
 * It forwards to whichever call is live at send time ([send] goes through
 * the activity-scoped CallViewModel), so a bridge created before the call
 * connected still works once it does.
 *
 * SECURITY NOTE: addJavascriptInterface injects this object into EVERY
 * frame in the WebView. The WebView serves one local asset and 403s
 * everything else (see GameScreen.kt), so no third-party frame can load.
 */
class GameWebRtcBridge(private val send: (String) -> Boolean) {

    /** Called from game.html as window.AndroidRTC.sendGameStateToDad(JSON.stringify(state)). */
    @JavascriptInterface
    fun sendGameStateToDad(gameStateJson: String) {
        val sent = send(gameStateJson)
        WebRtcLog.transition(if (sent) "Game state TX" else "Game state dropped")
    }
}
