// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Host tests for the ICE-restart reconnect path (ADR-017, K21 sibling).
//
// WHY THIS IS A SOURCE-SCAN TEST. The reconnect path touches libwebrtc media
// constraints and a Firestore transaction; a host JVM cannot create a
// PeerConnection or run a transaction. What CAN be pinned here is the part
// that has silently broken before: a reconnect implemented in a way that
// looks right but re-rings the peer, drops the generation, or races itself.
//
// THE THREE FAILURES THIS FILE EXISTS FOR, each a real way this feature
// breaks while still "having restartIce":
//
//   1. Reusing publishOffer for the restart. publishOffer starts a new
//      generation (seq+1, status RINGING, both candidate arrays cleared).
//      A reconnect that does that re-rings the other phone mid-call, drops
//      every candidate already exchanged, and makes the peer's
//      generation-checked teardown kill a call that is still live.
//
//   2. Forgetting the IceRestart constraint. createOffer() with no
//      constraint produces a valid SDP that libwebrtc will accept and then
//      refuse to re-gather, so ICE reconverges on the same dead candidates.
//      The feature appears to exist and does nothing.
//
//   3. Both sides restarting at once. Each side's OFFER overwrites the
//      other's, leaving one waiting on an SDP already replaced.
//
// As with QuietNotificationTest, these strip comments before scanning:
// the fix's own comments name IceRestart and publishOffer to DESCRIBE the
// bug, so a raw scan would match the prose and pass forever.
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IceRestartTest {

    private fun read(path: String): String {
        val file = listOf(File(path), File("app", path)).firstOrNull(File::exists)
        checkNotNull(file) { "could not find " + path }
        return file.readText()
    }

    private val client = read("src/main/java/com/calldad/webrtc/WebRTCClient.kt")
    private val signaling = read("src/main/java/com/calldad/data/signaling/SignalingClient.kt")
    private val vm = read("src/main/java/com/calldad/ui/screens/CallViewModel.kt")
    private val screen = read("src/main/java/com/calldad/ui/screens/CallScreen.kt")

    /** A source scan must assert on CODE. The fix's comment names the bug. */
    private fun codeOnly(src: String): String = Regex("""//[^\n]*""").replace(src, "")

    /** The body of a function, anchored on its declaration, comments removed. */
    private fun body(src: String, signature: String): String =
        codeOnly(src.substringAfter(signature).substringBefore("\n    }"))

    // ---- failure 1: the restart must not start a new generation ----

    @Test
    fun theRestartDoesNotStartANewGeneration() {
        val offer = body(signaling, "suspend fun publishRenegotiation(")
        assertTrue("publishRenegotiation must exist for the reconnect path", offer.isNotBlank())
        assertFalse(
            "a reconnect must NOT bump seq -- publishOffer does seq+1, which re-rings " +
                "the peer mid-call and invalidates their generation-checked teardown. " +
                "Body: " + offer,
            Regex("""\bseq\s*\+\s*1\b""").containsMatchIn(offer) ||
                Regex("""nextSeq""").containsMatchIn(offer)
        )
        assertFalse(
            "a reconnect must NOT return the room to RINGING. Body: " + offer,
            offer.contains("\"RINGING\"")
        )
        assertFalse(
            "a reconnect must NOT clear the exchanged candidate arrays -- that is " +
                "what drops the working path. Body: " + offer,
            offer.contains("emptyList<Map<String, Any>>()")
        )
    }

    @Test
    fun theRestartStillRefusesToRunOnADeadGeneration() {
        // The generation guard is what makes a reconnect racing a hangup safe.
        // Dropping it would let a late restart resurrect an ended call.
        val offer = body(signaling, "suspend fun publishRenegotiation(")
        assertTrue(
            "the restart must still check seq and live status. Body: " + offer,
            offer.contains("LIVE_STATUSES") &&
                Regex("""seq""").containsMatchIn(offer)
        )
        assertTrue(
            "a stale restart must throw the same 'no longer ringing' signal the " +
                "rest of the client uses, so callers handle it identically",
            offer.contains("CallNoLongerRingingException")
        )
    }

    @Test
    fun theCallerSideNeverUsesPublishOfferForTheRestart() {
        val restart = body(vm, "private fun attemptIceRestart(")
        assertTrue("attemptIceRestart must exist", restart.isNotBlank())
        assertFalse(
            "attemptIceRestart must NOT call publishOffer -- that starts a new " +
                "generation and re-rings the peer. Body: " + restart,
            restart.contains("publishOffer(")
        )
        assertTrue(
            "attemptIceRestart must publish via publishRenegotiation. Body: " + restart,
            restart.contains("publishRenegotiation(")
        )
    }

    // ---- failure 2: the IceRestart constraint must actually be there ----

    @Test
    fun aRestartOfferCarriesTheIceRestartConstraint() {
        val offer = body(client, "suspend fun createOffer(")
        assertTrue(
            "createOffer must accept the iceRestart flag; without it there is no " +
                "restart. Body: " + offer,
            offer.contains("iceRestart")
        )
        assertTrue(
            "createOffer must pass the flag through to setLocalAndAwait. Body: " + offer,
            Regex("""setLocalAndAwait\([^)]*iceRestart""").containsMatchIn(offer)
        )
    }

    @Test
    fun theConstraintIsAddedAndNotMerelyAccepted() {
        // A parameter that is accepted and ignored is the exact shape of the
        // silent no-op this guards. The flag has to reach the constraints.
        val setLocal = body(client, "private suspend fun setLocalAndAwait(")
        assertTrue(
            "setLocalAndAwait must take the iceRestart parameter. Body: " + setLocal,
            setLocal.contains("iceRestart")
        )
        assertTrue(
            "the IceRestart media constraint must actually be added to the " +
                "MediaConstraints -- libwebrtc only re-gathers when this key is " +
                "present, so an absent key is a feature that silently does " +
                "nothing. Body: " + setLocal,
            setLocal.contains("\"IceRestart\"")
        )
        assertFalse(
            "the IceRestart constraint must not be mandatory-on always -- a normal " +
                "first offer would then re-gather needlessly. Body: " + setLocal,
            Regex("""if\s*\(\s*true\s*\)""").containsMatchIn(setLocal)
        )
    }

    // ---- failure 3: one side restarts, both sides apply ----

    @Test
    fun onlyOneSideEverPublishesTheRestart() {
        val restart = body(vm, "private fun attemptIceRestart(")
        assertTrue(
            "the restart must be caller-side only: both sides restarting at once " +
                "would overwrite each other's SDP and leave one waiting on a " +
                "replaced offer. Body: " + restart,
            restart.contains("!amCaller")
        )
        assertTrue(
            "the restart must be rate-limited, or a phone in a dead zone publishes " +
                "an offer per second for the whole grace window. Body: " + restart,
            restart.contains("LOST_RESTART_COOLDOWN_MS")
        )
        assertTrue(
            "the restart must be single-flight (guarded while one is in flight)",
            restart.contains("restartInFlight")
        )
    }

    @Test
    fun theCalleeAppliesTheRestartAndAnswersIt() {
        val apply = body(vm, "private fun maybeApplyRenegotiation(")
        assertTrue("maybeApplyRenegotiation must exist", apply.isNotBlank())
        assertTrue(
            "the callee must answer the restart OFFER. Body: " + apply,
            apply.contains("publishRenegotiationAnswer(")
        )
        assertFalse(
            "the callee must not publish a restart OFFER of its own. Body: " + apply,
            apply.contains("publishRenegotiation(")
        )
        assertTrue(
            "the restart answer must be applied to the peer connection, or ICE " +
                "never re-converges. Body: " + apply,
            apply.contains("setRemoteDescription(")
        )
    }

    @Test
    fun theApplyPathIsIdempotentAndGenerationChecked() {
        // The room listener fires repeatedly for one document. Applying the same
        // restart OFFER twice would renegotiate into a second, needless ICE
        // cycle, and a restart that ignores seq could apply to a newer call.
        val apply = body(vm, "private fun maybeApplyRenegotiation(")
        assertTrue(
            "the restart offer must be applied at most once. Body: " + apply,
            apply.contains("renegotiationApplied")
        )
        assertTrue(
            "the restart offer must be checked against the live seq. Body: " + apply,
            apply.contains("s.seq != doc.seq") || apply.contains("s.seq == doc.seq")
        )
    }

    @Test
    fun aTerminalDocumentWinsOverAPendingRestart() {
        // The ordering in onSameGeneration: a hangup observed in the same
        // snapshot as a restart must end the call, never answer the restart.
        // Getting this backwards would let a reconnect resurrect a dead call.
        val branch = codeOnly(
            vm.substringAfter("private fun onSameGeneration(").substringBefore("\n    }")
        )
        val endedAt = branch.indexOf("REMOTE_HANGUP")
        val restartAt = branch.indexOf("maybeApplyRenegotiation")
        assertTrue(
            "the terminal-state branch must exist",
            endedAt >= 0 && restartAt >= 0
        )
        assertTrue(
            "the ENDED/DECLINED check must come BEFORE the renegotiation branch, so " +
                "a hangup in the same snapshot as a restart always wins",
            endedAt < restartAt
        )
    }

    // ---- the kid is never stranded ----

    @Test
    fun theKidIsStillReturnedHomeIfTheRestartFails() {
        val watch = body(vm, "private fun startConnectionWatch(")
        assertTrue(
            "the LOST grace must still end the call -- a reconnect must never " +
                "leave a child stuck on a dead call screen. Body: " + watch,
            watch.contains("LOST_GRACE_MS") && watch.contains("NETWORK_FAILURE")
        )
        // Anchored on the GIVE-UP DECISION, not on `lostTooLong =`. The
        // threshold is computed in every loop iteration; only the `if` that
        // ends the call is the point of no return, and ordering the restart
        // against the assignment would be asserting about the wrong line.
        val restartAt = watch.indexOf("attemptIceRestart()")
        val giveUpAt = watch.indexOf("if (neverConnected || lostTooLong)")
        assertTrue(
            "the connection watch must contain both the restart call and the " +
                "give-up branch. Body: $watch",
            restartAt >= 0 && giveUpAt >= 0
        )
        assertTrue(
            "the restart must be attempted BEFORE the give-up branch, not after " +
                "(after it, it would be dead code -- the call is already ended). " +
                "Body: $watch",
            restartAt < giveUpAt
        )
    }

    @Test
    fun theUiDoesNotPromiseMoreThanTheCodeDelivers() {
        val lost = codeOnly(
            screen.substringAfter("ConnectionHealth.LOST").substringBefore("}\n")
        )
        assertTrue(
            "RECONNECTING is still unassigned, so it must not be the string the " +
                "screen renders -- that would repeat the 2026-09-25 lie",
            lost.contains("Connection lost")
        )
        assertFalse(
            "the stale comment claiming restartIce appears nowhere in the app " +
                "must be gone; it now contradicts the code",
            codeOnly(screen).contains("no restartIce() anywhere")
        )
    }
}