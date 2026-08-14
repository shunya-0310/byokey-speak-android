package com.byokeylab.speak

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun parserKeepsFallbackKindSeparateFromOrdinaryNews() {
        val feed = """
            {
              "schemaVersion": 1,
              "status": "live",
              "date": "2026-08-04",
              "generatedAt": "2026-08-03T22:13:14.089Z",
              "items": [
                ${validItem("politics_economy")},
                {
                  "id": "technology-rss",
                  "category": "technology",
                  "headline": "Current technology headline",
                  "summary": "This headline is currently listed by Google News for Technology. Open the source before discussing details beyond the headline.",
                  "question": "What caught your attention about this technology headline?",
                  "coachLeads": {
                    "friendly": "I found a current technology headline we can use for conversation practice.",
                    "energetic": "Here's a fresh technology headline to talk about today.",
                    "calm": "There is a current technology headline available from Google News.",
                    "direct": "Use this current technology headline as today's discussion topic."
                  },
                  "fallback": true,
                  "fallbackKind": "rss_headline",
                  "sources": [{"title": "Source", "url": "https://example.com/technology"}]
                },
                {
                  "id": "sports-source-list",
                  "category": "sports",
                  "headline": "Today's Sports headlines",
                  "summary": "A verified single-story summary could not be prepared safely today. Open the source list to choose a current headline, then use it as a conversation starter.",
                  "question": "Which current sports headline would you like to talk about?",
                  "coachLeads": {
                    "friendly": "Let's pick a current sports headline together and talk about it.",
                    "energetic": "There are fresh sports headlines today.",
                    "calm": "A source list is available for today's sports news.",
                    "direct": "Open today's sports headlines and choose one to discuss."
                  },
                  "fallback": true,
                  "fallbackKind": "source_list",
                  "sources": [{"title": "Source", "url": "https://example.com/sports"}]
                },
                ${validItem("entertainment")}
              ]
            }
        """.trimIndent()

        val parsed = DailyNewsParser.parse(feed)
        val technology = parsed.items.first { it.category == NewsCategory.Technology }
        val sports = parsed.items.first { it.category == NewsCategory.Sports }

        assertEquals(NewsFallbackKind.RssHeadline, technology.fallbackKind)
        assertTrue(technology.canStartConversation)
        assertEquals(NewsFallbackKind.SourceList, sports.fallbackKind)
        assertFalse(sports.canStartConversation)
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
