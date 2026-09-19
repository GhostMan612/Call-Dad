// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/theme/Color.kt
// Location: app/src/main/java/com/calldad/app/ui/theme/Color.kt
package com.calldad.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Feature identity palette -------------------------------------------
// Each core feature owns exactly ONE saturated colour. The child learns
// "green = Dad, blue = games, orange = walkie-talkie, purple = helper".
// All values are dark enough for WCAG AA contrast against white text/icons.

val CallGreen = Color(0xFF2E7D32)
val CallGreenDark = Color(0xFF1B5E20)
val CallGreenLight = Color(0xFF81C784)

val GameBlue = Color(0xFF1565C0)
val GameBlueLight = Color(0xFF64B5F6)

val PttOrange = Color(0xFFE65100)
val PttTransmitRed = Color(0xFFB71C1C)

val HelperPurple = Color(0xFF6A1B9A)
val HelperPurpleLight = Color(0xFFF3E5F5)

val HangUpRed = Color(0xFFC62828)

// ---- Neutrals ------------------------------------------------------------
val WarmCream = Color(0xFFFDF7F0)
val InkBlack = Color(0xFF1B1B1B)
