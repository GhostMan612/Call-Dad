// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ChatKidSafetyTest.kt — the no-escape boundary for anything a GROWN-UP types
// Location: app/src/test/java/com/calldad/ChatKidSafetyTest.kt
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The child half of the chat surface is covered by `ChatTextTest` (what a child
 * may send) and `CallLogTest`. This covers the half nobody was looking at: what
 * a GROWN-UP can put in front of a child, and what the app is structurally
 * forbidden from doing with it.
 *
 * A chat box is the only place in this app where a message is authored by
 * someone other than the child, which makes it the widest hole the allowlist
 * could have. The kid-safe promise in AGENTS.md is "nothing and nobody else is
 * reachable", so a link in a message is a reachability bug, not a formatting
 * preference.
 *
 * These are SOURCE assertions, and that limitation is stated rather than
 * hidden: they cannot prove what a rendered screen does, only that the code
 * contains no affordance. The device half of BP-05 §4 is a human walkthrough
 * and is recorded as HUMAN in `docs/kid-safe-ux.md`.
 */
class ChatKidSafetyTest {

    // Tests run with the `app/` module directory as the working directory, so
    // the source root is a SIBLING of it, not a child. `../docs` is the same
    // trick `KidUxAuditTest` uses for the audit sheet.
    private val mainDir = "src/main/java/com/calldad"
    private fun read(rel: String) = java.io.File("$mainDir/$rel").readText()

    /**
     * The CODE, with comments and doc strings removed.
     *
     * Without this the test is a liar in the worst way: the chat screen's own
     * KDoc explains WHY it uses no `ClickableText` and no `autoLink`, and a
     * substring scan then reports a violation for the comment that documents
     * the fix. A source-text test has to read the source the way a compiler
     * does — otherwise every future explanatory comment becomes a false
     * positive, and the incentive becomes "stop writing down why".
     */
    private fun code(rel: String): String = read(rel)
        // DOT_MATCHES_ALL is load-bearing: a KDoc block spans many lines and a
        // `.` that does not cross newlines leaves the whole thing in the "code",
        // which is how this file spent a failing run reporting violations for
        // the comment explaining the fix.
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""(?m)//.*$"""), " ")

    private fun chatCode() = code("ui/screens/ChatScreen.kt")

    @Test
    fun theTestItselfIsNotTheReasonItPasses() {
        // If stripping comments ever stops mattering, this goes red and the
        // helper is doing nothing. Cheap insurance against a self-defeating
        // test that can only ever pass one way.
        assertTrue(
            "the chat screen's KDoc must mention the banned tokens, otherwise the " +
                "comment-stripping below is doing nothing and this file is asserting " +
                "against documentation",
            read("ui/screens/ChatScreen.kt").contains("ClickableText")
        )
    }

    @Test
    fun messageBodiesAreNeverRenderedAsClickableText() {
        val text = chatCode()
        assertFalse(
            "A tappable message is a route out of the allowlist. Use plain Text " +
                "for message bodies -- the app's whole promise is that nothing else " +
                "is reachable, and a link in a message is reachable.",
            text.contains("ClickableText") || text.contains("LinkAnnotation")
        )
    }

    @Test
    fun autoLinkIsNeverEnabled() {
        // autoLink silently turns a pasted URL into a link, so a parent who
        // pastes one without noticing has armed it for the child.
        val text = chatCode()
        assertFalse(
            "autoLink must never be set on a message body",
            text.contains("AutoLink") || text.contains("autoLink")
        )
    }

    @Test
    fun thereIsNoLinkPreviewOrAttachmentAffordance() {
        val text = chatCode()
        listOf("LinkPreview", "previewImage", "AsyncImage", "attachment").forEach {
            assertFalse(
                "A preview or attachment affordance in the thread is a way out of " +
                    "the app. Found: $it",
                text.contains(it)
            )
        }
    }

    @Test
    fun theThreadHasNoBrowserIntent() {
        // Belt and braces: even if a URL got through both the validator and the
        // renderer, nothing in the chat screen may reach an intent.
        val text = chatCode()
        listOf("Intent.ACTION_VIEW", "startActivity", "LocalUriHandler").forEach {
            assertFalse("the chat screen must not launch anything. Found: $it", text.contains(it))
        }
    }

    @Test
    fun theScreenExposesExactlyThreeActions() {
        // Read, type, send, go home. Every added affordance is something a child
        // can reach from the one screen that shows free-form text.
        val text = chatCode()
        val onClicks = Regex("""onClick\s*=\s*""").findAll(text).count()
        assertTrue(
            "the thread should stay at a small, fixed number of actions; found " +
                "$onClicks onClick sites",
            onClicks <= 4
        )
    }

    @Test
    fun bothTheBackArrowAndTheSendButtonAreLargeEnoughToHit() {
        // 96dp is the BP-05 §4 floor for a primary target. This assertion is why
        // the chat screen's icon buttons are 96dp and not the 64dp used elsewhere
        // in the app: this screen is reached deliberately and has no 2x2 grid of
        // giant cards to fall back on, and both of these buttons are the only way
        // out of a screen showing free-form text.
        //
        // The size is read from the CONSTANT rather than from the `.size(...)`
        // call sites, because a literal at the call site is exactly the thing
        // that gets "tidied" down to 64dp later without anyone deciding to.
        val text = chatCode()
        val declared = Regex("""TOUCH_TARGET_DP\s*=\s*(\d+)""")
            .find(text)?.groupValues?.get(1)?.toIntOrNull()
        assertTrue(
            "the thread must declare its touch target as a named constant, " +
                "found none",
            declared != null
        )
        assertTrue(
            "TOUCH_TARGET_DP is $declared dp, below the 96dp floor",
            declared!! >= 96
        )
        // And every IconButton must actually USE it, or the constant is decorative.
        val buttons = Regex("""IconButton\([\s\S]{0,220}?\.size\(([^)]*)\)""")
            .findAll(text).map { it.groupValues[1].trim() }.toList()
        assertTrue("expected IconButtons in the thread, found none", buttons.isNotEmpty())
        buttons.forEach {
            assertTrue(
                "IconButton sized by `$it` bypasses the 96dp constant",
                it == "TOUCH_TARGET_DP.dp"
            )
        }
    }

    @Test
    fun theComposerNeverAutocorrectsOrPredicts() {
        // Autocorrect and predictive text both learn from what a child types.
        // The messages stay on-device, but a learned dictionary is a copy of the
        // thread sitting somewhere else, and there is no reason for a 6-year-old
        // to trade privacy for spelling help.
        val text = chatCode()
        listOf("KeyboardType", "AutoCorrect", "KeyboardOptions", "ImeAction")
            .forEach {
                assertFalse("found $it in the thread composer", text.contains(it))
            }
    }
}
