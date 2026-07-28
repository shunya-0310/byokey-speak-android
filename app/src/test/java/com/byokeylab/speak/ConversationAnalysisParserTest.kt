package com.byokeylab.speak

import com.byokeylab.speak.data.ConversationAnalysisParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAnalysisParserTest {
    @Test
    fun parsesStructuredAnalysisJson() {
        val raw = """
            {
              "summary": "質問に短く答えられている。",
              "estimatedCefr": "A2",
              "cefrRationale": "短い日常表現は使えるが、語順の誤りが繰り返されている。",
              "strengths": [
                {"title": "反応が速い", "evidence": ["today is free"], "comment": "短い文で返せています。"}
              ],
              "recurringPatterns": [
                {
                  "category": "naturalness",
                  "title": "語順が日本語寄り",
                  "occurrences": 2,
                  "examples": [
                    {"original": "today is free", "suggestion": "I am free today.", "explanationJa": "主語を人にすると自然です。"}
                  ],
                  "nextAction": "I am ... today の形を練習しましょう。"
                }
              ],
              "improvements": [],
              "nextFocus": ["主語を入れる"],
              "levelUpPlan": ["主語と時制を毎回確認する"],
              "practicePrompts": ["Talk about your day off."]
            }
        """.trimIndent()

        val parsed = ConversationAnalysisParser.parse(raw).getOrThrow()

        assertEquals("質問に短く答えられている。", parsed.summary)
        assertEquals("反応が速い", parsed.strengths.first().title)
        assertEquals("語順が日本語寄り", parsed.recurringPatterns.first().title)
        assertEquals("A2", parsed.estimatedCefr)
        assertEquals("主語と時制を毎回確認する", parsed.levelUpPlan.first())
        assertEquals("Talk about your day off.", parsed.practicePrompts.first())
    }

    @Test
    fun extractsJsonFromFencedLikeText() {
        val parsed = ConversationAnalysisParser.parse("Here is JSON:\n{\"summary\":\"ok\"}").getOrThrow()

        assertEquals("ok", parsed.summary)
        assertTrue(parsed.strengths.isEmpty())
    }
}
