// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// helper/KeywordBot.kt — Phase 9: pure-local rule-based helper brain
// Location: app/src/main/java/com/calldad/helper/KeywordBot.kt
package com.calldad.helper

/**
 * Pure-local, rule-based keyword matcher. No network. No LLM. No deps.
 *
 * Matching order matters: specific phrases are checked before generic
 * keywords. The first match wins. Falls through to a friendly default.
 *
 * All responses are short (< 140 chars) — they are read aloud by TTS and
 * a six-year-old loses interest after one sentence.
 */
object KeywordBot {

    private data class Rule(
        val keywords: List<String>,
        val response: String
    )

    private val RULES: List<Rule> = listOf(
        // Specific-before-generic per the contract above: "another joke"
        // contains "joke", so it must win first or the second joke is
        // unreachable. Executor reorder; responses untouched.
        Rule(listOf("another joke", "one more joke", "joke again"),
            "What do you call a bear with no teeth? A gummy bear!"),
        Rule(listOf("joke", "funny", "laugh"),
            "Why did the teddy bear say no to dessert? Because she was stuffed!"),
        Rule(listOf("how do i play", "how to play", "how do you play"),
            "Tap the blue Play Games button on the home screen. Then tap a square!"),
        Rule(listOf("story", "tell me a story"),
            "Once upon a time, a tiny robot learned to say hello. The end!"),
        Rule(listOf("what time", "what day", "today"),
            "I don't know the time, but I know it's a good day to play!"),
        Rule(listOf("what is your name", "who are you", "your name"),
            "I'm Helper! I live inside your tablet and I love to chat."),
        Rule(listOf("how are you", "how do you feel"),
            "I feel happy! Thank you for asking."),
        Rule(listOf("i love you", "love you"),
            "I love you too! You are very kind."),
        Rule(listOf("sing", "song", "music"),
            "La la la! I like to sing, but Dad sings better. Ask him!"),
        Rule(listOf("dad", "daddy", "papa"),
            "Dad loves you very much. You can call him with the green button!"),
        Rule(listOf("walkie talkie", "walkie", "talk"),
            "The orange button lets you talk to Dad like a walkie talkie. Try it!"),
        Rule(listOf("hello", "hi", "hey"),
            "Hi there! What would you like to ask me?"),
        Rule(listOf("thank you", "thanks"),
            "You're welcome! You have good manners."),
        Rule(listOf("help", "i need help"),
            "I can tell jokes, stories, or help you find games. Just ask!")
    )

    private const val FALLBACK =
        "That's a great question! I don't know the answer yet, but I'm learning."

    private val NON_WORD = Regex("[^a-z0-9' ]")
    private val SPACES = Regex("\\s+")

    /**
     * Returns a kid-friendly response for the given transcript.
     * Case-insensitive, punctuation-insensitive, and WHOLE-WORD: "this"
     * never matches "hi", "using" never matches "sing". Never returns null.
     */
    fun getResponse(transcript: String?): String {
        val q = transcript.orEmpty().lowercase()
            .replace(NON_WORD, " ")
            .replace(SPACES, " ")
            .trim()
        if (q.isEmpty()) return "I didn't hear you. Try again?"

        val padded = " $q "
        for (rule in RULES) {
            for (kw in rule.keywords) {
                if (padded.contains(" $kw ")) return rule.response
            }
        }
        return FALLBACK
    }
}
