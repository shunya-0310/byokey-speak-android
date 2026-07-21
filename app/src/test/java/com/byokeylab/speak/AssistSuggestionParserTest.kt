package com.byokeylab.speak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistSuggestionParserTest {

    @Test
    fun parsesThreeOptionsWithNotes() {
        val result = """
            Option 1: Prices keep going up these days.
            最近ずっと値上がりしている、というシンプルな言い方。
            Option 2: Everything's getting so expensive lately.
            日常会話でよく使うカジュアルな表現。
            Option 3: Inflation's really hitting my wallet.
            くだけた言い回し。
        """.trimIndent()

        val suggestions = AssistSuggestionParser.parse(result)
        assertEquals(3, suggestions.size)
        assertEquals("Prices keep going up these days.", suggestions[0].english)
        assertTrue(suggestions[0].note.contains("シンプル"))
    }

    @Test
    fun parsesSingleOption() {
        val suggestions = AssistSuggestionParser.parse("Option 1: I'm cutting back on eating out.")
        assertEquals(1, suggestions.size)
        assertEquals("I'm cutting back on eating out.", suggestions[0].english)
        assertEquals("", suggestions[0].note)
    }

    @Test
    fun returnsEmptyForUnlabeledText() {
        assertTrue(AssistSuggestionParser.parse("Sorry, I can't help.").isEmpty())
    }

    @Test
    fun returnsEmptyForNullOrBlank() {
        assertTrue(AssistSuggestionParser.parse(null).isEmpty())
        assertTrue(AssistSuggestionParser.parse("  ").isEmpty())
    }

    @Test
    fun capsAtThreeSuggestions() {
        val result = """
            Option 1: A
            Option 2: B
            Option 3: C
        """.trimIndent()
        assertEquals(3, AssistSuggestionParser.parse(result).size)
    }
}
