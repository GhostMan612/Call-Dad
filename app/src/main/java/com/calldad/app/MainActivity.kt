// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// MainActivity.kt
// Location: app/src/main/java/com/calldad/app/MainActivity.kt
package com.calldad.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.calldad.app.navigation.AppNavHost
import com.calldad.app.ui.theme.CallDadTheme

/**
 * Single-activity host. All UI is Compose; all navigation is Navigation-Compose.
 *
 * Deliberate omissions:
 *  - No dynamic colour  -> a 6-year-old needs a stable, predictable colour language.
 *  - No splash / onboarding -> fewer taps between launch and "Call Dad".
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CallDadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
