// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// ChatTextTest.kt — the allowlist half of SPEC_SHEET §4, in a chat box
// Location: app/src/test/java/com/calldad/ChatTextTest.kt
package com.calldad

import com.calldad.chat.ChatText
import com.calldad.consent.ConsentScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTextTest {

    private val all = setOf(ConsentScope.CALL, ConsentScope.TEXT, ConsentScope.PHOTO)
    private val callOnly = setOf(ConsentScope.CALL)

    private fun ok(raw: String, granted: Set<ConsentScope> = all) =
        ChatText.validate(raw, granted)

    private fun reason(raw: String, granted: Set<ConsentScope> = all) =
        (ok(raw, granted) as ChatText.Verdict.Rejected).reason

    // ---------- consent ----------

    @Test
    fun textNeedsATextGrant() {
        // A CALL-only grant is exactly the case a global flag gets wrong: the
        // child can ring dad, so the app is alive, but text is not permitted.
        assertEquals(ChatText.Reason.NOT_ALLOWED, reason("hi", callOnly))
    }

    @Test
    fun noGrantsAtAllDenies() {
        assertEquals(ChatText.Reason.NOT_ALLOWED, reason("hi", emptySet()))
    }

    @Test
    fun consentIsCheckedBeforeTheBodySoItCannotBeWorkedAround() {
        // An empty body with a text grant is EMPTY, not NOT_ALLOWED: the kid
        // gets the actionable message when they are allowed to send.
        assertEquals(ChatText.Reason.EMPTY, reason("   ", all))
    }

    // ---------- emptiness and length ----------

    @Test
    fun blankIsRejected() {
        assertEquals(ChatText.Reason.EMPTY, reason(""))
        assertEquals(ChatText.Reason.EMPTY, reason("     "))
    }

    @Test
    fun surroundingWhitespaceIsTrimmedNotRejected() {
        assertEquals(ChatText.Verdict.Ok("hello dad"), ok("  hello dad  "))
    }

    @Test
    fun lengthIsBoundedAndTheLimitItselfIsAllowed() {
        val atLimit = "a".repeat(ChatText.MAX_LENGTH)
        assertTrue(ok(atLimit) is ChatText.Verdict.Ok)
        assertEquals(ChatText.Reason.TOO_LONG, reason("a".repeat(ChatText.MAX_LENGTH + 1)))
    }

    // ---------- the no-escape rule ----------

    @Test
    fun urlsAreRejected() {
        listOf(
            "http://example.com",
            "https://example.com/x",
            "HTTPS://EXAMPLE.COM",
            "visit example.com",
            "example.co.uk now",
            "mailto:someone@somewhere.com",
            "tel:+15551234",
            "sms:+15551234",
            "market://details?id=x",
            "data:text/html,hi",
            "file:///etc/passwd",
            "ws://socket.example.io",
            "customscheme://whatever",
            "urn:ietf:params"
        ).forEach {
            assertEquals("must refuse: $it", ChatText.Reason.LINKISH, reason(it))
        }
    }

    @Test
    fun ordinaryChildLanguageSurvives() {
        // The false-positive cost is one word, so these must NOT be refused or
        // the chat becomes useless and the child stops using it.
        listOf(
            "hi dad",
            "I love cake :)",
            "Im 6 and I can do a roly poly",
            "can we play the racing game",
            "bye bye",
            "3.30 is tea time",
            "NO",
            "no!!!",
            "x",
            "call me when you get home ok?",
            "éèü accents fine",
            "my dog is called Spot",
            "my data folder is tidy",
            "lunch is at 12:30"
        ).forEach {
            assertTrue("must allow: $it", ok(it) is ChatText.Verdict.Ok)
        }
    }

    @Test
    fun aWordThatMerelyContainsASchemeIsNotAUrl() {
        // Word boundaries plus the colon requirement: "darling" must not trip
        // the tel/data patterns, and a bare "data" is just a word.
        assertTrue(ok("dear darling") is ChatText.Verdict.Ok)
        assertTrue(ok("my data folder is tidy") is ChatText.Verdict.Ok)
        // A colon after an ordinary word is not a scheme either.
        assertTrue(ok("time: after lunch") is ChatText.Verdict.Ok)
    }

    @Test
    fun controlCharactersAndNewlinesAreRejected() {
        assertEquals(ChatText.Reason.WEIRD_LETTERS, reason("hi\nthere"))
        assertEquals(ChatText.Reason.WEIRD_LETTERS, reason("hi\r\nthere"))
        // Built with Char() rather than written literally: a real ESC or DEL
        // byte in a source file makes it binary, which is a nastier failure
        // than the one under test.
        val esc = Char(0x1B)
        val del = Char(0x7F)
        val nul = Char(0x00)
        // A pasted ANSI escape must not survive into a Compose Text as raw
        // control bytes.
        assertEquals(ChatText.Reason.WEIRD_LETTERS, reason("hi${esc}[31mred"))
        assertEquals(ChatText.Reason.WEIRD_LETTERS, reason("hi${del}bye"))
        assertEquals(ChatText.Reason.WEIRD_LETTERS, reason("hi${nul}bye"))
        // A vertical tab is control too (0x0B), and is inside the class.
        assertEquals(ChatText.Reason.WEIRD_LETTERS, reason("hi${Char(0x0B)}bye"))
    }

    @Test
    fun tabsAreAllowedSoFormulasSurvive() {
        // 0x09 is excluded from the control class on purpose: it is whitespace a
        // child can produce on a keyboard, not an escape.
        assertTrue(ok("10\t30 tomorrow") is ChatText.Verdict.Ok)
    }

    @Test
    fun emojiAndAccentsPassThrough() {
        assertTrue(ok("I love you ❤") is ChatText.Verdict.Ok)
    }
}
