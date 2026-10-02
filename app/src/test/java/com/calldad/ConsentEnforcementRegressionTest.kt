// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ConsentEnforcementRegressionTest.kt — the kill switch, and whether it is one
// Location: app/src/test/java/com/calldad/ConsentEnforcementRegressionTest.kt
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The second audit, and a nastier class of defect than the first.
 *
 * The first audit found features that were *unreachable*. This one found
 * features that were reachable, gated green, documented as enforcing the
 * parental kill switch — and enforcing **nothing**.
 *
 * Two findings, both structural:
 *
 *  1. **CALL and PTT had no consent check at all.** `grep` for `ConsentScope`
 *     across `app/src/main` returned the store, the gate, the two new screens
 *     and the two new clients — and nothing else. Calling and the walkie talkie
 *     are the two OLDEST features in the app; they predate the consent model
 *     and so shipped "ALREADY SHIPPED without a cert" (the words are in
 *     `ConsentScope`'s own KDoc), and nobody went back. So "Turn everything
 *     off" closed Messages and Pictures and left a child able to call their
 *     grown-up forever. `firestore.rules` cannot compensate: the call and PTT
 *     rooms are written by pair membership alone, so an unenforced client is
 *     the ONLY enforcement there is.
 *
 *  2. **The parent's own grant sequence was structurally unreadable.** The
 *     grant listener queried `granteeUid == ownUid`. The rules forbid a member
 *     writing a grant naming themselves, so on the grown-up's phone that query
 *     returns nothing, ever — `highestSeq` was permanently 0. Therefore
 *     `revoke()` read `through = 0` and refused every single time ("still
 *     checking", forever), and `grant()` computed seq 1 on every tap so the
 *     second "Allow everything" was denied by the rules' monotonicity check.
 *     The kill switch was inoperable on the one phone that owns it.
 *
 * Both are silent. Neither throws, neither logs an error, and both fail in the
 * direction that LOOKS like something else — a network hiccup, a waiting child.
 * A structural assertion is the only thing that reliably catches this class,
 * so that is what these are.
 */
class ConsentEnforcementRegressionTest {

    private val mainDir = "src/main/java/com/calldad"
    private fun read(rel: String) = File("$mainDir/$rel").readText()

    private fun callVm() = read("ui/screens/CallViewModel.kt")
    private fun pttVm() = read("ui/screens/PttViewModel.kt")
    private fun consentStore() = read("consent/ConsentStore.kt")
    private fun consentScreen() = read("ui/screens/ConsentScreen.kt")

    /**
     * Calling MUST consult the CALL grant. It did not: `ConsentScope.CALL`
     * appeared exactly once in the whole main source tree — inside ConsentStore,
     * computing a headline decision nobody read.
     */
    @Test
    fun callingIsGatedByTheCallGrant() {
        val vm = callVm()
        assertTrue(
            "CallViewModel must observe consent. Calling is the feature the " +
                "consent model exists to protect, and it read NOTHING: a revoked " +
                "child could still call their grown-up indefinitely.",
            vm.contains("ConsentStore()") && vm.contains("consent.start(")
        )
        assertTrue(
            "it must read the derived decision reactively, so a parent can revoke " +
                "calling mid-call rather than only before the next one",
            vm.contains("consent.decision.collect")
        )
    }

    /**
     * Both directions. Gating only the outgoing path leaves a parent unable to
     * stop a call already ringing on the child's phone.
     */
    @Test
    fun bothCallDirectionsAreGated() {
        val vm = callVm()
        val start = vm.substringAfter("fun startCall()").substringBefore("fun onReRing()")
        val answer = vm.substringAfter("fun answerCall()").substringBefore("private fun newClient")
        assertTrue(
            "startCall() must refuse without the CALL grant",
            start.contains("callAllowed()")
        )
        assertTrue(
            "answerCall() must refuse without it too — an incoming ring on a " +
                "revoked child's phone is still a call",
            answer.contains("callAllowed()")
        )
    }

    /**
     * The third hole, and the one that made the other two theoretical.
     *
     * Gating `startCall` and `answerCall` is not enough, because a CHILD's phone
     * decides whether to ring in neither — it rings in the room listener. With
     * only the two tap-path checks, a parent who had revoked calling still got
     * their own phone ringing at full volume from the revoked child's phone, the
     * room went RINGING, and the `answerCall` guard never ran because the child
     * never tapped anything.
     *
     * The refusal must also publish ENDED, or the caller's phone rings out for a
     * call this side silently ignored.
     */
    @Test
    fun anIncomingRingIsRefusedBeforeThePhoneRings() {
        val vm = callVm()
        val handle = vm.substringAfter("doc.status == \"RINGING\" && !mineAsCaller")
            .substringBefore("startIncomingTimeout")
        assertTrue(
            "the incoming branch must check the CALL grant. This is where the " +
                "phone decides to start ringing, so this is the check that actually " +
                "stops a revoked child's phone from ringing at all.",
            handle.contains("callAllowed()")
        )
        assertTrue(
            "a refused ring must publish ENDED so the CALLER's phone stops ringing " +
                "too — ignoring the document leaves their phone ringing at nobody",
            handle.contains("finishCall") && handle.contains("\"ENDED\"")
        )
    }

    /**
     * Absence DENIES. A cold start has no decision yet; if that reads as
     * permitted, the app fails open before the grown-up has granted anything.
     */
    @Test
    fun anUnknownConsentDecisionIsDeniedNotPermitted() {
        val vm = callVm()
        assertTrue(
            "the CALL check must be `isGranted == true`, never a null/empty test that " +
                "treats 'not known yet' as allowed",
            vm.contains("isGranted == true")
        )
        assertFalse(
            "a bare `_callConsent.value != null` is not a permission check — the value " +
                "can be a Denied decision",
            Regex("""_callConsent\.value\s*!=\s*null""").containsMatchIn(vm)
        )
    }

    /**
     * Revoking must END a live call, not merely block the next one. A child
     * mid-call to a grown-up who has just hit the kill switch is exactly the
     * case the feature exists for.
     */
    @Test
    fun revokingEndsAnInProgressCall() {
        val vm = callVm()
        val onConsent = vm.substringAfter("private fun onConsentChanged(")
            .substringBefore("private fun callAllowed()")
        assertTrue(
            "a withdrawn CALL grant must tear down a Ringing or Connected call",
            onConsent.contains("CallState.Ringing") && onConsent.contains("CallState.Connected")
        )
        assertTrue(
            "it must go through endCall(), the one path that stops the ringtone, " +
                "dismisses the foreground service AND publishes ENDED so the peer's " +
                "phone stops ringing",
            onConsent.contains("endCall()")
        )
    }

    /**
     * The INBOUND half of the walkie talkie.
     *
     * Gating `onPress` stops a child sending. It does nothing about the other
     * phone: `VoiceClipPttEngine` plays inbound clips on ANY screen, unprompted,
     * so a revoked child's phone kept talking at it from the grown-up's side
     * regardless of what the button did. A kill switch with the speaker path
     * still open is not a kill switch.
     */
    @Test
    fun theWalkieTalkieIsGatedOnBOTHDirections() {
        val vm = pttVm()
        val onConsent = vm.substringAfter("private fun onConsentChanged(")
            .substringBefore("private var pttAllowed")
        assertTrue(
            "revocation must reach the ENGINE, not just the button — inbound clips " +
                "play on any screen and would keep talking to a revoked child",
            onConsent.contains("setInboundAllowed")
        )

        val engine = read("ptt/VoiceClipPttEngine.kt")
        assertTrue(
            "the engine needs an inbound gate setter",
            engine.contains("fun setInboundAllowed(allowed: Boolean)")
        )
        val player = engine.substringAfter("private suspend fun runPlayer()")
            .substringBefore("private suspend fun play(")
        assertTrue(
            "the PLAYER must honour it. The listener is not enough: a clip that " +
                "arrives while the grant is absent must WAIT rather than play, and " +
                "must not be dropped either — the sender was told it was SENT.",
            player.contains("inboundAllowed")
        )
        assertTrue(
            "it must wait, not drop: dropping destroys a delivered message, and " +
                "blocking-then-reallowing would silently lose the words in between",
            Regex("""while\s*\(\s*!inboundAllowed\s*\)\s*delay""").containsMatchIn(player)
        )
    }

    /**
     * The walkie talkie is the second-oldest feature and had no check either.
     */
    @Test
    fun theWalkieTalkieIsGated() {
        val vm = pttVm()
        assertTrue(
            "PttViewModel must hold a ConsentStore — PTT predates the consent model " +
                "and shipped without one, so the kill switch never covered it",
            vm.contains("ConsentStore()") && vm.contains("consent.start(")
        )
        assertTrue(
            "it must react to revocation, not check once at open: a parent hitting the " +
                "kill switch mid-clip must not leave the recording uploading",
            vm.contains("consent.decision.collect")
        )
        val press = vm.substringAfter("fun onPress()").substringBefore("fun onRelease()")
        assertTrue(
            "onPress() must refuse without the PTT grant",
            press.contains("pttAllowed") || press.contains("callAllowed")
        )
        assertTrue(
            "the refusal needs a kid-safe message, not silence. A button that does " +
                "nothing looks broken; a button that explains asks for a grown-up.",
            press.contains("walkie talkie is turned off")
        )
    }

    /**
     * The asymmetry trap, pinned. The grant listener can only ever see docs
     * naming THIS device as grantee, and on the parent's phone that set is
     * empty by construction.
     */
    @Test
    fun theParentReadsTheGrantsItAuthoredNotTheOnesItHolds() {
        val store = consentStore()
        assertTrue(
            "ConsentStore must track the grants it AUTHORED separately. The grant " +
                "listener queries granteeUid == ownUid, and the rules forbid a " +
                "self-named grant, so on the grown-up's phone that query is empty " +
                "forever — which pinned highestSeq at 0.",
            store.contains("authoredGrants")
        )
        assertTrue(
            "observedCerts() must return BOTH roles, or the parent's screen reads a " +
                "sequence that does not exist",
            store.contains("fun observedCerts(): List<ConsentCert> = grants + authoredGrants")
        )
        assertTrue(
            "there must be a single accessor for the max sequence across both roles",
            store.contains("fun highestObservedSeq(): Int")
        )
    }

    /**
     * The view model must read the sequence on a cert CHANGE, not on a scope
     * change. The grantor's derived scope set is `ConsentScope.entries` from the
     * instant it becomes grantor and never varies again — so a renewal, the one
     * event that raises the sequence, changes nothing observable and a
     * `scopes.collect` alone would freeze the sequence at its first value.
     */
    @Test
    fun theParentScreenRefreshesItsSequenceOnCertChangeNotScopeChange() {
        val store = consentStore()
        val screen = consentScreen()
        assertTrue(
            "the store needs a tick that fires when a cert changes without changing " +
                "the derived scopes — a StateFlow conflates equal values, and the " +
                "grantor's scope set never changes again after it fills",
            store.contains("certTick")
        )
        assertTrue(
            "ConsentScreen must merge scopes with certTick",
            screen.contains("store.certTick")
        )
        assertTrue(
            "and read the sequence from both roles",
            screen.contains("store.highestObservedSeq()")
        )
        assertFalse(
            "deriving the sequence from observedCerts() alone in the collector was the " +
                "original bug",
            Regex("""observedCerts\(\)\.maxOfOrNull""").containsMatchIn(screen)
        )
    }

    /**
     * The RECEIVE half of every feature.
     *
     * Each had a *display* gate and a *send* gate, and both were taken for the
     * whole gate. None had a gate on the Firestore LISTENER, so a revoked child's
     * phone kept reading the room while showing nothing:
     *
     *  - photos: up to 24 x 800KB of chunks still downloaded — invisible to the
     *    user, real on the data plan, and exactly the traffic a parent expects to
     *    stop when they switch sharing off;
     *  - chat: up to 200 messages still pulled;
     *  - PTT: every clip in the room still played out loud, unprompted, on
     *    whatever screen the phone happened to be on.
     *
     * The PTT case is the sharp one, because that one is AUDIBLE. "Turn
     * everything off" has to mean the device stops reading and stops playing —
     * a switch that only changes what is drawn is not what a parent pressing it
     * is being told.
     */
    @Test
    fun everyFeatureGatesItsListenerNotJustItsScreen() {
        assertTrue(
            "ChatViewModel must push the grant down to the client, not only hold it " +
                "for the screen's own check",
            read("ui/screens/ChatViewModel.kt").contains("client.setInboundAllowed(")
        )
        assertTrue(
            "PhotoViewModel likewise — the chunk download was the ungated part",
            read("ui/screens/PhotoViewModel.kt").contains("client.setInboundAllowed(")
        )
        assertTrue(
            "PttViewModel likewise — inbound clips play on any screen",
            pttVm().contains("setInboundAllowed")
        )

        val chatClient = read("chat/ChatClient.kt")
        assertTrue(
            "ChatClient needs the gate and must default it to FALSE (absence denies)",
            chatClient.contains("fun setInboundAllowed(allowed: Boolean)") &&
                chatClient.contains("private var inboundAllowed = false")
        )
        assertTrue(
            "and the LISTENER must honour it, not just the setter — a listener " +
                "attached before revocation kept delivering into a hidden thread",
            chatClient.substringAfter("private fun listen()")
                .substringBefore("private fun stampDelivered")
                .contains("inboundAllowed")
        )

        val photoClient = read("photos/PhotoClient.kt")
        assertTrue(
            "PhotoClient likewise, defaulting closed",
            photoClient.contains("fun setInboundAllowed(allowed: Boolean)") &&
                photoClient.contains("private var inboundAllowed = false")
        )
        assertTrue(
            "and onPair must honour it: the listener is attached THERE, so a gate " +
                "living anywhere else would never be consulted",
            photoClient.substringAfter("private fun onPair(").contains("inboundAllowed")
        )

        val pttEngine = read("ptt/VoiceClipPttEngine.kt")
        assertTrue(
            "the PTT LISTENER must honour it too, for the same reason",
            pttEngine.substringAfter("private fun listenForClips(").contains("inboundAllowed")
        )
        assertTrue(
            "and the PLAYER must wait rather than drop — dropping destroys a " +
                "delivered message the sender was told was SENT",
            pttEngine.substringAfter("private suspend fun runPlayer()").contains("inboundAllowed")
        )
    }

    /**
     * A UI-layer scope check alone is not a gate.
     *
     * `ChatScreen` read `scopes` to decide what to draw and `ChatViewModel` read
     * it before sending, but both were reading a snapshot taken at some earlier
     * moment. A press landing in the same frame the grant is revoked would slip
     * through. For chat that is unusually visible, because [ChatClient.send]
     * inserts an OPTIMISTIC row before the write resolves: the child watches
     * their message appear and then vanish with no explanation. For photos it is
     * worse in the other direction — entirely silent, because there is no
     * optimistic row — so an 800KB upload would complete after the switch was
     * flipped.
     */
    @Test
    fun theClientRechecksTheLiveGateRatherThanTrustingTheCallersSnapshot() {
        assertTrue(
            "ChatClient.send must re-check its own gate. The `scopes` argument is a " +
                "stale snapshot, and the optimistic row makes that slip VISIBLE.",
            read("chat/ChatClient.kt").substringAfter("suspend fun send(")
                .substringBefore("return runCatching")
                .contains("inboundAllowed")
        )
        assertTrue(
            "PhotoClient.send must re-check too — and here the failure would be silent, " +
                "so a completed 800KB upload after revocation is the whole risk",
            read("photos/PhotoClient.kt").substringAfter("suspend fun send(")
                .substringBefore("return runCatching")
                .contains("inboundAllowed")
        )
    }

    /**
     * Revoking must clear what is ALREADY held, not merely stop the next fetch.
     * Otherwise the revoked phone is still sitting on the content it was told to
     * stop receiving.
     */
    @Test
    fun revocationClearsAlreadyHeldContent() {
        assertTrue(
            "revoking TEXT must drop the messages already in memory",
            read("chat/ChatClient.kt")
                .substringAfter("fun setInboundAllowed(allowed: Boolean)")
                .substringBefore("private fun listen()")
                .contains("_messages.value = emptyList()")
        )
        assertTrue(
            "revoking PHOTO must drop the pictures already in memory — 24 decoded " +
                "images is the tail of the same problem",
            read("photos/PhotoClient.kt")
                .substringAfter("fun setInboundAllowed(allowed: Boolean)")
                .substringBefore("private fun onPair(")
                .contains("_photos.value = emptyList()")
        )
    }

/**
     * A press is a GESTURE, not an atomic action, and that is what makes the PTT
     * gate have to exist in the engine rather than only in the ViewModel.
     *
     * The finger goes down while the grant is live, and comes up after a parent
     * has flipped it — with a finished, fully-formed recording on disk. Gating
     * only `onPress` uploaded exactly that clip: the one moment a parent most
     * expects the traffic to stop.
     */
    @Test
    fun thePttEngineGatesTheSendItselfNotOnlyTheButton() {
        val engine = read("ptt/VoiceClipPttEngine.kt")
        assertTrue(
            "startTransmitting must check consent BEFORE the microphone. Checking " +
                "after leaves a recorder running that nothing will stop cleanly — an " +
                "open mic on a phone the child believes is switched off.",
            engine.substringAfter("override suspend fun startTransmitting(")
                .substringBefore("stopPlayer()")
                .contains("inboundAllowed")
        )
        assertTrue(
            "and the shape must be right: the consent check has to come BEFORE the " +
                "RECORD_AUDIO check, not merely exist somewhere in the function",
            engine.substringAfter("override suspend fun startTransmitting(")
                .indexOf("inboundAllowed") <
                engine.substringAfter("override suspend fun startTransmitting(")
                    .indexOf("RECORD_AUDIO")
        )
        assertTrue(
            "stopTransmitting must re-check too — it is a different coroutine from " +
                "the press, and the recording is complete by then",
            engine.substringAfter("override suspend fun stopTransmitting(")
                .substringBefore("return runCatching")
                .contains("inboundAllowed")
        )
        assertTrue(
            "a refusal must be NOT_ALLOWED, not PERMISSION_DENIED. The microphone " +
                "permission is something a child can grant; the PTT grant is a " +
                "grown-up's decision, and conflating them tells the child to ask " +
                "for something they cannot give.",
            read("ptt/PttEngine.kt").contains("NOT_ALLOWED")
        )
    }

    /**
     * Belt and braces on the store: the invariant must not depend on every
     * caller reading both roles correctly.
     */
    @Test
    fun theStoreRefusesToIssueAnInertRevocation() {
        val store = consentStore()
        assertTrue(
            "revoke() must still require throughGrantSeq >= 1",
            Regex("""throughGrantSeq\s*>=\s*1""").containsMatchIn(store)
        )
        assertTrue(
            "grant() must still require grantSeq >= 1",
            Regex("""grantSeq\s*>=\s*1""").containsMatchIn(store)
        )
    }
}