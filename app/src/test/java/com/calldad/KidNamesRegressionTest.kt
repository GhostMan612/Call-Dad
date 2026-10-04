// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// KidNamesRegressionTest.kt — who is this app calling the other person?
// Location: app/src/test/java/com/calldad/KidNamesRegressionTest.kt
package com.calldad

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE APP NAMED THE WRONG HUMAN, THREE SEPARATE TIMES, ON ONE HARDWARE SIGHTING.
 *
 * The operator reported it: the child app said "Mama is calling" when it should
 * have said "Dad is calling", and when one device won a game "things are
 * mislabeled". Root-causing those found three distinct instances of ONE class of
 * defect, and every instance is the same mistake:
 *
 *  1. **A peer-specific fact baked into a string literal.** The walkie talkie
 *     said "Dad will hear it right away" on BOTH flavors, so on the grown-up's
 *     own device it told them Dad would hear their message.
 *  2. **A game string hardcoded to one side.** `winText()` returned
 *     `"Dad wins!"` for every loss. `role === "caller"` IS the parent (GameScreen
 *     maps APP_THEME blue -> caller), so when the CHILD won, the PARENT'S screen
 *     congratulated Dad for a game Dad had just lost.
 *  3. **Resource names that lied about their own meaning.** `child_peer_name`
 *     held a GROWN-UP's name, and was used in one place to name the child and in
 *     another to name the grown-up -- so no call site could be read reliably. It
 *     also had two DIFFERENT mappings in the tree: `CallViewModel.peerDisplayName`
 *     was inverted relative to ChatScreen and the PTT banner, so the call screen
 *     and the message thread named the same person two different ways.
 *
 * None of these throw, none crash, and none are visible to any host test that
 * checks behaviour rather than text. They are only visible to a person LOOKING at
 * the screen. Which means they need a structural pin, because "we fixed the one
 * we saw" is how the second and third got shipped.
 *
 * THE MAPPING, which is the thing worth protecting: a parent device
 * (`APP_THEME == "blue"`) shows the CHILD's name; a child device shows the
 * GROWN-UP's name. Uniformly, everywhere. If you are adding a screen that names
 * the peer, use that mapping rather than re-deriving it.
 */
class KidNamesRegressionTest {

    private val mainDir = "src/main/java/com/calldad"
    private fun read(rel: String) = File("$mainDir/$rel").readText()
    private fun res(rel: String) = File("src/main/res/$rel").readText()
    private fun asset(rel: String) = File("src/main/assets/$rel").readText()

    private val strings = res("values/strings.xml")
    private val callVm = read("ui/screens/CallViewModel.kt")
    private val chatScreen = read("ui/screens/ChatScreen.kt")
    private val pttScreen = read("ui/screens/PttViewModel.kt")
    private val pttUi = read("ui/screens/PttScreen.kt")
    private val nav = read("navigation/AppNavigation.kt")
    private val game = asset("game.html")

    /**
     * Source with comments removed.
     *
     * Not cosmetic. Every assertion in this class is about what a HUMAN READS on
     * the screen, and every fix for one of these bugs comes with a comment
     * explaining the bug — which quotes the old wrong string verbatim. So a
     * scanner that does not strip comments reports the documentation of the fix
     * as a fresh instance of the bug, and the test cannot be written at all. This
     * is the same trap `WiringRegressionTest` already fell into with
     * `substringBefore` widening a slice to the whole file.
     */
    private fun String.withoutComments(): String =
        replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    /**
     * The grown-up is Dad and the child is a kid. Both sides ship, so any string
     * that names one of them has to be a RESOURCE, not a literal.
     */
    @Test
    fun theTwoHumanNamesExistAndMeanWhatTheySay() {
        assertTrue(
            "there must be a string naming the GROWN-UP as the child sees them. " +
                "It was 'Mama', which is how the child app came to announce " +
                "'Mama is calling'.",
            strings.contains("name_of_grown_up")
        )
        assertTrue(
            "and the grown-up's name must not be the old wrong one",
            !strings.contains(">Mama<")
        )
    }

    /**
     * The resources were named `child_peer_name` / `parent_peer_name`, which is
     * worse than no name: the VALUES were inverted relative to the names, so a
     * reader could not tell which person a call site was labelling. The names are
     * now the people rather than the flavors, which is the only thing that makes
     * them readable.
     */
    @Test
    fun theFlavourNamedResourcesAreGone() {
        // Match the DECLARATION, not the name: this file's own comment quotes
        // both old names in order to explain why they were bad.
        listOf("child_peer_name", "parent_peer_name").forEach { dead ->
            assertFalse(
                "'$dead' named a FLAVOR while being used to name a PERSON, which is " +
                    "how the values ended up inverted. Name the person instead.",
                strings.contains("<string name=\"$dead\"")
            )
        }
        for (src in listOf(callVm, chatScreen, pttUi, nav)) {
            assertFalse(
                "no screen may still reference a flavour-named peer resource",
                src.withoutComments().contains("child_peer_name") ||
                    src.withoutComments().contains("parent_peer_name")
            )
        }
    }

    /**
     * THE UNIFORM MAPPING. Every site that names the other person must use the
     * same one, and this is the assertion that would have caught the second
     * mislabel. `peerDisplayName()` was inverted relative to ChatScreen and the
     * PTT banner, so the call screen and the message thread disagreed.
     */
    @Test
    fun everySiteThatNamesThePeerUsesTheSameMapping() {
        val sites = mapOf(
            "CallViewModel.peerDisplayName" to callVm.substringAfter("private fun peerDisplayName")
                .substringBefore("private fun callerDisplayName"),
            "ChatScreen header" to chatScreen.substringAfter("peerName = stringResource(")
                .substringBefore("),"),
            "PttScreen hint" to pttUi.substringAfter("val peerName = stringResource(")
                .substringBefore("val hintLabel"),
            // Widened past the first ")", which lands inside stringResource(...) and
            // would slice away the mapping entirely -- an earlier version of this
            // assertion passed on a banner that had no name in it at all.
            "AppNavigation PTT banner" to nav.substringAfter("R.string.ptt_receiving_banner")
                .substringBefore("style = MaterialTheme.typography.headlineSmall")
        )
        sites.forEach { (where, body) ->
            val code = body.withoutComments()
            assertTrue(
                "$where must name the peer via a stringResource, not a literal",
                code.contains("stringResource") || code.contains("R.string.")
            )
            assertTrue(
                "$where must use the UNIFORM mapping: a parent device (APP_THEME " +
                    "blue) shows name_of_child, a child device shows name_of_grown_up",
                code.contains("name_of_child") && code.contains("name_of_grown_up")
            )
            assertTrue(
                "$where must key the choice on APP_THEME, since that is the only " +
                    "thing that distinguishes the two devices",
                code.contains("APP_THEME")
            )
        }
    }

    /**
     * The incoming overlay names the CALLER. It is a different sentence from the
     * peer name, and it used to be computed by a hand-inverted second mapping —
     * a second place for the same bug to live.
     */
    @Test
    fun theIncomingOverlayNamesTheCallerWithoutReDerivingTheMapping() {
        assertTrue(
            "callerDisplayName() must DELEGATE to peerDisplayName(). Both name the " +
                "human on the other end, so two independent mappings were two " +
                "chances to be wrong.",
            callVm.contains("private fun callerDisplayName(): String = peerDisplayName()")
        )
        assertFalse(
            "and it must not re-open a second APP_THEME branch",
            callVm.substringAfter("private fun callerDisplayName()")
                .contains("APP_THEME")
        )
    }

    /**
     * The game bug, exactly as reported: one device wins, and the other one's
     * screen congratulates the wrong human. `role === "caller"` is the PARENT
     * (GameScreen maps APP_THEME blue -> caller), so the loser's label must
     * depend on the role.
     */
    @Test
    fun theGameNamesTheActualLoserOnBothDevices() {
        val win = game.substringAfter("function winText(winner)")
            .substringBefore("function turnText")
        assertTrue(
            "the loser's label must depend on the role. It was hardcoded to " +
                "'Dad wins!', which is only ever true on the CHILD's phone, so the " +
                "parent's screen congratulated Dad when the kid won. Body: " + win,
            win.contains("role === \"caller\"")
        )
        assertTrue(
            "and the parent's rival must be named as the child",
            win.contains("Your kid wins!")
        )
        assertTrue(
            "while the child's rival stays Dad",
            win.contains("Dad wins!")
        )
    }

    /**
     * The walkie talkie said "Dad will hear it right away" as a LITERAL, on both
     * flavors. Both flavors have a walkie talkie, so on the grown-up's own device
     * it announced that Dad would hear their own message.
     */
    @Test
    fun theWalkieTalkieDoesNotHardcodeAPeerName() {
        // The slice must INCLUDE the peerName val, which sits above hintLabel.
        val region = pttUi.substringAfter("val peerName = stringResource(")
            .substringBefore("val context = LocalContext.current")
        val hint = region.substringAfter("val hintLabel = when {")
        assertTrue(
            "the just-sent hint must interpolate a peer name resource",
            hint.contains("will hear it right away") &&
                hint.contains("\$" + "peerName will hear")
        )
        val literals = Regex("\"[^\"]*\\bDad\\b[^\"]*\"").findAll(region).map { it.value }
        assertTrue(
            "no screen may hardcode a person's name in a user-visible literal. " +
                "Both flavors ship, so a literal is wrong on one of them by " +
                "construction. Found: ${literals.toList()}",
            literals.none()
        )
    }

    /**
     * The child device is a TABLET (Q8K, 600x1024) as of 2026-10-03, and three
     * screens told a six-year-old that "the phone" needed something. A wrong noun
     * about the device in front of you is the same class of defect as a wrong
     * name: the text does not match the world.
     */
    @Test
    fun noScreenCallsTheChildTabletAPhone() {
        val userVisible = listOf(
            "HomeScreen" to read("ui/screens/HomeScreen.kt"),
            "CallScreen" to read("ui/screens/CallScreen.kt"),
            "ConsentScreen" to read("ui/screens/ConsentScreen.kt")
        )
        userVisible.forEach { (name, src) ->
            val literals = Regex("\"[^\"]*\\bphone\\b[^\"]*\"")
                .findAll(src.withoutComments()).map { it.value }.toList()
            assertTrue(
                "$name calls the child's device a phone in $literals. The child " +
                    "device is a 600x1024 tablet; say \"device\".",
                literals.isEmpty()
            )
        }
    }
}