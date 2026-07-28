package com.byokeylab.speak

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPromptTest {
    @Test
    fun c1PromptDoesNotKeepQuickChatShortSentenceRule() {
        val prompt = PromptBuilder.buildConversationPrompt(
            messages = emptyList(),
            latestUserMessage = "What is happening in AI regulation this week?",
            level = EnglishLevel.C1,
            coachInstructions = AppStorage.DEFAULT_COACH_INSTRUCTIONS
        )

        assertTrue(prompt.contains("C1"))
        assertTrue(prompt.contains("Do not force brevity"))
        assertFalse(prompt.contains("2 to 4 short sentences"))
    }

    @Test
    fun analysisPromptRequiresExplicitJsonAndRecurringEvidence() {
        val prompt = PromptBuilder.buildConversationAnalysisPrompt(
            contexts = emptyList(),
            level = EnglishLevel.B2,
            coachInstructions = AppStorage.DEFAULT_COACH_INSTRUCTIONS
        )

        assertTrue(prompt.contains("Return valid JSON only"))
        assertTrue(prompt.contains("at least twice"))
        assertTrue(prompt.contains("\"recurringPatterns\""))
        assertTrue(prompt.contains("\"estimatedCefr\""))
        assertTrue(prompt.contains("Do not flatter"))
        assertTrue(prompt.contains("\"levelUpPlan\""))
    }

    @Test
    fun conversationPromptIncludesSafetyAndCredentialRules() {
        val prompt = PromptBuilder.buildConversationPrompt(
            messages = emptyList(),
            latestUserMessage = "Hello",
            level = EnglishLevel.A2
        )

        assertTrue(prompt.contains("Safety rules:"))
        assertTrue(prompt.contains("self-harm"))
        assertTrue(prompt.contains("API keys"))
    }

    @Test
    fun betterOptionsAreRestrictedToLatestLearnerMessage() {
        val prompt = PromptBuilder.buildConversationPrompt(
            messages = emptyList(),
            latestUserMessage = "I didn't hear that news.",
            level = EnglishLevel.A2
        )

        assertTrue(prompt.contains("the learner's exact latest message only"))
        assertTrue(prompt.contains("not your Natural reply"))
        assertTrue(prompt.contains("Better options: None"))
    }
}
