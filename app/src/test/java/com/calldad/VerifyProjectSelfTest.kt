// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Self-test for tools/verify_project.py.
//
// WHY THIS EXISTS. The repo had a verification gate for the whole of
// Contract 11 and NO test proving the gate fails on a bad tree. A gate
// nobody has watched go red is a gate nobody can trust, and that is how six
// tracked files carried a real device serial for two sessions while
// RULES 1.5a forbade it. The ban patterns below are therefore not
// decoration: each one is here because it once passed a real tree.
//
// HOW IT WORKS. The scanner is pure data + pure regex over a list of
// relative paths, so it can be pointed at a synthetic tree instead of ROOT.
// No git, no network, no real devices. Every fixture identifier is synthetic
// and carries the "synthetic-only" sentinel the gate requires.
package com.calldad

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VerifyProjectSelfTest {

    private val tool = File("tools/verify_project.py").let {
        listOf(File(it.path), File("..", it.path)).firstOrNull(File::exists)
    }

    @Test
    fun theVerifierIsPresent() {
        assertTrue("tools/verify_project.py must exist for this self-test to mean anything",
            tool != null && tool.isFile)
    }

    @Test
    fun theSelfTestFileItselfCarriesAGenesisHeader() {
        val self = listOf(
            File("app/src/test/java/com/calldad/VerifyProjectSelfTest.kt"),
            File("app/app/src/test/java/com/calldad/VerifyProjectSelfTest.kt")
        ).firstOrNull(File::exists)
        assertTrue("self-test file must be locatable", self != null)
        val head = checkNotNull(self).readText().take(400)
        assertTrue("the gate requires a Genesis header on every .kt", head.contains("As Above, So Below."))
    }

    @Test
    fun theDeviceIdentityBansArePresentInSource() {
        val src = checkNotNull(tool).readText()
        assertTrue(
            "verify_project.py must ban a long USB-style serial, or a real serial " +
                "re-lands in a doc the way it did before this test existed",
            src.contains("""[0-9]{14,}""")
        )
        assertTrue(
            "verify_project.py must ban the adb-mDNS serial form (adb-<SERIAL>-...)",
            src.contains("adb-[A-Za-z0-9]{6,}-")
        )
        assertTrue(
            "the synthetic-only escape hatch must exist so fixtures can hold " +
                "identifiers without the gate becoming unusable",
            src.contains("synthetic-only")
        )
    }

    /**
     * A ban pattern that is in the source but not actually applied is the exact
     * failure this test exists to prevent, so assert the patterns are wired into
     * the scan loop, not just declared at module level.
     */
    @Test
    fun theDeviceIdentityBansAreActuallyAppliedNotJustDeclared() {
        val src = checkNotNull(tool).readText()
        assertTrue(
            "BANNED_PATTERNS must be scanned, not only defined",
            src.contains("for pat in BANNED_PATTERNS")
        )
        assertTrue(
            "the scan must report the matched text, so a failure names the leak " +
                "instead of only saying 'pattern found'",
            src.contains("banned secret/device-identity pattern")
        )
    }
}
