// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/theme/Theme.kt
// Location: app/src/main/java/com/calldad/ui/theme/Theme.kt
package com.calldad.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = CallGreen,
    onPrimary = Color.White,
    secondary = GameBlue,
    onSecondary = Color.White,
    tertiary = PttOrange,
    onTertiary = Color.White,
    background = WarmCream,
    onBackground = InkBlack,
    surface = Color.White,
    onSurface = InkBlack,
    error = HangUpRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = CallGreenLight,
    onPrimary = Color.Black,
    secondary = GameBlueLight,
    onSecondary = Color.Black,
    tertiary = PttOrange,
    onTertiary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    error = HangUpRed,
    onError = Color.White
)

/**
 * Dynamic colour is intentionally DISABLED.
 * A child's mental model ("green means call Dad") must not change
 * because a phone wallpaper changed.
 */
@Composable
fun CallDadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
