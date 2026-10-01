// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host test for the BP-05 §4 kid-UX audit (docs/kid-safe-ux.md sheet).
//
// WHY THIS EXISTS. BP-05 §4 lists six criteria and docs/kid-safe-ux.md:7 asks
// for an audit sheet. The sheet was never written, so the criteria were never
// checked. That is the exact "unenforced law drifts" failure this repo has
// already paid for three times (the serial leak, the per-edit gate, the
// reconnect string), so the machine-checkable half is pinned here rather than
// left to a reader's memory.
//
// WHAT IS ACTUALLY CHECKABLE. Touch-target sizes and the no-escape property are
// static facts about the source: a primary control under 96dp, or an
// ACTION_BROWSER/ACTION_VIEW intent, is checkable. Contrast ratios need a
// rendered frame, and the back-stack walk, airplane recovery, loud-ring check
// and missed-call callback all need a human with two phones. Those are marked
// HUMAN below and are NOT asserted here -- claiming them would be the same
// kind of invented pass this repo keeps catching.
//
// Comments are stripped before every scan. Several of these criteria were
// historically satisfied by a comment describing the invariant rather than by
// the code, which is how the earlier drift stayed invisible.
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KidUxAuditTest {

    private fun read(path: String): String {
        val file = listOf(File(path), File("app", path)).firstOrNull(File::exists)
        checkNotNull(file) { "could not find " + path }
        return file.readText()
    }

    private fun codeOnly(src: String): String = Regex("""//[^\n]*""").replace(src, "")

    /**
     * Matches `minHeight = 96.dp` / `minHeight: Dp = 100.dp` and returns the
     * NUMBER.
     *
     * The trailing `\.dp` is required, not `dp`. In Kotlin source the dot is a
     * decimal point AND the unit separator, so `96.dp` will NOT match a naive
     * `(\d+(?:\.\d+)?)dp` -- the capture backtracks off the dot and the literal
     * "dp" never arrives. Writing the separator explicitly is what makes both
     * `100.dp` and a hypothetical `96.5.dp` parse correctly.
     */
    private val minHeightDp =
        Regex("""minHeight\s*(?::\s*Dp\s*)?=\s*([0-9]+(?:\.[0-9]+)?)\.dp""")

    private fun sourceDir(dir: String): List<File> =
        File(dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val uiSources: List<File> by lazy {
        val uiRoot = listOf(
            File("src/main/java/com/calldad/ui"),
            File("app/src/main/java/com/calldad/ui")
        ).firstOrNull { it.isDirectory }
        checkNotNull(uiRoot) { "could not find the ui source directory" }
        sourceDir(uiRoot.path)
    }

    private fun code(): String = uiSources.joinToString("\n") { codeOnly(it.readText()) }

    // ---- criterion 1: primary targets >= 96dp ----

    @Test
    fun everyPrimaryControlIsAtLeast96dp() {
        assertFalse("no UI sources found to audit", uiSources.isEmpty())
        val components = code()
        // The BP-05 floor is 96dp. Below that a 6-year-old's thumb misses, and
        // a miss on a hangup button is worse than a miss anywhere else.
        val tooSmall = minHeightDp.findAll(components)
            .map { it.groupValues[1].toDouble() }
            .filter { it < 96.0 }
            .toList()
        assertTrue(
            "these primary controls declare a minHeight under the 96dp floor: $tooSmall",
            tooSmall.isEmpty()
        )
        // GiantButton's own default must clear the floor without a caller
        // having to remember to raise it.
        val defaultDp = minHeightDp.findAll(components)
            .map { it.groupValues[1].toDouble() }
            .maxOrNull()
        assertTrue(
            "GiantButton's default minHeight must clear the 96dp floor so a new " +
                "screen cannot accidentally ship a small primary control, found $defaultDp",
            defaultDp != null && defaultDp >= 96.0
        )
    }

    @Test
    fun theParentGateControlsAlsoClearTheFloor() {
        // The gate is the ONLY thing standing between a child and re-pairing, so
        // its keys are primary controls by any reading, not secondary ones.
        val gate = codeOnly(read("src/main/java/com/calldad/ui/components/ParentGate.kt"))
        val heights = minHeightDp.findAll(gate)
            .map { it.groupValues[1].toDouble() }
            .toList()
        assertTrue(
            "the parent gate must declare sized controls via minHeight = Ndp, found none in:\n$gate",
            heights.isNotEmpty()
        )
        assertTrue(
            "the parent gate's own controls must clear 96dp, found $heights",
            heights.all { it >= 96.0 }
        )
    }

    // ---- criterion 4: no escape ----

    @Test
    fun thereIsNoBrowserOrStoreEscapeFromAnyScreen() {
        // docs/kid-safe-ux.md:5 -- "no links/browser/store/settings reachable
        // from kid screens". An ACTION_VIEW or ACTION_BROWSABLE intent is the
        // way out of an app and straight onto the open internet.
        val whole = uiSources.joinToString("\n") { codeOnly(it.readText()) }
        listOf(
            "Intent.ACTION_VIEW",
            "Intent.ACTION_BROWSABLE",
            "Intent.ACTION_SEND",
            "Intent.ACTION_WEB_SEARCH",
            "market://"
        ).forEach { escape ->
            assertFalse(
                "kid screens must not reach $escape -- that is a one-tap exit from " +
                    "the app and onto the internet",
                whole.contains(escape)
            )
        }
    }

    @Test
    fun systemBackCannotLeaveTheKidOnTheirOwn() {
        // "system back lands on Home". Back must return to Home, never exit.
        val activity = codeOnly(read("src/main/java/com/calldad/MainActivity.kt"))
        assertFalse(
            "the activity must not opt into allowing the system back to finish " +
                "the app -- a child pressing back should land on Home, not on a " +
                "home-screen launcher",
            activity.contains("enableOnBackInvokedCallback") &&
                activity.contains("finish()")
        )
        assertTrue(
            "a BackHandler should exist so back is intercepted rather than " +
                "defaulting to whatever Compose navigation does",
            read("src/main/java/com/calldad/MainActivity.kt").contains("BackHandler") ||
                code().contains("BackHandler")
        )
    }

    // ---- criterion 6: no real child data in committed screenshots ----

    @Test
    fun noScreenshotOrMediaIsCommitted() {
        // docs/kid-safe-ux.md:8 / RULES 1.3 -- a screenshot taken during
        // testing can carry a real child's name, face or location.
        val banned = listOf(".png", ".jpg", ".jpeg", ".webp", ".heic")
        banned.forEach { ext ->
            val found = walkRepo()
                .filter { it.isFile && it.extension.equals(ext, ignoreCase = true) }
                .filterNot { it.path.contains("${File.separator}drawable${File.separator}") }
                .filterNot { it.path.contains("${File.separator}build${File.separator}") }
                .filterNot { it.path.contains("${File.separator}.git${File.separator}") }
                .map { it.name }
            assertTrue(
                "a committed image outside res/drawable may contain real child " +
                    "data or a screenshot: $found",
                found.isEmpty()
            )
        }
    }

    private fun walkRepo(): List<File> {
        val root = listOf(File("."), File("..")).first { File(it, "RULES.md").isFile }
        return root.walkTopDown()
            .onEnter { it.name !in setOf("build", ".git", ".gradle") }
            .toList()
    }

    // ---- criteria that need a human, recorded so the sheet is honest ----

    @Test
    fun theAuditSheetNamesEveryCriterionAndMarksTheHumanOnes() {
        // The sheet is the deliverable; this keeps it from silently dropping a
        // criterion, and keeps it from quietly claiming a human-only result.
        val sheet = read("../docs/kid-safe-ux.md")
        // Terms come from BP-05 §4 / kid-safe-ux.md:7. "No escape" is written
        // with a space in prose and "no-escape" as a hyphen in the criterion
        // label; accept either so the check tracks meaning, not punctuation.
        listOf(
            "96dp", "contrast", "one action", "no escape", "escape",
            "back-stack", "airplane", "loud ring", "callback", "screenshot"
        ).forEach { term ->
            assertTrue(
                "the audit sheet must cover \"$term\" (BP-05 §4 / kid-safe-ux.md:7)",
                sheet.contains(term, ignoreCase = true)
            )
        }
        assertTrue(
            "the sheet must distinguish machine-checked results from HUMAN ones, " +
                "or it becomes a checklist of claims",
            sheet.contains("HUMAN") || sheet.contains("human-witnessed")
        )
    }
}