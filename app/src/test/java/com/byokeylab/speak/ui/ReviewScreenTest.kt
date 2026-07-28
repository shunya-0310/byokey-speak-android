package com.byokeylab.speak.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewScreenTest {
    @Test
    fun countsWholeExpressionsIgnoringCase() {
        val messages = listOf(
            "I use Quick Assist when I need help.",
            "quick assist is useful. Quick Assistant is a different phrase."
        )

        assertEquals(2, countExpressionOccurrences("Quick Assist", messages))
    }

    @Test
    fun doesNotCountAWordInsideAnotherWord() {
        val messages = listOf("I can speak, but this speaker is louder.")

        assertEquals(1, countExpressionOccurrences("speak", messages))
    }
}
