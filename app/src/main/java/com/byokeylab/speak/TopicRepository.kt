package com.byokeylab.speak

import java.time.LocalDate
import kotlin.math.abs

object TopicRepository {
    private val politics = listOf(
        "Have you been following the debate about election reform?",
        "What do you think makes a political leader trustworthy?",
        "Do you think younger voters are changing modern politics?",
        "How much should governments regulate social media platforms?"
    )

    private val economy = listOf(
        "Have you noticed how interest rates affect everyday prices?",
        "What do you think is the biggest risk for the global economy right now?",
        "Do you think cash will disappear in the future?",
        "How should ordinary people think about inflation?"
    )

    private val generalKnowledge = listOf(
        "Do you know why sleep has such a strong effect on memory?",
        "What is one common belief that you think people rarely question?",
        "How would you explain artificial intelligence to a beginner?",
        "What skill do you think everyone should learn at least once?"
    )

    private val liberalArts = listOf(
        "Have you ever read a book that changed how you see society?",
        "Why do you think history often repeats itself?",
        "What does it mean to be well educated today?",
        "Do you think philosophy is useful in everyday life?"
    )

    fun topicForToday(category: TopicCategory? = null): String {
        val date = LocalDate.now()
        val selectedCategory = category ?: TopicCategory.entries[abs(date.dayOfYear) % TopicCategory.entries.size]
        val candidates = when (selectedCategory) {
            TopicCategory.Politics -> politics
            TopicCategory.Economy -> economy
            TopicCategory.GeneralKnowledge -> generalKnowledge
            TopicCategory.LiberalArts -> liberalArts
        }
        return candidates[abs(date.toEpochDay().toInt()) % candidates.size]
    }

    fun randomTopic(category: TopicCategory): String {
        val candidates = when (category) {
            TopicCategory.Politics -> politics
            TopicCategory.Economy -> economy
            TopicCategory.GeneralKnowledge -> generalKnowledge
            TopicCategory.LiberalArts -> liberalArts
        }
        return candidates.random()
    }
}
