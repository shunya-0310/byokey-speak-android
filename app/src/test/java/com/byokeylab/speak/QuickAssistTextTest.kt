package com.byokeylab.speak

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickAssistTextTest {
    @Test
    fun appendsSuggestionWithoutReplacingDraft() {
        assertEquals(
            "I went to see autumn leaves",
            appendAssistText("I went to see", "autumn leaves")
        )
    }

    @Test
    fun keepsExistingTrailingSpace() {
        assertEquals("I saw fall foliage", appendAssistText("I saw ", "fall foliage"))
    }

    @Test
    fun ignoresBlankSuggestion() {
        assertEquals("I went", appendAssistText("I went", "   "))
    }
}
