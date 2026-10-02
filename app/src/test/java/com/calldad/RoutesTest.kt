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

    /**
     * Local `const val` aliases declared in `AppNavigation.kt`, name -> the
     * `Routes.X` it interpolates.
     *
     * The CALL destination is declared as `route = CALL_ROUTE`, where
     * `CALL_ROUTE = "${Routes.CALL}?mode={mode}"`. A literal `composable(
     * Routes.NAME)` search therefore reports CALL as orphaned even though the
     * destination is right there — the old test did exactly that, and had been
     * failing behind a green gate ever since the alias was introduced.
     */
/**
     * Matches a local `const val NAME = "` opening. Deliberately stops BEFORE the
     * quote's value so the pattern itself never has to contain one.
     *
     * The first version put the whole alias in the pattern, quotes and all, and
     * matched NOTHING -- silently, since an empty map still produces a plausible
     * "missing routes" answer. It failed with `[CALL]` missing, which is the
     * correct DIAGNOSIS reached by the wrong route.
     */
    private val localAliases: Map<String, String> =
        Regex("""(?m)^.*const val (\w+)\s*=.*""")
            .findAll(navSource)
            .mapNotNull { m ->
                // An alias is only interesting if the line ALSO interpolates a
                // declared route. Skipping the rest matters: `requireNotNull` here
                // failed every test in the class at construction time, because
                // AppNavigation declares non-route constants too. A check that
                // throws on unrelated input is not a check.
                val declared = declaredRoutes.keys.firstOrNull { m.value.contains("Routes.$it") }
                if (declared == null) null else m.groupValues[1] to declared
            }
            .toMap()

    /**
     * Every `Routes` constant that has a `composable(...)` destination.
     *
     * Reads the ARGUMENT of each `composable(` and resolves it, whether it is
     * `Routes.NAME` directly or a local alias such as `CALL_ROUTE`. Handles the
     * `route =` named-argument form and a newline between `composable(` and its
     * argument, both of which the CALL destination uses.
     */
    private fun destinationRoutes(): Set<String> {
        val out = mutableSetOf<String>()
        Regex("""composable\s*\(\s*(?:route\s*=\s*)?([A-Za-z_.]+)""")
            .findAll(navSource)
            .forEach { m ->
                val arg = m.groupValues[1]
                val direct = arg.removePrefix("Routes.")
                when {
                    arg.startsWith("Routes.") && direct in declaredRoutes -> out += direct
                    arg in localAliases -> out += localAliases.getValue(arg)
                    // A literal route string that happens to equal a declared value.
                    declaredRoutes.containsValue(arg) ->
                        out += declaredRoutes.entries.first { it.value == arg }.key
                }
            }
        return out
    }

    @Test
    fun everyDeclaredRouteHasADestination() {
        assertTrue(
            "failed to parse any routes out of Routes.kt — if the syntax changed, " +
                "this test is now vacuous and must be updated, not deleted",
            declaredRoutes.isNotEmpty()
        )
        val missing = declaredRoutes.keys.filter { name ->
            !destinationRoutes().any { it == name }
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
        // The mode argument lives in the CALL_ROUTE template
        // (`"${Routes.CALL}?mode={mode}"`), NOT in a literal `call?mode={mode}`
        // string. The old assertion looked for the literal and so failed on a
        // correct implementation.
        assertTrue(
            "the CALL destination must keep its mode argument. It is declared via " +
                "CALL_ROUTE = \"\${Routes.CALL}?mode={mode}\", so that is where the " +
                "template must appear",
            Regex("""Routes\.CALL\}\?mode=\{mode\}""").containsMatchIn(navSource)
        )
        assertTrue(
            "the mode navArgument must still be declared, or Navigation-Compose " +
                "silently drops the parameter and every outgoing call takes the " +
                "incoming-ring path",
            navSource.contains("""navArgument("mode")""")
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
