// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// navigation/Routes.kt
// Location: app/src/main/java/com/calldad/navigation/Routes.kt
package com.calldad.navigation

/** Single source of truth for every route in the graph. */
object Routes {
    const val HOME = "home"
    const val CALL = "call"
    const val PTT = "ptt"
    const val GAME = "game"
    const val HELPER = "helper"
    const val PAIRING = "pairing"

    /** 1:1 Dad thread (SPEC_SHEET §2.3, BP-03). */
    const val CHAT = "chat"

    /** Pictures in the same thread (SPEC_SHEET §2.4, BP-04). */
    const val PHOTO = "photo"

    /** Parent-side consent grant/revoke. ALWAYS behind ParentGate. */
    const val CONSENT = "consent"
}
