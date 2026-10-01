// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ui/theme/Color.kt
// Location: app/src/main/java/com/calldad/ui/theme/Color.kt
package com.calldad.ui.theme

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

/**
 * Chat's tile colour.
 *
 * The tile needs a saturated identity to stay legible in the 2x2 grid, so it
 * gets a hue the child has not already learned — teal, deliberately far from
 * green (call), blue (games), orange (walkie-talkie) and purple (helper). It is
 * the one place the "no fifth colour" rule yields, and it yields for a specific
 * reason: the MESSAGE BUBBLES stay neutral (see `ChatBubbleMine`), because a
 * bubble is text to be read, while a tile is a door to be found.
 */
val ChatPurple = Color(0xFF00796B)

val HangUpRed = Color(0xFFC62828)

// ---- Chat (SPEC_SHEET §2.3) ----------------------------------------------
// Deliberately NOT a fourth saturated feature colour. A child has already
// learned green = Dad, blue = games, orange = walkie-talkie, purple = helper;
// adding a fifth would dilute the cue that makes the Home screen legible at a
// glance. Chat is the written form of the same conversation, so it reuses the
// neutrals and separates the two speakers by VALUE, not hue.
//
// The "mine" bubble is the deeper neutral on purpose: the child's own words sit
// behind the most contrast on the screen, so what they said is always the
// easiest thing to read back.
val ChatBubbleMine = Color(0xFFDDE7EE)
val ChatBubbleTheirs = Color(0xFFF1EDE7)

// ---- Neutrals ------------------------------------------------------------
val WarmCream = Color(0xFFFDF7F0)
val InkBlack = Color(0xFF1B1B1B)

// ---- Flavor palettes ----------------------------------------------------
// These route the app's background and primary surface colors based on
// the build flavor. Feature-identity colors (CallGreen, GameBlue, etc.)
// remain constant across flavors — a child who learned "green means
// call Dad" must not lose that cue when the flavor changes.

// Parent (Dad) — Light Blue.
val DadBluePrimary       = Color(0xFF1565C0)
val DadBluePrimaryDark   = Color(0xFF0D47A1)
val DadBlueContainer     = Color(0xFFE3F2FD)
val DadBlueOnContainer   = Color(0xFF0D47A1)
val DadBlueBackground    = Color(0xFFF5F9FF)

// Child (Daughter) — Light Pink.
val ChildPinkPrimary       = Color(0xFFD81B60)
val ChildPinkPrimaryDark   = Color(0xFFAD1457)
val ChildPinkContainer     = Color(0xFFFCE4EC)
val ChildPinkOnContainer   = Color(0xFF880E4F)
val ChildPinkBackground    = Color(0xFFFFF5F8)
