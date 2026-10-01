// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ConsentScreenSafetyTest.kt — the kill switch must be parent-only, and findable
// Location: app/src/test/java/com/calldad/ConsentScreenSafetyTest.kt
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BP-05 §2 says "revocation blocks all comms". A revocation the child can
 * trigger is not a kill switch, it is a child-controlled mute — and a child who
 * can turn their own permissions off and back on has no reason to ask.
 *
 * The converse failure is just as bad and easier to ship: a kill switch that
 * exists but which no parent can FIND is a feature that only looks done. These
 * tests pin both directions, because they fail in opposite ways and a repo that
 * only checks one will happily accept the other.
 */
class ConsentScreenSafetyTest {

    private val mainDir = "src/main/java/com/calldad"
    private fun read(rel: String) = java.io.File("$mainDir/$rel").readText()
    private fun code(rel: String) = read(rel)
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""(?m)//.*$"""), " ")

    private fun consentScreen() = read("ui/screens/ConsentScreen.kt")
    private fun nav() = read("navigation/AppNavigation.kt")

    @Test
    fun theKillSwitchIsBehindTheParentGate() {
        val code = code("ui/screens/ConsentScreen.kt")
        assertTrue(
            "ConsentScreen must gate itself with ParentGate. Without the gate the " +
                "child can revoke and, worse, re-grant themselves.",
            code.contains("ParentGate(")
        )
    }

    @Test
    fun theGateStateIsNotSaveableAcrossProcessDeath() {
        // `rememberSaveable` here would restore the child INTO the controls after
        // the process dies — which is exactly the bug the pairing screen already
        // had and fixed. This is the same class, so it gets the same assertion.
        val text = consentScreen()
        val gateState = Regex("""var unlocked by (remember|rememberSaveable)""")
            .find(text)?.groupValues?.get(1)
        assertTrue("expected an `unlocked` gate flag, found none", gateState != null)
        assertTrue(
            "the gate flag MUST be `remember`, not `rememberSaveable`: a saved " +
                "true restores the child straight into the grown-ups controls. Found: " +
                "$gateState",
            gateState == "remember"
        )
    }

    @Test
    fun theGrantIsReachableFromHome() {
        // The "exists but nobody finds it" failure. A revoke control reachable
        // only by deep link or by editing code is a control that will never be
        // used, and a kill switch that is never used is indistinguishable from
        // having none.
        val home = read("ui/screens/HomeScreen.kt")
        assertTrue(
            "Home must expose a way into the consent screen",
            home.contains("onOpenConsent")
        )
        val navCode = nav()
        assertTrue(
            "the CONSENT route must be registered in the nav graph",
            navCode.contains("Routes.CONSENT)")
        )
    }

    @Test
    fun consentIsNotAChildReachableDestination() {
        // The route exists in the graph, so the guard is that NOTHING in the
        // child's tile set can navigate to it. A HomeDestination is a tile a
        // child can press; consent must never be one of them.
        val home = code("ui/screens/HomeViewModel.kt")
        assertFalse(
            "CONSENT must not be a HomeDestination — that is a child-reachable tile",
            home.contains("Routes.CONSENT")
        )
    }

    @Test
    fun reGrantingAlwaysUsesAHigherSequence() {
        // The rules require a strictly higher `grantSeq`, so a client that reuses
        // the current one is guaranteed PERMISSION_DENIED. A parent who taps
        // "Allow everything" twice and gets a failure the second time will
        // conclude the switch is broken.
        val text = code("ui/screens/ConsentScreen.kt")
        assertTrue(
            "a grant must be issued with a higher sequence than the current one",
            Regex("""highestSeq\s*\+\s*1""").containsMatchIn(text)
        )
    }

    @Test
    fun theRevokeWordingDoesNotPromiseAToggle() {
        // "It stays off until you allow it again" is true (ADR-017: a revocation
        // is append-only and nothing can edit or delete it). A wording that
        // implied a switch is a false promise, and a parent who believes the
        // switch is off and does not check is the exact failure this exists to
        // prevent.
        val text = consentScreen()
        listOf("Toggle", "on/off", "On or off").forEach {
            assertFalse("the revoke copy must not imply a toggle. Found: $it", text.contains(it))
        }
        assertTrue(
            "the revoke result should say it stays off until re-allowed",
            text.contains("stays off until you allow it again")
        )
    }

    @Test
    fun theScreenOffersTwoUnambiguousActions() {
        // Deliberately not a per-scope checkbox grid. A parent on a child's phone
        // in a hurry needs two choices; four checkboxes is how you ship a consent
        // screen nobody reads, and a half-read consent screen is worse than a
        // coarse one. The domain still supports independent scopes.
        //
        // The dismiss-tap on the result banner is counted here rather than
        // exempted, because a banner nobody can clear is a banner that stays on
        // screen saying "Couldn't save that" after the connection comes back and
        // a retry succeeds.
        val code = code("ui/screens/ConsentScreen.kt")
        val actions = Regex("""(onGrant|onRevoke|onAcknowledge)\(""").findAll(code).count()
        assertTrue(
            "expected only the grant, revoke and dismiss handlers, found $actions",
            actions <= 6
        )
        assertFalse(
            "a per-scope checkbox grid would ship a consent screen nobody reads",
            code.contains("Checkbox(")
        )
    }

    @Test
    fun theControlsClearTheNinetySixDpFloor() {
        val code = code("ui/screens/ConsentScreen.kt")
        val small = Regex("""heightIn\(min = (\d+)\.dp\)""")
            .findAll(code)
            .map { it.groupValues[1].toInt() }
            .filter { it < 96 }
            .toList()
        assertTrue(
            "grown-ups controls are not a 6-year-old's target, but BP-05 §4's 96dp " +
                "floor still applies here; found ${small.map { "${it}dp" }}",
            small.isEmpty()
        )
        val iconButtons = Regex("""IconButton\([\s\S]{0,200}?\.size\((\d+)\.dp\)""")
            .findAll(code).map { it.groupValues[1].toInt() }
        iconButtons.forEach {
            assertTrue("IconButton of ${it}dp is below the 96dp floor", it >= 96)
        }
    }
}
