// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host test: the claims our documents make about the tree must not be false.
//
// WHY THIS EXISTS. `87ab914` was a documentation-alignment pass that fixed 28
// files. A follow-up audit of THAT pass found the worst of the defects had been
// in the layer above the code, not in the code:
//
//   * the cold-start ramp told the next agent to run `node --test
//     functions/ring.test.js` — 6 tests — while RULES.md says 13, so the ramp
//     and the law disagreed and the ramp wins because it is read first;
//   * `opencode.json` carried a live narrow allow for `firebase deploy --only
//     firestore:rules` directly under a deny, while RULES.md, AGENTS.md and
//     CHECKLIST.md all insisted there was no exception. The law was wrong in
//     the permissive direction;
//   * `/flash` told the operator to expect a `pairings` denial that cannot
//     happen because those rules went live 2026-10-01;
//   * the media auditor was told the walkie-talkie "degrades to a simulated
//     engine" and to "never imply real PTT audio", so it would have reported
//     the shipped feature as fake — the exact defect Contract 10 closed;
//   * BP-04 still instructed the operator not to add `google-services.json`
//     "until ADR-002", which would leave the app unbuildable;
//   * three agents had no front matter, so they were not registered subagents
//     and could not be invoked at all.
//
// Every one of those is a claim about the tree living only in prose. Nothing
// failed, so nothing was noticed: this repo's own hard-won lesson is that "the
// check ran" is not the same as "the check was looking at what I thought".
//
// This pins the specific claims, not prose style. If a doc needs to change
// because the CODE changed, update the doc and this test together.
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins doc claims that would send an operator or a subagent somewhere wrong.
 * Each test names the exact failure it prevents, because a test named
 * "docsAreCorrect" tells you nothing when it goes red.
 */
class DocTruthRegressionTest {

    /**
     * The repo root, found by walking up until the toolchain catalog appears.
     *
     * This replaced a candidate list of `File(path)`, `File("app", path)`,
     * `File("..", path)` which picked whichever existed FIRST -- and for a bare
     * name like "README.md" that is `app/README.md`, so the test asserting the
     * ROOT README's recorded test count was silently reading the module's. It
     * failed for the right reason and for the wrong file, which is the worst
     * combination: the message named README.md and the content came from
     * app/README.md.
     */
    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "gradle/libs.versions.toml").isFile) return@lazy dir
            dir = dir.parentFile
        }
        error("could not locate the repo root (no gradle/libs.versions.toml above ${File("").absolutePath})")
    }

    private fun read(path: String): String {
        val file = File(repoRoot, path)
        check(file.exists()) { "could not find " + file }
        return file.readText()
    }

    private fun repoFilesUnder(dir: String, suffix: String): List<File> {
        val root = File(repoRoot, dir)
        check(root.isDirectory) { "could not find directory " + root }
        return root.walkTopDown().filter { it.isFile && it.name.endsWith(suffix) }.toList()
    }

    // ---- the cold-start ramp must not contradict the law ----

    /**
     * The ramp is read BEFORE RULES.md on a cold start, so a wrong command
     * there beats a right command in the law. `ring.test.js` alone is 6 of 13
     * tests and reads as a 7-test regression.
     */
    @Test
    fun theColdStartRampRunsTheWholeFunctionsGate() {
        val agents = read("AGENTS.md")
        assertFalse(
            "AGENTS.md is the cold-start ramp and it still names only ring.test.js; " +
                "the full gate is 13 tests (6 ring + 7 clip) and a 6-test run reads " +
                "as a regression. RULES.md already says this -- keep both in step.",
            Regex("""node --test functions/ring\.test\.js\s*$""", RegexOption.MULTILINE)
                .containsMatchIn(agents)
        )
        assertTrue(
            "AGENTS.md should name the full functions gate",
            agents.contains("functions/clip.test.js")
        )
    }

    /**
     * The narrow `firebase deploy --only firestore:rules` allow existed only to
     * unblock one deploy. It outlived that deploy and sat one line under a deny
     * while three documents insisted there was no exception at all.
     */
    @Test
    fun noDeployAllowOutlivesTheDeployItExistedFor() {
        val config = read("opencode.json")
        assertFalse(
            "opencode.json grants a firebase deploy allow. RULES.md §1.5a, AGENTS.md " +
                "and CHECKLIST.md all state there is NO exception; an allow that " +
                "outlives its one use is a hole in the law, not a permission.",
            Regex(""""firebase[^"]*deploy[^"]*"\s*:\s*"allow"""").containsMatchIn(config)
        )
        assertTrue(
            "the blanket deploy deny must survive",
            Regex(""""firebase\*deploy\*"\s*:\s*"deny"""").containsMatchIn(config)
        )
    }

    /** `/flash` is run mid-incident; telling the operator to expect a fixed bug wastes the run. */
    @Test
    fun flashDoesNotTellTheOperatorToExpectAnAlreadyLiveStanza() {
        val flash = read(".opencode/commands/flash.md")
        assertFalse(
            "flash.md still warns that pairings writes 'can be denied' -- those rules " +
                "went live 2026-10-01 to calldad-508d7 and are pinned by the emulator.",
            flash.contains("until this lands") && flash.contains("pairings")
        )
    }

    /**
     * This one is the costly direction: the auditor was instructed to describe the
     * shipped walkie-talkie as simulated, so it would have re-reported the PTT
     * defect that Contract 10 closed.
     */
    @Test
    fun noAgentPromptDescribesShippedAudioAsSimulated() {
        val ptt = read("app/src/main/java/com/calldad/ui/screens/PttViewModel.kt")
        assertTrue(
            "premise check: the source is expected to wire the real engine",
            ptt.contains("VoiceClipPttEngine")
        )
        repoFilesUnder(".opencode/agents", ".md").forEach { agent ->
            val text = agent.readText()
        // Pin the CLAIM, not the word. "Simulated engine" legitimately appears in
        // the doc that refutes the claim; what must not survive is the claim itself
        // -- telling an auditor to describe real PTT as fake would re-report the
        // exact defect Contract 10 closed.
        listOf(
            "degrades to a simulated engine",
            "never imply real ptt audio",
            "say so, never imply"
        ).forEach { stale ->
            assertFalse(
                agent.path + " carries the stale walkie-talkie claim \"$stale\". " +
                    "PttViewModel wires VoiceClipPttEngine (real AAC clips over the " +
                    "pair room, ADR-016); SimulatedPttEngine is host-test-only, there " +
                    "is no private module and no runtime fallback.",
                text.contains(stale, ignoreCase = true)
            )
        }
        }
    }

    /**
     * ADR-015 removed heartbeat and takeover. Two agent surfaces still named them
     * as things to audit, which invites a finding that the fix is "missing".
     */
    @Test
    fun noAgentPromptAuditsDeliberatelyAbsentFeatures() {
        val adr = read("blueprints/decisions/ADR-015-pair-rooms.md")
        assertTrue(
            "premise check: ADR-015 is expected to record the removal",
            adr.contains("takeover", ignoreCase = true)
        )
        repoFilesUnder(".opencode/agents", ".md").forEach { agent ->
            val text = agent.readText()
            listOf(
                "heartbeat, takeover",
                "takeover guards",
                "heartbeat/takeover"
            ).forEach { stale ->
                assertFalse(
                    agent.path + " still asks for \"$stale\" as a thing to audit. " +
                        "ADR-015 removed both ('no busy/takeover logic any more') and no " +
                        "heartbeat writer exists anywhere in app/src; an auditor told to " +
                        "check it reports a removed feature as missing.",
                    text.contains(stale, ignoreCase = true)
                )
            }
            // "topic subscription" is only acceptable as the thing that is ABSENT.
            text.lineSequence()
                .filter { it.contains("topic subscription", ignoreCase = true) }
                .filterNot { line ->
                    val lower = line.lowercase()
                    lower.contains("no topic") || lower.contains("there is no") ||
                        lower.contains("zero match") || lower.contains("not ")
                }
                .forEach { line ->
                    assertTrue(
                        agent.path + " tells the auditor to check topic subscription, " +
                            "but push is token-targeted by decision (ADR-015 §2) and " +
                            "subscribeToTopic has zero matches in the tree. Line: $line",
                        false
                    )
                }
        }
    }

    /**
     * "no google-services.json until ADR-002" in a task slice is an unbuildable
     * instruction. It has now appeared twice (setup-android-studio.md, BP-04).
     */
    @Test
    fun noDocTellsTheOperatorToSkipGoogleServicesJson() {
        // Scanned everywhere an operator could be pointed at the file, not just
        // blueprints: this claim has now appeared in setup-android-studio.md,
        // BP-04 and SPEC_SHEET.md, and it was fixed in each one separately.
        val docs = repoFilesUnder("blueprints", ".md") +
            repoFilesUnder("docs", ".md") +
            listOf(File(repoRoot, "SPEC_SHEET.md"))
        docs.forEach { doc ->
            val text = doc.readText()
            assertFalse(
                doc.path + " instructs the operator to withhold app/google-services.json. " +
                    "ADR-002 is DECIDED, Firebase is the shipped signaling + wakeup path " +
                    "(ADR-015), and the file is required to build -- a fresh clone " +
                    "following this line produces an app that cannot compile.",
                text.contains("no `google-services.json` until ADR-002") ||
                    text.contains("no google-services.json until ADR-002")
            )
        }
    }

    /**
     * `SPEC_SHEET.md` and `ARCHITECTURE.md` open with a HISTORICAL banner, which
     * covers a plan that was *superseded*. It does not cover a plan that was
     * *rejected* — Hilt, Room, SQLCipher and Concentus were never adopted, so a
     * reader who trusts the banner and skims to the "Technical contract" finds
     * a dependency list that cannot compile. `SPEC_SHEET.json` (machine truth)
     * records each as explicitly NOT-in-the-build; the prose had not caught up.
     */
    @Test
    fun noHistoricalBannerHidesARejectedStack() {
        val catalog = read("gradle/libs.versions.toml")
        val rejected = listOf("Hilt", "Room", "SQLCipher", "OkHttp", "Concentus", "KSP")
        listOf(
            "SPEC_SHEET.md",
            "blueprints/ARCHITECTURE.md",
            "blueprints/CALL_DAD_MASTER_BLUEPRINT.md"
        ).forEach { path ->
            val text = read(path)
            assertTrue(
                path + " should still carry its HISTORICAL banner",
                text.contains("HISTORICAL")
            )
            // Split into `## ` sections. A section is allowed to name a rejected
            // dependency as PROPOSED if that section opens by saying so; every
            // other section must negate it on the line itself. Without the
            // section split, the correctly-flagged "as proposed" list trips the
            // same guard that exists to catch an unmarked claim.
            val sections = text.split("\n## ").map { section ->
                val lines = section.lines()
                val body = lines.joinToString("\n")
                // Deliberately NOT "NOT BUILT": that phrase appears INSIDE a section
                // as a line-level correction, and honouring it at section level would
                // exempt the whole section — which is how this guard gets defeated
                // by its own fix.
                val flagged = listOf("NOT WHAT SHIPPED", "AS PROPOSED", "HISTORICAL")
                    .any { body.contains(it) }
                lines to flagged
            }
            sections.forEach { (lines, flagged) ->
                lines.forEach { line ->
                    rejected.forEach { dep ->
                        if (!line.contains(dep)) return@forEach
                        val negated = listOf("no ", "not ", "absent", "deferred")
                            .any { line.contains(it, ignoreCase = true) }
                        val inCatalog = catalog.contains(dep)
                        assertTrue(
                            path + " names the rejected dependency \"$dep\" " +
                                "(catalog has it: $inCatalog) as if it were part of " +
                                "the shipped stack. Either negate it on the line or " +
                                "put it under a section that opens by saying it is " +
                                "the proposal and not what shipped. Line: $line",
                            inCatalog || negated || flagged
                        )
                    }
                }
            }
        }
    }

    /** A dep list that is not in the catalog is an operator step that cannot compile. */
    @Test
    fun noBlueprintHumanStepAddsADependencyThatIsNotInTheCatalog() {
        val catalog = read("gradle/libs.versions.toml")
        val phantom = listOf("Hilt", "Room", "SQLCipher", "OkHttp", "Concentus", "KSP")
        repoFilesUnder("blueprints/blueprint-sections", ".md").forEach { doc ->
            doc.readText().lineSequence()
                .filter { it.contains("Deps:") || it.contains("Add deps") }
                .forEach { line ->
                    phantom.forEach { dep ->
                        // "No Hilt, no Room, ..." is the correction, not the defect.
                        val negated = Regex("(?i)\\bno\\s+" + Regex.escape(dep) + "\\b")
                            .containsMatchIn(line)
                        assertTrue(
                            doc.path + " tells the operator to add \"$dep\", which is " +
                                "not in gradle/libs.versions.toml (the catalog is the " +
                                "only source of truth, ADR-004). Line: $line",
                            negated || catalog.contains(dep) || !line.contains(dep)
                        )
                    }
                }
        }
    }

    /**
     * Counts drift silently because nothing breaks when they are wrong: 518 vs
     * 520 reads as a rounding difference. Pin them, and pin the unit that is
     * stable across runs.
     *
     * Second half of the pin: the numbers recorded in the docs must equal the
     * numbers the suite has. Counting the source is the easy direction and it is
     * the direction that was already checked -- the recorded text was free to
     * drift from it, and did (it said 520 after the suite was 536).
     */
    @Test
    fun theRecordedTestCountsMatchTheTestSource() {
        val classes = repoFilesUnder("app/src/test", ".kt")
        assertTrue(
            "test class count changed (now ${classes.size}) -- update the counts in " +
                "CURRENT_STATE.md, CHECKPOINTS.md, SESSION_HANDOFF.md and README.md in " +
                "the same commit",
            classes.size == 27
        )
        val testMethods = classes.sumOf { classFile ->
            Regex("""^\s*@Test""", RegexOption.MULTILINE).findAll(classFile.readText()).count()
        }
        assertTrue(
            "expected 283 @Test methods across the suite (566 across both flavors) " +
                "but found $testMethods -- update the recorded gate counts",
            testMethods == 283
        )

        // The recorded TEXT, not just the source. These are the four files that
        // state the count as current; a stale number there is the drift the pin
        // exists to catch, and pinning only the source direction leaves it open.
        listOf(
            "blueprints/CURRENT_STATE.md",
            "blueprints/CHECKPOINTS.md",
            "SESSION_HANDOFF.md",
            "README.md"
        ).forEach { path ->
            val text = read(path)
            assertTrue(
                path + " records $testMethods tests per flavor / ${testMethods * 2} " +
                    "across both flavors; it must match the suite or a reader is " +
                    "quoting a number that was true of an older commit",
                text.contains("${testMethods * 2}") &&
                    (text.contains("$testMethods") || text.contains("$testMethods "))
            )
        }
    }
}
