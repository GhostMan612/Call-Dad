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
 * The body of the function whose declaration starts with [signature], with
     * COMMENTS STRIPPED.
     *
     * Brace-matched, so it cannot drift when an unrelated member is added or
     * reordered above it. Comments removed, so a KDoc that names the forbidden
     * construct in order to explain its absence is not read as its presence.
     *
     * Every "does this function still do the forbidden thing" assertion in this
     * file used `substringAfter(sig).substringBefore(otherMarker)`, and that is a
     * trap twice over:
     *
     *  1. **`substringBefore` returns the ENTIRE remaining source when its
     *     delimiter is absent.** Moving the second marker silently widened the
     *     slice to the whole file.
     *  2. The forbidden token was then found *inside a comment* explaining why it
     *     must not appear — so `markVisibleAsRead()` and `BitmapFactory
     *     .decodeByteArray` were both reported as violations of code that
     *     correctly contained neither.
     *
     * Together those made two real regression checks into tests that could only
     * fail, and both had been failing silently behind a green gate. A slice is
     * only meaningful if you know where it ends (count braces) and you are
     * reading CODE (strip comments).
     */
    private fun functionBody(source: String, signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) {
            "no function starting with '$signature' — this test is now vacuous and " +
                "must be updated, not deleted"
        }
        // An EXPRESSION body (`fun f(): T = expr`) has no brace of its own, and
        // the first `{` after it belongs to something inside the expression — a
        // `filterNot { ... }` lambda, say. Brace-matching from there returns the
        // lambda and nothing else, which silently makes the slice useless.
        //
        // `prepend` is exactly that shape, so this is not hypothetical. Detect a
        // top-level `=` between the signature and the first `{` (paren-depth 0, so
        // a default-argument `=` does not count) and slice to the next member
        // declaration instead.
        val eq = topLevelAssignmentAfter(source, start)
        if (eq >= 0) {
            val end = Regex("""(?m)^\s{4}(?:private|internal|public|override|fun|val|var|@)""")
                .find(source, eq)?.range?.first ?: source.length
            return stripComments(source.substring(eq, end))
        }

        val open = source.indexOf('{', start)
        require(open >= 0) { "'$signature' has no body" }
        var depth = 0
        var i = open
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return stripComments(source.substring(open + 1, i))
                    }
                }
            }
            i++
        }
        throw AssertionError("unbalanced braces after '$signature'")
    }

    /** Index of a `=` at paren depth 0 in [source] after [from], or -1. */
    private fun topLevelAssignmentAfter(source: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++
                ')' -> depth--
                '=' -> if (depth == 0) return i
                '{', '\n' -> if (source[i] == '{') return -1 // block body, not an expression
            }
            i++
        }
        return -1
    }

    /** Comments out of a source slice. `//` first, so `//` inside `/* */` is safe. */
    private fun stripComments(src: String): String =
        src.replace(Regex("""(?m)//.*$"""), " ")
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")

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
        val body = functionBody(photoClient(), "private suspend fun loadOne(")
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
        val prepend = functionBody(client, "private fun prepend(")
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
        val onPair = functionBody(
            chatVm(), "private suspend fun onPair("
        )
        assertFalse(
            "onPair must NOT mark read: it runs on pairing change, which happens on " +
                "every screen including an active call, so a parent would see \"Seen\" " +
                "for a message the child never opened",
            onPair.contains("markVisibleAsRead()")
        )
    }
}
