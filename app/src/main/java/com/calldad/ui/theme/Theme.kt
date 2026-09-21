// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/theme/Theme.kt — Phase 10: flavor-driven palettes, dynamic OFF
// Location: app/src/main/java/com/calldad/ui/theme/Theme.kt
package com.calldad.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.calldad.BuildConfig

// ---- Flavor-agnostic schemes -------------------------------------------
// These are the fallback if APP_THEME is unrecognized. They match the
// Phase 1 defaults so a misconfigured build still renders.

private val DefaultLightColors = lightColorScheme(
    primary = CallGreen,
    onPrimary = Color.White,
    background = WarmCream,
    onBackground = InkBlack,
    surface = Color.White,
    onSurface = InkBlack,
    error = HangUpRed,
    onError = Color.White
)

// ---- Parent (Dad) — Light Blue -----------------------------------------

private val ParentLightColors = lightColorScheme(
    primary = DadBluePrimary,
    onPrimary = Color.White,
    primaryContainer = DadBlueContainer,
    onPrimaryContainer = DadBlueOnContainer,
    secondary = GameBlue,
    onSecondary = Color.White,
    tertiary = PttOrange,
    onTertiary = Color.White,
    background = DadBlueBackground,
    onBackground = InkBlack,
    surface = Color.White,
    onSurface = InkBlack,
    error = HangUpRed,
    onError = Color.White
)

// ---- Child (Daughter) — Light Pink -------------------------------------

private val ChildLightColors = lightColorScheme(
    primary = ChildPinkPrimary,
    onPrimary = Color.White,
    primaryContainer = ChildPinkContainer,
    onPrimaryContainer = ChildPinkOnContainer,
    secondary = GameBlue,
    onSecondary = Color.White,
    tertiary = PttOrange,
    onTertiary = Color.White,
    background = ChildPinkBackground,
    onBackground = InkBlack,
    surface = Color.White,
    onSurface = InkBlack,
    error = HangUpRed,
    onError = Color.White
)

// ---- Dark schemes (kept minimal; feature colors stay) -------------------

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
 * CallDadTheme.
 *
 * dynamicColor is HARD-CODED false. It is not a parameter. If a future
 * contributor wants Material You, they must add an explicit opt-in flag
 * AND accept that the flavor palettes will be silently overridden on
 * Android 12+ devices. The flavor distinction is the product requirement;
 * dynamic color is not.
 *
 * Theme selection reads BuildConfig.APP_THEME, an explicit string injected
 * by the Gradle flavor block. Renaming a flavor in build.gradle.kts does
 * not change APP_THEME, so the theme cannot be accidentally swapped by a
 * flavor rename.
 */
@Composable
fun CallDadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColors
        BuildConfig.APP_THEME == "pink" -> ChildLightColors
        BuildConfig.APP_THEME == "blue" -> ParentLightColors
        else -> DefaultLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
