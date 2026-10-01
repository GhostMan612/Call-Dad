// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host test: RULES 1.4a tool-use discipline must not silently regress.
//
// WHY THIS EXISTS. Three days of a plan were dominated by shell round-trips
// for things the read/grep/glob/edit tools already do. The cause was not
// discipline, it was that no document said how to READ or EDIT a file -- they
// all listed commands to RUN. An agent following the docs literally had no
// instruction that shelling for a file read was wrong, and `bash: {"*":
// "allow"}` in opencode.json permitted it by default. This test pins both the
// prose and the machine layer, because a rule that only exists in one of them
// is a rule that will drift.
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolUseDisciplineTest {

    private fun read(path: String): String {
        val file = listOf(File(path), File("app", path), File("..", path))
            .firstOrNull { it.exists() }
        checkNotNull(file) { "could not find " + path }
        return file.readText()
    }

    private fun exists(path: String): Boolean =
        listOf(File(path), File("app", path), File("..", path)).any { it.exists() }

    // ---- the machine layer: opencode.json must actually deny the shell reads ----

    private val config = read("opencode.json")

    @Test
    fun openCodeDeniesShellFileReadsAndWrites() {
        // Each of these has a dedicated tool. If one is merely "allow" again, an
        // agent has no signal that shelling is wrong and the habit returns.
        listOf(
            "Get-Content", "Select-String", "findstr", "Get-ChildItem",
            "Test-Path", "Set-Content", "Out-File", "cat", "type", "head", "tail"
        ).forEach { cmd ->
            assertTrue(
                "opencode.json must pin \"$cmd*\": \"deny\" -- it has a dedicated tool",
                Regex("\"$cmd\\*\"\\s*:\\s*\"deny\"").containsMatchIn(config)
            )
        }
    }

    @Test
    fun theRealDenyListIsNotSilentlyReorderedUnderTheWildcard() {
        // bash "*": "allow" is still the default, so the specific denies are the
        // ONLY thing stopping a shell file read. They must be present and last-
        // matching must not win. This asserts the wildcard is not the sole rule.
        assertTrue(
            "opencode.json should keep a wildcard default but with explicit denies",
            config.contains("\"*\": \"allow\"") && config.contains("\"deny\"")
        )
        assertFalse(
            "read/search tools must not be denied wholesale -- they are the sanctioned path",
            config.contains("\"read\": \"deny\"") || config.contains("\"grep\": \"deny\"")
        )
    }

    @Test
    fun theBuildBoundaryDenyListSurvived() {
        // A rules rewrite must not quietly relax these while adding the new ones.
        listOf("firebase*deploy*", "adb*uninstall*", "adb*push*", "adb*root*")
            .forEach { rule ->
                assertTrue(
                    "opencode.json lost the deny for $rule",
                    Regex("\"" + Regex.escape(rule) + "\"\\s*:\\s*\"deny\"").containsMatchIn(config)
                )
            }
    }

    /**
     * The deny list is a NAME list, so anything that executes arbitrary code is
     * a hole: it can read or write a file no matter which names are denied.
     *
     * `python -c` and `node -e` are the dangerous ones. PowerShell mangles
     * their quoting, and a mangled `python -c` has corrupted files in the
     * sibling repos. `bash`/`sh`/`wsl`/`cmd` are worse: they start a whole new
     * shell in which NONE of these patterns apply, so `bash -c "cat foo"`
     * evades every entry in the list.
     */
    @Test
    fun arbitraryCodeExecutionIsDenied() {
        listOf(
            "python*-c*", "python*-m*", "node*-e*", "node*--eval*",
            "bash*", "sh*", "wsl*", "cmd*",
            "Start-Process*", "Invoke-Expression*"
        ).forEach { pattern ->
            assertTrue(
                "opencode.json must deny \"$pattern\" -- it executes arbitrary code " +
                    "and bypasses every other entry in the list",
                Regex("\"" + Regex.escape(pattern) + "\"\\s*:\\s*\"deny\"").containsMatchIn(config)
            )
        }
    }

    @Test
    fun theDenyListDoesNotBlockLegitimateWork() {
        // Regression guard for a real session failure: "write*": "deny" matched
        // ANY command containing the substring "write", so `$out = ...` and a
        // plain `git commit` were both blocked and the agent could not commit
        // its own work. A deny list that stops version control is worse than
        // useless. The file-writing intent is already covered precisely by
        // Set-Content / Add-Content / Out-File.
        listOf("write*", "commit*", "output*", "show*", "view*", "read*").forEach { overbroad ->
            assertFalse(
                "\"$overbroad\": \"deny\" is too broad and collides with legitimate " +
                    "commands; use the specific cmdlet instead (Set-Content, " +
                    "Add-Content, Out-File) or delegate to the read/grep/glob/edit tools",
                Regex("\"" + Regex.escape(overbroad) + "\"\\s*:\\s*\"deny\"").containsMatchIn(config)
            )
        }
    }

    // ---- the prose layer: the law must exist and be findable on cold start ----

    @Test
    fun rulesHasTheToolUseLawAndItIsSelfContained() {
        val rules = read("RULES.md")
        assertTrue(
            "RULES.md must contain the tool-use section",
            rules.contains("1.4a")
        )
        assertTrue(
            "RULES.md must map cat/Get-Content to the read tool",
            rules.contains("read") && rules.contains("Get-Content")
        )
        assertTrue(
            "RULES.md must map search commands to grep/glob",
            rules.contains("grep") && rules.contains("glob")
        )
        assertTrue(
            "RULES.md must require batching independent lookups",
            rules.contains("batch") || rules.contains("Batch")
        )
        assertTrue(
            "RULES.md must say the gate runs once at the END of a phase, not per edit",
            rules.contains("END of a phase") || rules.contains("end of a phase")
        )
    }

    @Test
    fun agentsMdPutsTheToolRuleBeforeTheCommandList() {
        // AGENTS.md is the cold-start ramp. If the commands appear before the
        // tool rule, an agent reads "here are the commands to run" and stops.
        val agents = read("AGENTS.md")
        val toolRule = agents.indexOf("1.4a")
        val firstCommand = agents.indexOf("gradlew")
        assertTrue("AGENTS.md must reference the tool-use law", toolRule >= 0)
        assertTrue("AGENTS.md still has gate commands", firstCommand >= 0)
        assertTrue(
            "AGENTS.md must state the tool-use rule BEFORE the command list",
            toolRule in 0 until firstCommand
        )
    }

    @Test
    fun noDocTeachesThePipeFilterIdiom() {
        // "2>&1 | Select-Object -Last 5" is how the habit was taught: it teaches
        // an agent to reach for the shell to inspect output. It is banned in the
        // agent-facing docs. Operator runbooks are exempt (a human runs those).
        listOf("AGENTS.md", "CLAUDE.md", "blueprints/CHECKPOINTS.md").forEach { doc ->
            if (!exists(doc)) return@forEach
            assertFalse(
                doc + " still teaches the `| Select-Object -Last N` filter idiom",
                read(doc).contains("| Select-Object -Last")
            )
        }
    }

    @Test
    fun theSkillAndGateRunnerCarryTheDiscipline() {
        // The skill is what loads into every session; gate-runner is the only
        // fleet agent with shell access. Both need the rule stated.
        val skill = read(".opencode/skills/calldad-conventions/SKILL.md")
        assertTrue(
            "the skill must state the tool-use rule",
            skill.contains("last resort") || skill.contains("1.4a")
        )
        val runner = read(".opencode/agents/gate-runner.md")
        assertTrue(
            "gate-runner must be told it is a phase-closing step, not a per-edit step",
            runner.contains("phase") && runner.contains("edit")
        )
    }

    /**
     * A subagent's `description` is the DISPATCH SURFACE -- it is what a router
     * matches on to decide whether to invoke the agent. The body only reaches the
     * agent after it has already been invoked.
     *
     * So a description saying "use before declaring any change done" is a
     * per-change router trigger, and the body's "once per phase" instruction
     * never gets a chance to matter. This is exactly the leak RULES 1.4a exists
     * to close, sitting in the description rather than the body, which is why
     * the body-only assertion above could never catch it.
     */
    @Test
    fun gateRunnerDescriptionMatchesItsOwnPhaseClosingBody() {
        val runner = read(".opencode/agents/gate-runner.md")
        val front = runner.substringBefore("mode: subagent")
        val description = Regex("""description:\s*(.+)""").find(front)?.groupValues?.get(1).orEmpty()
        assertTrue("gate-runner must have a description", description.isNotBlank())

        val perChange = listOf(
            "before declaring any change", "per change", "each change",
            "every change", "after each edit", "before each edit"
        )
        perChange.forEach { phrase ->
            assertFalse(
                "gate-runner's DESCRIPTION says \"$phrase\" -- that is a per-change " +
                    "router trigger and contradicts its own phase-closing body. " +
                    "The description is matched on; the body is read after. Found: $description",
                description.contains(phrase, ignoreCase = true)
            )
        }
        assertTrue(
            "the description should positively state that it runs ONCE at the END of " +
                "a phase, not per edit. Found: $description",
            description.contains("END of a phase") || description.contains("once")
        )
    }

    /**
     * The `gate` TOOL is the only sanctioned way to run a Gradle test or lint
     * task. Everything above this test pins a PROXY: the deny list, the wording
     * in the docs. Those were both true while the agent was running
     * `:app:testParentDebugUnitTest` six times in one phase, once per failing
     * test -- exactly the loop RULES 1.4a exists to end, with the gate green
     * throughout.
     *
     * No test inside this process can observe what the agent actually typed. So
     * this pins the thing that can be pinned: the sanctioned command is named in
     * the same place the rule is stated, and no document teaches a bare
     * gradlew test invocation as the thing to reach for. The operator runbooks
     * in `.opencode/commands/` are the deliberate exception -- a human runs
     * those -- so they are excluded rather than being treated as violations.
     */
    @Test
    fun theGateToolIsWhatDocumentsTellYouToRun() {
        val lawDocs = listOf(
            "AGENTS.md", "RULES.md", "CLAUDE.md",
            ".opencode/skills/calldad-conventions/SKILL.md"
        )
        lawDocs.filter { exists(it) }.forEach { doc ->
            val text = read(doc)
            assertTrue(
                "$doc must tell the agent that the `gate` TOOL is how a gate runs. " +
                    "The raw gradle task is available to the agent, and naming only " +
                    "the task names is what made the per-edit loop the path of least " +
                    "resistance.",
                text.contains("gate tool") || text.contains("`gate`") ||
                    text.contains("gate-runner") || text.contains("/verify")
            )
        }

        // The failure mode is a document SHOWING the raw invocation as something
        // to type. Operator runbooks legitimately contain it (a human runs them),
        // so scope this to the documents an agent follows.
        val agentFacing = listOf(
            ".opencode/skills/calldad-conventions/SKILL.md",
            ".opencode/agents/gate-runner.md"
        )
        agentFacing.filter { exists(it) }.forEach { doc ->
            val text = read(doc)
            assertFalse(
                "$doc must not present a bare gradle test invocation as the command " +
                    "to run. Use the `gate` tool instead. Found:\n$text",
                Regex("""(?m)^\s*\.?\\?gradlew(\.bat)?\s+:app:test""").containsMatchIn(text)
            )
        }
    }

    @Test
    fun theLawSaysGateOncePerPhaseNotPerEdit() {
        val rules = read("RULES.md")
        assertTrue(
            "RULES 1.4a must state that the gate runs ONCE at the END of a phase. " +
                "Without the word 'once' alongside the phase-closing rule, the " +
                "per-edit loop is a defensible reading.",
            rules.contains("once", ignoreCase = true) &&
                rules.contains("END of a phase", ignoreCase = true)
        )
        assertTrue(
            "RULES 1.4a must forbid gating per edit explicitly, since that is the " +
                "exact behaviour that recurred after the rule was first written",
            rules.contains("not per edit", ignoreCase = true)
        )
    }

    @Test
    fun readOnlyAuditorsStillHaveNoShellAccess() {
        // Six auditors run with `bash: deny`, which is why they were never the
        // source of the shelling. Guard it: widening one reopens the hole.
        listOf(
            "call-core-auditor", "webrtc-media", "firestore-rules-auditor",
            "fcm-wakeup-auditor", "doc-drift-auditor", "handoff-writer"
        ).forEach { agent ->
            if (!exists(".opencode/agents/$agent.md")) return@forEach
            val text = read(".opencode/agents/$agent.md")
            assertTrue(
                "$agent must keep `bash: deny`",
                text.contains("bash: deny") || text.contains("\"bash\": \"deny\"")
            )
        }
    }
}
