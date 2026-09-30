// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host tests for the Contract 10 PTT honesty fixes (ADR-016 follow-up).
//
// WHY THIS IS A SOURCE-SCAN TEST AND NOT A BEHAVIOUR TEST.
// VoiceClipPttEngine needs a real Context, MediaRecorder, MediaPlayer and a
// live Firestore instance, so its branches are not reachable from a host JVM
// test. Rather than assert tautologies about logic copied into the test (a
// green test that passes no matter what the engine does — the exact defect
// this contract exists to kill), these tests read the engine's own source
// and assert the invariants that were previously violated. If a future edit
// reintroduces any of them, the gate goes red.
//
// The behavioural proof of each invariant is the Contract 10 device matrix in
// blueprints/CHECKPOINTS.md. These are the cheap tripwire.
package com.calldad

import com.calldad.ptt.PttAudioState
import com.calldad.ptt.PttFailureKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PttHonestyTest {

    private val engine = readEngine()

    private fun readEngine(): String {
        val candidates = listOf(
            "src/main/java/com/calldad/ptt/VoiceClipPttEngine.kt",
            "app/src/main/java/com/calldad/ptt/VoiceClipPttEngine.kt"
        )
        val file = candidates.map(::File).firstOrNull(File::exists)
        checkNotNull(file) { "VoiceClipPttEngine.kt not found from ${File(".").absolutePath}" }
        return file.readText()
    }

    // ---- "SENT!" must mean the write actually left the device ----

    @Test
    fun uploadIsAwaited_soAnOfflineSendCannotReportSuccess() {
        // Was: `.add(...)` discarded, so `runCatching` only ever caught the
        // synchronous path and the child got a green "SENT!" for a write that
        // was queued, offline, or rejected by un-deployed rules.
        assertTrue(
            "the ptt upload must be awaited, found no .await() on the add()",
            engine.contains("collection(\"ptt\").add(") ||
                engine.contains("collection(\"ptt\")\n")
        )
        assertTrue(
            "the ptt add() must be awaited before reporting success",
            Regex("""\.add\([\s\S]{0,400}?\)\s*\.await\(\)""").containsMatchIn(engine)
        )
    }

    // ---- delete only after a clip actually played ----

    @Test
    fun deletionIsGatedOnPlaybackCompletion() {
        // Was: `if (roomId != null) deleteClip(...)` ran unconditionally after
        // a runCatching, so a decode failure, a ring interrupt, and the
        // 30-minute staleness sweep each destroyed an unheard message.
        assertTrue(
            "deleteClip must be guarded by a playback-success check",
            Regex("""if\s*\(\s*played\s*\)[\s\S]{0,200}?deleteClip""").containsMatchIn(engine)
        )
        assertFalse(
            "a bare unconditional deleteClip must not exist in the play loop",
            Regex("""_incoming\.value = PttAudioState\.Idle\s*\n\s*if \(roomId != null\) deleteClip""")
                .containsMatchIn(engine)
        )
    }

    @Test
    fun stalenessSweepNoLongerDestroysUnplayedClips() {
        // Was: a clip older than MAX_CLIP_AGE_MS was deleteClip'd without ever
        // entering the play queue — six bedtime messages, each confirmed
        // "SENT!", silently annihilated.
        assertFalse(
            "no age-based clip deletion is allowed",
            engine.contains("MAX_CLIP_AGE_MS")
        )
    }

    @Test
    fun aFailedDeleteReleasesTheIdSoItCanBeRetried() {
        // Was: `deleteClip` had no failure path, so a failed delete left the
        // id in seenClips and the doc became a permanent orphan.
        assertTrue(
            "a failed delete must drop the id from seenClips",
            engine.contains("seenClips.remove(clipId)")
        )
    }

    @Test
    fun listenerErrorsSurfaceInsteadOfBeingSwallowed() {
        // Was: `if (err != null || snap == null) return@addSnapshotListener` —
        // a dead walkie-talkie was indistinguishable from a quiet parent.
        assertFalse(
            "the listener error path must not return silently",
            Regex("""if \(err != null \|\| snap == null\) return@addSnapshotListener""")
                .containsMatchIn(engine)
        )
        assertTrue(
            "a listener failure must emit PttAudioState.Error",
            Regex("""PttAudioState\.Error\(""")
                .let { engine.contains(it) }
        )
    }

    // ---- the 15s cap must not blame the child ----

    @Test
    fun maxDurationHasAnInfoListener_soTheCapIsNotSilent() {
        // Was: setMaxDuration(15_000) with no setOnInfoListener. The recorder
        // self-stopped, stop() then threw, the bytes were deleted, and the
        // child was told they failed to hold the button — after watching a
        // red "TALKING" screen for 30 seconds.
        assertTrue("setMaxDuration is present", engine.contains("setMaxDuration(MAX_CLIP_MS)"))
        assertTrue(
            "setMaxDuration must be paired with setOnInfoListener",
            engine.contains("setOnInfoListener")
        )
        assertTrue(
            "the cap must be tracked",
            engine.contains("recordLimitReached")
        )
    }

    // ---- the bytes must survive a size failure ----

    @Test
    fun bytesAreReadBeforeTheFileIsDestroyed() {
        // Was: `file.readBytes().also { file.delete() }` then the size check —
        // an over-size clip destroyed the recording before deciding it was
        // over-size.
        val readIdx = engine.indexOf("readBytes()")
        val deleteIdx = engine.indexOf("file.delete()", readIdx)
        val sizeIdx = engine.indexOf("MAX_CLIP_BYTES) error", readIdx)
        assertTrue("readBytes() not found", readIdx >= 0)
        assertTrue("the size check must come after the read, not after a delete", sizeIdx > readIdx)
        assertTrue("the file must be deleted explicitly, not via .also{}", deleteIdx > readIdx)
        assertFalse(
            "readBytes must not delete the file inline",
            engine.contains("readBytes().also { file.delete() }")
        )
    }

    // ---- the press/release race ----

    @Test
    fun aNewPressIsBlockedWhileASendIsStillResolving() {
        // Was: isTransmitting cleared synchronously on release, so an
        // impatient press-release-press recorded nothing and then shipped the
        // PREVIOUS clip under the new press's confirmation.
        val vm = readViewModel()
        assertTrue(
            "onPress must also guard on isSending",
            Regex("""fun onPress\(\) \{\s*\n\s*if \(_state\.value\.isTransmitting \|\| _state\.value\.isSending\) return""")
                .containsMatchIn(vm)
        )
    }

    private fun readViewModel(): String {
        val candidates = listOf(
            "src/main/java/com/calldad/ui/screens/PttViewModel.kt",
            "app/src/main/java/com/calldad/ui/screens/PttViewModel.kt"
        )
        val file = candidates.map(::File).firstOrNull(File::exists)
        checkNotNull(file) { "PttViewModel.kt not found" }
        return file.readText()
    }

    // ---- the contract types must stay reachable ----

    @Test
    fun pttErrorStateIsDeclaredAndReachable() {
        // Was: PttAudioState.Error was declared and handled by the ViewModel
        // but never constructed by any production code — a wired channel with
        // no door, which is how the walkie-talkie could fail silently.
        assertTrue(
            "PttAudioState.Error must remain part of the sealed interface",
            PttAudioState::class.java.declaredClasses.any { it.simpleName == "Error" }
        )
    }

    @Test
    fun pttFailureKindsStillCoverTheContract() {
        val kinds = PttFailureKind.entries.map { it.name }.toSet()
        listOf(
            "AUDIO_CONTENDED",
            "PERMISSION_DENIED",
            "AUDIO_FOCUS_LOST",
            "ENGINE_UNAVAILABLE",
            "TRANSPORT_ERROR",
            "UNKNOWN"
        ).forEach { assertTrue("missing PttFailureKind.$it", kinds.contains(it)) }
    }
}
