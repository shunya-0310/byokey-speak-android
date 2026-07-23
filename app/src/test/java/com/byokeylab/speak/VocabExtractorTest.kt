package com.byokeylab.speak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabExtractorTest {

    @Test
    fun extractsVocabLinesAndStripsThemFromText() {
        val reply = """
            Natural reply: Sounds like a plan!
            Coach notes: Good sentence.
            Vocab: cut back on | 〜を控える
            Vocab: swamped | 忙しくて手一杯
        """.trimIndent()

        val result = VocabExtractor.extract(reply)
        assertEquals(2, result.vocab.size)
        assertEquals("cut back on", result.vocab[0].expression)
        assertEquals("〜を控える", result.vocab[0].meaning)
        assertEquals("swamped", result.vocab[1].expression)
        assertFalse(result.cleanText.contains("Vocab:"))
        assertTrue(result.cleanText.contains("Sounds like a plan!"))
    }

    @Test
    fun handlesFullWidthSeparator() {
        val result = VocabExtractor.extract("Vocab: hit the road ｜ 出発する")
        assertEquals(1, result.vocab.size)
        assertEquals("hit the road", result.vocab[0].expression)
        assertEquals("出発する", result.vocab[0].meaning)
    }

    @Test
    fun ignoresVocabLineWithoutExpression()  {
        val result = VocabExtractor.extract("Hello!\nVocab: | 意味だけ")
        assertTrue(result.vocab.isEmpty())
        assertEquals("Hello!", result.cleanText)
    }

    @Test
    fun missingMeaningIsAllowed() {
        val result = VocabExtractor.extract("Vocab: no worries")
        assertEquals(1, result.vocab.size)
        assertEquals("no worries", result.vocab[0].expression)
        assertEquals("", result.vocab[0].meaning)
    }

    @Test
    fun capsAtThreeCards() {
        val reply = (1..5).joinToString("\n") { "Vocab: word$it | 意味$it" }
        assertEquals(3, VocabExtractor.extract(reply).vocab.size)
    }

    @Test
    fun textWithoutVocabIsUnchanged() {
        val result = VocabExtractor.extract("Just a normal reply.")
        assertTrue(result.vocab.isEmpty())
        assertEquals("Just a normal reply.", result.cleanText)
    }
}
