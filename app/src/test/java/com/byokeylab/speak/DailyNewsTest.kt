package com.byokeylab.speak

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DailyNewsTest {
    private val newsContext = listOf(
        ChatMessage(
            id = 1,
            role = MessageRole.System,
            text = "${NewsConversation.HIDDEN_PREFIX}\nHeadline: Example",
            timestampMillis = 0
        )
    )

    @Test
    fun newsChatDoesNotSearchForEveryOpinion() {
        assertFalse(WebSearchPolicy.shouldSearch(newsContext, "I think that is interesting."))
    }

    @Test
    fun newsChatSearchesForFactualFollowUp() {
        assertTrue(WebSearchPolicy.shouldSearch(newsContext, "What happened after that?"))
    }

    @Test
    fun regularChatSearchesForCurrentTopic() {
        assertTrue(WebSearchPolicy.shouldSearch(emptyList(), "What is the latest score?"))
    }

    @Test
    fun regularChatDoesNotSearchForCasualUseOfToday() {
        assertFalse(WebSearchPolicy.shouldSearch(emptyList(), "Today is free."))
    }

    @Test
    fun regularChatSearchesForExplicitNewsRequest() {
        assertTrue(WebSearchPolicy.shouldSearch(emptyList(), "今日のニュースを教えて"))
    }

    @Test
    fun parserRejectsLeakedJsonFragmentsInNewsText() {
        val brokenFeed = """
            {
              "schemaVersion": 1,
              "status": "live",
              "date": "2026-07-27",
              "generatedAt": "2026-07-26T22:13:14.089Z",
              "items": [
                {
                  "id": "broken-politics",
                  "category": "politics_economy",
                  "headline": "\"coachLeads\": {",
                  "summary": "\", \"question\": \"Do you think this should be visible?\"",
                  "question": "What do you think?",
                  "coachLeads": {
                    "friendly": "Hey, have you heard about this story?",
                    "energetic": "This story is worth talking about.",
                    "calm": "There is a current story worth discussing.",
                    "direct": "Let's talk about this story."
                  },
                  "sources": [{"title": "Source", "url": "https://example.com/politics"}]
                },
                ${validItem("technology")},
                ${validItem("sports")},
                ${validItem("entertainment")}
              ]
            }
        """.trimIndent()

        try {
            DailyNewsParser.parse(brokenFeed)
            fail("Broken news feed should be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("leaked JSON"))
        }
    }

    @Test
    fun hiddenNewsContextIsHiddenEvenIfLegacyRoleWasSaved() {
        val legacyVisibleRole = ChatMessage(
            id = 2,
            role = MessageRole.Coach,
            text = """
                Sources:
                https://example.com
                Treat these sources as the initial factual context. Search the web again when the learner asks for newer facts.
            """.trimIndent(),
            timestampMillis = 0
        )

        assertTrue(NewsConversation.isHidden(legacyVisibleRole))
    }

    private fun validItem(category: String): String = """
        {
          "id": "valid-$category",
          "category": "$category",
          "headline": "Valid headline for $category",
          "summary": "This is a valid summary for the category. It contains ordinary news text.",
          "question": "What do you think about this topic?",
          "coachLeads": {
            "friendly": "Hey, have you heard about this story?",
            "energetic": "This story is worth talking about.",
            "calm": "There is a current story worth discussing.",
            "direct": "Let's talk about this story."
          },
          "sources": [{"title": "Source", "url": "https://example.com/$category"}]
        }
    """.trimIndent()
}
