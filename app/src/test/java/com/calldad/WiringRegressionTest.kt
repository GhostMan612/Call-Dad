// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// WiringRegressionTest.kt — features that were complete, gated green, and UNUSABLE
// Location: app/src/test/java/com/calldad/WiringRegressionTest.kt
package com.calldad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The 2026-10-01 audit's most embarrassing finding, and the general shape of it.
 *
 * `ChatViewModel` declared `scopes: Set<ConsentScope>` in its UI state and read it
 * in two places -- the `TEXT`-grant check and the send validator. **Nothing ever
 * wrote it.** So it was permanently `emptySet()`, `ChatScreen`'s
 * `ConsentScope.TEXT !in state.scopes` was permanently true, and the only thing
 * the Messages tile ever rendered was "Messaging is turned off right now. Ask a
 * grown-up."
 *
 * A complete, reviewed, documented, gated-green text-chat feature that a child
 * could not open. The tests passed because the tests checked the domain, the
 * rules, and the screen's *source text* -- and every one of those was correct.
 * Nothing checked that the two halves were connected.
 *
 * These assertions are deliberately structural and blunt: a state field that the
 * gate checks must have a writer somewhere in the same file.
 */
class WiringRegressionTest {

    private val mainDir = "src/main/java/com/calldad"
    private fun read(rel: String) = File("$mainDir/$rel").readText()

    private fun chatVm() = read("ui/screens/ChatViewModel.kt")
    private fun photoVm() = read("ui/screens/PhotoViewModel.kt")
    private fun consentStore() = read("consent/ConsentStore.kt")
    private fun consentScreen() = read("ui/screens/ConsentScreen.kt")
    private fun chatClient() = read("chat/ChatClient.kt")
    private fun photoClient() = read("photos/PhotoClient.kt")

    /**
     * Every `ConsentScope` the chat screen and send path depend on must be
     * derived from a LIVE ConsentStore, not read from a field nobody sets.
     */
    @Test
    fun theChatViewModelIsWiredToConsent() {
        val vm = chatVm()
        assertTrue(
            "ChatViewModel must hold a ConsentStore, or `scopes` is a field nobody " +
                "writes and the whole thread renders as 'turned off right now'",
            vm.contains("ConsentStore()")
        )
        assertTrue(
            "it must start it, or it never observes anything",
            vm.contains("consent.start(")
        )
        assertTrue(
            "it must collect the derived scopes into the UI state",
            vm.contains("consent.scopes.collect")
        )
    }

    @Test
    fun theSameWiringExistsForPhotos() {
        val vm = photoVm()
        assertTrue("PhotoViewModel must hold a ConsentStore", vm.contains("ConsentStore()"))
        assertTrue("it must collect the derived scopes", vm.contains("consent.scopes.collect"))
    }

    /**
     * The derived-scope set must actually be derived, and the derivation must
     * account for BOTH roles.
     */
    @Test
    fun theGrantorSideIsNotGatedOutOfItsOwnApp() {
        val store = consentStore()
        assertTrue(
            "ConsentStore must recognise the GRANTOR. The rules forbid a member " +
                "writing a grant naming themselves, so a grown-up can never hold a " +
                "cert -- a gate that required one would lock the parent out of the " +
                "very app they are configuring.",
            store.contains("isGrantor")
        )
        assertTrue(
            "the grantor determination must come from an actual query, not a guess",
            store.contains("""whereEqualTo("grantorUid"""")
        )
    }

    /**
     * The kill switch must never write a revocation that cancels nothing and
     * report success.
     */
    @Test
    fun theKillSwitchRefusesWhenNoGrantHasBeenSeen() {
        val screen = consentScreen()
        assertTrue(
            "revoke() must refuse when highestSeq is 0. A revocation of seq 0 is " +
                "permanent, succeeds, and cancels nothing -- so a parent on a cold " +
                "start or bad signal is told the child is cut off while the child " +
                "keeps every permission.",
            Regex("""through\s*<\s*1""").containsMatchIn(screen) ||
                Regex("""highestSeq\s*<\s*1""").containsMatchIn(screen)
        )
        assertTrue(
            "revoke() must not report success on that path",
            screen.contains("still checking")
        )
    }

    @Test
    fun theStoreAlsoRefusesAZeroSeqRevocation() {
        val store = consentStore()
        assertTrue(
            "ConsentStore.revoke must require >= 1, not >= 0, so the invariant does " +
                "not depend on every caller remembering it",
            Regex("""throughGrantSeq\s*>=\s*1""").containsMatchIn(store)
        )
    }

    /**
     * The chat listener window must be the NEWEST messages, matching what the
     * prune keeps. They disagreed: the prune kept the newest 200 and the listener
     * returned the oldest 200, so past 200 messages a child could send number 205
     * and see 1-200 on screen.
     */
    @Test
    fun theChatListenerWindowIsTheNewestMessages() {
        val body = chatClient()
            .substringAfter("private fun listen()")
            .substringBefore("private fun stampDelivered")
        assertTrue(
            "the listener must query DESCENDING so its window is the newest messages",
            Regex("""orderBy\("createdAt",\s*Query\.Direction\.DESCENDING\)""")
                .containsMatchIn(body)
        )
        assertFalse(
            "an ASCENDING listener window is the OLDEST 200 and hides every new message",
            Regex("""orderBy\("createdAt",\s*Query\.Direction\.ASCENDING\)""")
                .containsMatchIn(body)
        )
    }

    /**
     * No decode-as-a-validity-probe. It leaked a full ARGB bitmap per received
     * photo: the result was null-checked, published as bytes, and thrown away.
     */
    @Test
    fun photoReceiveDoesNotDecodeAndDiscardABitmap() {
        val body = photoClient()
            .substringAfter("private suspend fun loadOne(")
            .substringBefore("private fun prepend(")
        assertFalse(
            "loadOne must not BitmapFactory.decodeByteArray as a probe. The bitmap is " +
                "null-checked, the BYTES are published, and the bitmap is abandoned " +
                "-- ~4.7MB of native pixel data per photo, ~100MB across a 24-photo " +
                "window, i.e. an OutOfMemoryError on a cheap phone. The UI decodes " +
                "once, in a remember, inside a runCatching.",
            body.contains("BitmapFactory.decodeByteArray")
        )
    }

    @Test
    fun thePhotoWorkScopeCanBeCancelled() {
        val client = photoClient()
        assertTrue(
            "PhotoClient needs a close() that cancels workScope. It is a root " +
                "SupervisorJob and nothing else can complete it, so every instance " +
                "leaks a live job and any in-flight 800KB download keeps running " +
                "after the ViewModel is gone.",
            client.contains("fun close()") && client.contains("workScope.cancel()")
        )
        assertTrue(
            "PhotoViewModel.onCleared must call close(), not stop()",
            photoVm().contains("client.close()")
        )
    }

    /**
     * The picture thread must be ordered by TIME, not by Firestore auto-id. An
     * auto-id is a random 20-character string, so the thread was in arbitrary
     * order and `take(24)` kept an arbitrary 24 rather than the newest 24.
     */
    @Test
    fun thePhotoThreadIsOrderedByTimeNotByRandomId() {
        val client = photoClient()
        assertTrue(
            "PhotoMessage must carry createdAtMs; the doc id cannot order anything",
            client.contains("val createdAtMs: Long")
        )
        val prepend = client
            .substringAfter("private fun prepend(")
            .substringBefore("private companion object")
        assertFalse(
            "prepend must not sort by id: a Firestore auto-id is random, so a " +
                "week-old photo sorts above an hour-old one",
            prepend.contains("sortedByDescending { it.id }")
        )
        assertTrue("prepend must sort by createdAtMs", prepend.contains("createdAtMs"))
    }

    /**
     * Recycle where the work ends. A fixed `delay(5_000)` on a separate coroutine
     * is not synchronisation: the recycle can land while `createScaledBitmap` is
     * still reading, and it leaked the bitmap entirely if the ViewModel was cleared
     * inside the window. The KDoc also claimed a `finally` that did not exist.
     */
    @Test
    fun theSendBitmapIsRecycledInAFinally() {
        val vm = photoVm()
        val send = vm.substringAfter("fun send(bitmap: Bitmap)").substringBefore("fun clearProblem()")
        assertTrue(
            "the source bitmap must be recycled in a finally, after client.send " +
                "resolves",
            send.contains("finally") && send.contains("bitmap.recycle()")
        )
        assertFalse(
            "a fixed-delay recycle is not a synchronisation primitive; it can destroy " +
                "a bitmap createScaledBitmap is still reading, and it leaks the bitmap " +
                "if the ViewModel is cleared inside the window",
            send.contains("delay(") && send.contains("RECYCLE_GRACE_MS")
        )
    }

    /**
     * Messages must be marked READ only while the thread is on screen. The
     * pair-change collector fired on ANY screen, so the other phone showed "Seen"
     * for words the child never opened -- the receipt trust the whole design
     * exists to protect.
     */
    @Test
    fun pairingChangesDoNotMarkMessagesRead() {
        val onPair = chatVm()
            .substringAfter("private suspend fun onPair(")
            .substringBefore("fun markVisibleAsRead()")
        assertFalse(
            "onPair must NOT mark read: it runs on pairing change, which happens on " +
                "every screen including an active call, so a parent would see \"Seen\" " +
                "for a message the child never opened",
            onPair.contains("markVisibleAsRead()")
        )
    }
}
