// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// RoutesTest.kt — the graph's contract, checked against the source of truth
// Location: app/src/test/java/com/calldad/RoutesTest.kt
package com.calldad

import com.calldad.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every route must (a) be distinct, and (b) actually have a destination.
 *
 * The hand-written enumeration this replaced was a proxy that could not fail:
 * `CHAT`, `PHOTO` and `CONSENT` were added and the test stayed green, because it
 * only checked the six names it had been told about. That is the fourth time in
 * this repo's history a test was pinned to a list instead of to the property,
 * so this one parses the sources and compares them.
 *
 * (b) is the assertion with teeth. A `Routes` constant with no matching
 * `composable(...)` in `AppNavigation.kt` is a dead route: a tile that navigates
 * to a destination the graph has never heard of, which in Navigation-Compose
 * means a blank screen and no error at build time.
 */
class RoutesTest {

    private val mainDir = "src/main/java/com/calldad"

    private val routesSource = File("$mainDir/navigation/Routes.kt").readText()
    private val navSource = File("$mainDir/navigation/AppNavigation.kt").readText()
    private val homeVmSource = File("$mainDir/ui/screens/HomeViewModel.kt").readText()

    /** Every `const val NAME = "value"` declared in `Routes`. */
    private val declaredRoutes: Map<String, String> =
        Regex("""const val (\w+)\s*=\s*"([^"]+)"""")
            .findAll(routesSource)
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test
    fun everyDeclaredRouteHasADestination() {
        assertTrue(
            "failed to parse any routes out of Routes.kt — if the syntax changed, " +
                "this test is now vacuous and must be updated, not deleted",
            declaredRoutes.isNotEmpty()
        )
        val missing = declaredRoutes.keys.filter { name ->
            // The call destination is `composable(Routes.NAME)`, with a template
            // for the parameterised CALL route.
            !navSource.contains("composable(Routes.$name)")
        }
        assertTrue(
            "routes declared with no destination in AppNavigation.kt: $missing. A route " +
                "with no destination navigates to a blank screen and never fails the build.",
            missing.isEmpty()
        )
    }

    @Test
    fun everyRouteValueIsNonBlankAndUnique() {
        val values = declaredRoutes.values
        values.forEach { assertTrue("blank route value", it.isNotBlank()) }
        assertEquals(
            "duplicate route values: $values",
            values.size,
            values.toSet().size
        )
    }

    @Test
    fun theCallRouteKeepsItsModeArgument() {
        // `composable(Routes.CALL)` is a PREFIX match for the real
        // `call?mode={mode}` destination; dropping the argument would send every
        // outgoing call through the incoming-ring path.
        assertTrue(
            "the CALL destination must keep its mode argument",
            navSource.contains("""call?mode={mode}""")
        )
    }

    @Test
    fun everyHomeTileIsChildReachableAndRegistered() {
        val tileRoutes = Regex("""\((Routes\.\w+),""")
            .findAll(homeVmSource)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("no Home tiles parsed from HomeViewModel", tileRoutes.isNotEmpty())
        tileRoutes.forEach { r ->
            val name = r.removePrefix("Routes.")
            assertTrue(
                "Home tile $r is not a declared route",
                declaredRoutes.containsKey(name)
            )
        }
    }

    @Test
    fun consentAndPairingAreNeverChildTiles() {
        // These are the two capabilities a child must not be able to press. They
        // are routed to from the corner icons, which sit behind ParentGate.
        val home = File("$mainDir/ui/screens/HomeScreen.kt").readText()
        listOf("CONSENT", "PAIRING").forEach { name ->
            assertTrue(
                "$name must not be a HomeDestination — that is a child-reachable tile",
                !homeVmSource.contains("($name,") && !homeVmSource.contains("Routes.$name,")
            )
        }
    }

    @Test
    fun homeIsTheStartDestination() {
        assertEquals("home", Routes.HOME)
        assertTrue(
            "the graph must still start at Home",
            navSource.contains("startDestination = Routes.HOME")
        )
    }
}
