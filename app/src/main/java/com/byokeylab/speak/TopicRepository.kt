package com.byokeylab.speak

import java.time.LocalDate
import kotlin.math.abs

object TopicRepository {
    private val dailyLife = listOf(
        "What did you have for breakfast today?",
        "Do you have a morning routine you actually stick to?",
        "What small thing made you happy this week?",
        "Are you a morning person or a night owl?",
        "What do you usually do on a lazy Sunday?",
        "Is there a shop or cafe you keep going back to?",
        "What chore do you always put off?",
        "Have you cooked anything new recently?",
        "What show or video are you hooked on right now?",
        "How do you usually unwind after a long day?"
    )

    private val business = listOf(
        "How do you usually start a conversation with a new colleague?",
        "What makes a meeting actually useful?",
        "Have you ever had to give someone difficult feedback?",
        "Do you prefer working from home or at the office?",
        "What would you say to politely disagree with your boss?",
        "How do you handle a day with too many tasks?",
        "What was your first job like?",
        "How would you explain your job to a ten-year-old?",
        "What skill do you want to improve for work?",
        "Have you ever had a misunderstanding with a coworker?"
    )

    private val travel = listOf(
        "What's the best trip you've ever taken?",
        "Do you prefer planning every detail or just winging it?",
        "What would you show a foreign friend visiting your town?",
        "Have you ever gotten lost while traveling?",
        "Window seat or aisle seat, and why?",
        "What food surprised you the most on a trip?",
        "Where would you go if you had a free week and no budget limit?",
        "What do you always pack but never use?",
        "Have you ever had trouble communicating abroad?",
        "Do you like traveling alone or with someone?"
    )

    private val scienceTech = listOf(
        "How has your smartphone changed your daily habits?",
        "Do you use AI tools in your daily life?",
        "What recent invention do you think is underrated?",
        "Would you ride in a fully self-driving car?",
        "How do you feel about sharing personal data with apps?",
        "What technology from science fiction do you want most?",
        "Do you think people will live on Mars someday?",
        "What gadget could you not live without?",
        "How do you decide what news about science to trust?",
        "Is social media making our lives better or worse?"
    )

    private val politics = listOf(
        "Have you been following the debate about election reform?",
        "What do you think makes a political leader trustworthy?",
        "Do you think younger voters are changing modern politics?",
        "How much should governments regulate social media platforms?",
        "Should voting be mandatory?",
        "What local issue do you care about most?"
    )

    private val economy = listOf(
        "Have you noticed how interest rates affect everyday prices?",
        "What do you think is the biggest risk for the global economy right now?",
        "Do you think cash will disappear in the future?",
        "How should ordinary people think about inflation?",
        "Do you prefer saving or investing?",
        "Has anything gotten noticeably more expensive for you lately?"
    )

    private val generalKnowledge = listOf(
        "Do you know why sleep has such a strong effect on memory?",
        "What is one common belief that you think people rarely question?",
        "How would you explain artificial intelligence to a beginner?",
        "What skill do you think everyone should learn at least once?",
        "What's a fun fact you love telling people?",
        "If you could master any language instantly, which one?"
    )

    private val liberalArts = listOf(
        "Have you ever read a book that changed how you see society?",
        "Why do you think history often repeats itself?",
        "What does it mean to be well educated today?",
        "Do you think philosophy is useful in everyday life?",
        "What piece of music or art has stuck with you?",
        "If you could talk to one historical figure, who would it be?"
    )

    private fun topicsOf(category: TopicCategory): List<String> = when (category) {
        TopicCategory.DailyLife -> dailyLife
        TopicCategory.Business -> business
        TopicCategory.Travel -> travel
        TopicCategory.ScienceTech -> scienceTech
        TopicCategory.Politics -> politics
        TopicCategory.Economy -> economy
        TopicCategory.GeneralKnowledge -> generalKnowledge
        TopicCategory.LiberalArts -> liberalArts
    }

    fun topicForToday(category: TopicCategory? = null): String {
        val date = LocalDate.now()
        val selectedCategory = category ?: TopicCategory.entries[abs(date.dayOfYear) % TopicCategory.entries.size]
        val candidates = topicsOf(selectedCategory)
        return candidates[abs(date.toEpochDay().toInt()) % candidates.size]
    }

    fun randomTopic(category: TopicCategory): String {
        return topicsOf(category).random()
    }
}
