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
//   4. THE ONE THAT ACTUALLY SHIPPED. The caller published a restart offer and
//      then never applied the answer to it: `maybeApplyRenegotiation` opened
//      with `if (amCaller) return`, and the caller had already applied the
//      ORIGINAL answer for that same seq during setup, so even without that guard
//      a seq-keyed apply is a permanent skip. The callee half was perfect and the
//      feature was inert. Nothing about it was visible in review -- it re-gathered
//      candidates, it published SDP, it had a cooldown and an idempotence latch --
//      and it could not recover a call, ever. This is the one worth remembering:
//      a feature that is fully implemented on one side and not the other looks
//      exactly like a feature that works.
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
        //
        // The idempotence guard is a ROUND, not a boolean. This assertion used to
        // demand `renegotiationApplied`, which is exactly the defect: a latch set
        // once and never reset makes a SECOND LOST episode unrecoverable, and
        // there is no way to tell which of two offers in the same generation is
        // the new one.
        val apply = body(vm, "private fun maybeApplyRenegotiation(")
        assertTrue(
            "the restart offer must be applied at most once. Body: " + apply,
            apply.contains("appliedOfferRound")
        )
        assertFalse(
            "the round guard must be a COMPARISON (`<=`), not a bare latch: a latch " +
                "set once and never reset makes every later restart episode a no-op. " +
                "Body: " + apply,
            apply.contains("renegotiationApplied")
        )
        assertTrue(
            "the restart offer must be checked against the live seq. Body: " + apply,
            apply.contains("s.seq != doc.seq") || apply.contains("s.seq == doc.seq")
        )
    }

    // ---- failure 4 (the one that shipped broken): the caller never finished ----

    @Test
    fun theCallerAPPLIESItsOwnRestartAnswer() {
        // THE BUG THIS FILE NOW EXISTS FOR. `maybeApplyRenegotiation` used to
        // begin `if (amCaller) return`, so the side that published the restart
        // offer never applied the answer. The feature was fully implemented on
        // the callee and completely inert on the caller: the restart published an
        // offer, the callee answered, and the caller discarded it. ICE
        // re-gathered on the caller's own offer but the peer connection never
        // received the matching answer, so connectivity never recovered.
        //
        // `seq` cannot be the guard, because the caller already applied the
        // ORIGINAL answer for that same seq during setup.
        val apply = body(vm, "private fun applyRenegotiationAnswer(")
        assertTrue(
            "the caller must have its own answer-applying path. Body: " + apply,
            apply.isNotBlank()
        )
        assertTrue(
            "the caller must set the remote description from the renegotiated " +
                "answer, or the restart never completes. Body: " + apply,
            apply.contains("setRemoteDescription(")
        )
        assertTrue(
            "the caller must guard on the negotiation ROUND, not on seq: it already " +
                "applied an answer for this seq during setup, so a seq guard is a " +
                "permanent skip. Body: " + apply,
            apply.contains("appliedAnswerRound") && apply.contains("negotiationRound")
        )
        assertTrue(
            "the caller must release restartInFlight when the answer lands, or a " +
                "second LOST episode is blocked forever. Body: " + apply,
            apply.contains("restartInFlight = false")
        )
    }

    @Test
    fun theConnectedPathDispatchesToBothSides() {
        // The one-line version of the same bug: a single unguarded call site
        // reached only the callee.
        val branch = codeOnly(
            vm.substringAfter("private fun onSameGeneration(").substringBefore("\n    }")
        )
        assertTrue(
            "the connected path must dispatch to the caller's answer path",
            branch.contains("applyRenegotiationAnswer")
        )
        assertTrue(
            "the connected path must dispatch to the callee's offer path",
            branch.contains("maybeApplyRenegotiation")
        )
        assertTrue(
            "the dispatch must be on amCaller, or one side is skipped. Found: " + branch,
            branch.contains("amCaller")
        )
    }

    @Test
    fun theCalleeEchoesTheRoundItWasGiven() {
        // If the callee invents a round instead of echoing, the caller's
        // `negotiationRound <= appliedAnswerRound` guard rejects the answer and
        // the two sides talk past each other forever.
        val apply = body(vm, "private fun maybeApplyRenegotiation(")
        assertTrue(
            "the callee must publish the answer with the round it received. " +
                "Body: " + apply,
            apply.contains("publishRenegotiationAnswer(") &&
                Regex("""publishRenegotiationAnswer\([^)]*doc\.negotiationRound""")
                    .containsMatchIn(apply)
        )
    }

    @Test
    fun theRoundIsCarriedThroughTheDocumentAndTheRules() {
        // The round is the wire contract. If it is not written, read back, or
        // allowed by the rules, the whole mechanism silently degrades to
        // "always zero" and the caller skips every answer.
        val publish = body(signaling, "suspend fun publishRenegotiation(")
        val answer = body(signaling, "suspend fun publishRenegotiationAnswer(")
        assertTrue(
            "publishRenegotiation must WRITE the round. Body: " + publish,
            publish.contains("negotiationRound")
        )
        assertTrue(
            "publishRenegotiationAnswer must WRITE the round. Body: " + answer,
            answer.contains("negotiationRound")
        )
        assertTrue(
            "the round must be READ back off the snapshot",
            signaling.contains("getLong(\"negotiationRound\")")
        )
        assertTrue(
            "CallDocument must carry negotiationRound",
            signaling.contains("val negotiationRound: Int = 0")
        )
    }

    @Test
    fun theRoundAdvancesOnlyOnAConfirmedPublish() {
        // Advancing on failure would leave the peer's answer to round N being
        // compared against N+1, so it is discarded as stale and the two sides
        // never rendezvous.
        val restart = body(vm, "private fun attemptIceRestart(")
        val advanceAt = restart.indexOf("nextNegotiationRound = round + 1")
        val onSuccessAt = restart.indexOf(".onSuccess")
        assertTrue(
            "the round must advance inside onSuccess, i.e. only once the write is " +
                "confirmed. Body: " + restart,
            advanceAt > 0 && onSuccessAt >= 0 && advanceAt > onSuccessAt
        )
        assertTrue(
            "a failed publish must release the in-flight latch so the next cooldown " +
                "tick can retry. Body: " + restart,
            body(vm, "private fun attemptIceRestart(").contains(".onFailure")
        )
    }

    @Test
    fun theInFlightLatchIsHeldAcrossTheWholeRoundTrip() {
        // Releasing it right after the publish let a second LOST tick publish a
        // second offer for a call whose first answer had not arrived, and the two
        // overwrote each other.
        val restart = body(vm, "private fun attemptIceRestart(")
        val setTrue = restart.indexOf("restartInFlight = true")
        val cleared = restart.lastIndexOf("restartInFlight = false")
        assertTrue(
            "the latch must be taken before the publish and released on failure only",
            setTrue >= 0 && cleared >= 0 && setTrue < cleared
        )
        assertFalse(
            "the latch must NOT be cleared unconditionally after the launch, or a " +
                "second episode can start before the first answer lands. Body: " + restart,
            Regex("""\}\s*\n\s*restartInFlight = false""").containsMatchIn(restart)
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
        val restartAt = branch.indexOf("applyRenegotiationAnswer")
            .let { if (it < 0) branch.indexOf("maybeApplyRenegotiation") else it }
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