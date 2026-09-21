// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Phase 9 host-side tests: keyword bot contract (pure JVM, zero deps).
package com.calldad

import com.calldad.helper.KeywordBot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordBotTest {

    @Test
    fun joke_matchesFirstRule() {
        assertTrue(KeywordBot.getResponse("tell me a joke").contains("teddy bear"))
    }

    @Test
    fun specificBeatsGeneric() {
        // "another joke" contains "joke" — order must prefer the specific.
        assertTrue(KeywordBot.getResponse("tell me another joke").contains("gummy bear"))
    }

    @Test
    fun matching_isCaseInsensitive() {
        assertEquals(
            KeywordBot.getResponse("tell me a joke"),
            KeywordBot.getResponse("TELL ME A JOKE")
        )
    }

    @Test
    fun empty_neverNull() {
        val r = KeywordBot.getResponse("   ")
        assertTrue(r.isNotBlank())
    }

    @Test
    fun nonsense_fallsThrough() {
        val r = KeywordBot.getResponse("quantum banana")
        assertTrue(r.isNotBlank())
    }

    @Test
    fun responses_stayShort() {
        listOf("tell me a joke", "tell me a story", "how are you", "quantum banana")
            .forEach { assertTrue(KeywordBot.getResponse(it).length < 140) }
    }
}
