// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host test: every terminal room write must be generation-checked (Contract 10).
//
// The bug this pins: SignalingClient.finishCallDetached(callId, status) took
// no seq and wrote status ENDED unconditionally, with exactly one caller --
// CallViewModel.onCleared(). A ViewModel clearing for generation n after the
// peer had already published generation n+1 therefore overwrote n+1's
// RINGING, cancelling a ring for a call this device never owned, with no
// error on either side. ADR-015 section 6 claimed this was impossible.
//
// SignalingClient needs a live FirebaseFirestore, so as with the PTT guards
// this reads the source and asserts the invariant. The device proof is the
// Contract 10 matrix: start a call from the parent, answer on the child, force
// the parent's activity closed mid-call, and confirm the child's call is not
// cancelled by the parent's teardown.
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GenerationCheckedTeardownTest {

    private fun read(path: String): String {
        val here = File(".").absolutePath
        val file = listOf(File(path), File("app", path)).firstOrNull { it.exists() }
        checkNotNull(file) { "could not find " + path + " from " + here }
        return file.readText()
    }

    private val signaling = read("src/main/java/com/calldad/data/signaling/SignalingClient.kt")
    private val callViewModel = read("src/main/java/com/calldad/ui/screens/CallViewModel.kt")

    @Test
    fun finishCallDetachedTakesASeq() {
        assertTrue(
            "finishCallDetached must take the current generation",
            signaling.contains("fun finishCallDetached(callId: String, seq: Int, status: String)")
        )
    }

    @Test
    fun finishCallDetachedIsATransactionGuardedOnSeq() {
        val body = signaling.substringAfter("fun finishCallDetached(").substringBefore("\n    }")
        assertTrue(
            "finishCallDetached must run in a transaction",
            body.contains("runTransaction")
        )
        assertTrue(
            "finishCallDetached must compare the room seq against ours",
            body.contains("snap.getLong(\"seq\")?.toInt() == seq")
        )
        assertTrue(
            "finishCallDetached must only write while the generation is live",
            body.contains("LIVE_STATUSES")
        )
        assertFalse(
            "finishCallDetached must not write status unconditionally",
            body.contains(".update(mapOf(\"status\"")
        )
    }

    @Test
    fun everyCallerPassesTheCurrentGeneration() {
        val matcher = Regex("finishCallDetached[(]([^)]*[)])").findAll(callViewModel)
        var found = false
        for (m in matcher) {
            found = true
            val args = m.groupValues[1]
            assertTrue(
                "every finishCallDetached call must pass a seq, found: " + m.value,
                args.contains("seq", ignoreCase = true)
            )
        }
        assertTrue("onCleared must call finishCallDetached at all", found)
    }

    @Test
    fun onPairChangedWritesTheRoomBeforeResettingToIdle() {
        // Was: a live call went straight to Idle and wrote nothing, so the peer
        // rang to its own no-answer timeout with no idea what happened, and any
        // in-flight startCall still wrote a teardown to the OLD room id.
        val body = callViewModel
            .substringAfter("private fun onPairChanged(")
            .substringBefore("roomJob = viewModelScope.launch")
        assertTrue(
            "a live call must write ENDED for its generation before resetting",
            body.contains("finishCallDetached")
        )
    }

    @Test
    fun iceCandidatesCarryTheirGeneration() {
        // Was: sendLocalCandidate captured pair/amCaller and suspended on
        // addIceCandidate with no attempt check, so generation-n candidates
        // were arrayUnion-ed into generation n+1's freshly-reset arrays after
        // the peer's connection was already built.
        val body = callViewModel
            .substringAfter("private fun sendLocalCandidate(")
            .substringBefore("\n    }")
        assertTrue(
            "the attempt must be captured before suspending",
            body.contains("val myAttempt = attempt")
        )
        assertTrue(
            "the candidate write must re-check the attempt",
            body.contains("if (attempt != myAttempt")
        )
    }
}
