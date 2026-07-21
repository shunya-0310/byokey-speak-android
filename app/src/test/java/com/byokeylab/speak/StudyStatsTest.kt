package com.byokeylab.speak

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyStatsTest {

    @Test
    fun streakCountsConsecutiveDaysIncludingToday() {
        val days = setOf(100L, 99L, 98L)
        assertEquals(3, StudyStats.streak(days, today = 100L))
    }

    @Test
    fun streakSurvivesWhenTodayNotYetStudied() {
        val days = setOf(99L, 98L)
        assertEquals(2, StudyStats.streak(days, today = 100L))
    }

    @Test
    fun streakIsZeroAfterGap() {
        val days = setOf(97L, 96L)
        assertEquals(0, StudyStats.streak(days, today = 100L))
    }

    @Test
    fun streakIsZeroWithNoStudyDays() {
        assertEquals(0, StudyStats.streak(emptySet(), today = 100L))
    }

    @Test
    fun streakIgnoresOlderDisconnectedDays() {
        val days = setOf(100L, 99L, 90L, 89L)
        assertEquals(2, StudyStats.streak(days, today = 100L))
    }

    @Test
    fun titleUsesShortMessageAsIs() {
        assertEquals("Hello there", StudyStats.deriveChatTitle("Hello there"))
    }

    @Test
    fun titleTruncatesLongMessage() {
        val title = StudyStats.deriveChatTitle("This is a very long first message that keeps going")
        assertEquals(true, title.length <= 25)
        assertEquals(true, title.endsWith("…"))
    }

    @Test
    fun titleCollapsesWhitespaceAndNewlines() {
        assertEquals("Hello world", StudyStats.deriveChatTitle("Hello\n  world"))
    }

    @Test
    fun titleFallsBackWhenBlank() {
        assertEquals("New chat", StudyStats.deriveChatTitle("   "))
    }

    @Test
    fun naturalReplyExtractsOnlyReplySection() {
        val response = """
            Natural reply: That sounds great!
            Coach notes: Watch your tense.
            Japanese explanation: 時制に注意。
            Better options: Sounds good to me.
        """.trimIndent()
        assertEquals("That sounds great!", StudyStats.naturalReplyOf(response))
    }

    @Test
    fun naturalReplyReturnsWholeTextWithoutLabels() {
        assertEquals("Just a plain reply.", StudyStats.naturalReplyOf("Just a plain reply."))
    }
}
