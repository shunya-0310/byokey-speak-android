package com.byokeylab.speak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningNoteExtractorTest {

    @Test
    fun extractsAllSectionsFromCoachResponse() {
        val response = """
            Natural reply: Nice, that sounds fun!
            Coach notes: "I went to shopping" is unnatural. Drop "to".
            Japanese explanation: go shoppingが自然です。
            Better options: I went shopping. / I did some shopping.
        """.trimIndent()

        val note = LearningNoteExtractor.fromCoachResponse(response, "I went to shopping")
        assertNotNull(note)
        assertTrue(note!!.coachNotes.contains("unnatural"))
        assertTrue(note.betterOptions.contains("I went shopping"))
        assertTrue(note.japaneseNote.contains("go shopping"))
        assertEquals("I went to shopping", note.sourceMessage)
    }

    @Test
    fun extractsNativeNoteFromQuickChatReply() {
        val response = """
            Sounds like a busy day! Native note: A native speaker would usually say "I was swamped."
        """.trimIndent()

        val note = LearningNoteExtractor.fromCoachResponse(response, "I was very busy")
        assertNotNull(note)
        assertTrue(note!!.coachNotes.contains("swamped"))
    }

    @Test
    fun returnsNullWhenNothingToExtract() {
        assertNull(LearningNoteExtractor.fromCoachResponse("Great job! Keep going.", "Hello"))
    }
}
