package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WelcomePhrasesRulesTest {
    @Test
    fun `parse grouped format returns 30 phrases`() {
        val raw = buildGroupedRaw()
        val parsed = parseWelcomePhrases(raw)

        assertEquals(WELCOME_PHRASES_TOTAL, parsed.size)
        assertEquals("morn1", parsed.first())
        assertEquals("anyt10", parsed.last())
    }

    @Test
    fun `parse supports fullwidth separators`() {
        val raw = buildGroupedRaw()
            .replace('#', '＃')
            .replace('&', '＆')

        val parsed = parseWelcomePhrases(raw)
        assertEquals(WELCOME_PHRASES_TOTAL, parsed.size)
        assertEquals("morn1", parsed.first())
        assertEquals("anyt10", parsed.last())
    }

    @Test
    fun `candidates include slot and general`() {
        val phrases = parseWelcomePhrases(buildGroupedRaw())

        val morningCandidates = getWelcomePhraseCandidates(phrases, LocalTime.of(6, 0))
        assertEquals(15, morningCandidates.size)
        assertTrue(morningCandidates.contains("morn1"))
        assertTrue(morningCandidates.contains("anyt10"))
        assertFalse(morningCandidates.contains("noon1"))

        val afternoonCandidates = getWelcomePhraseCandidates(phrases, LocalTime.of(11, 0))
        assertTrue(afternoonCandidates.contains("noon1"))
        assertFalse(afternoonCandidates.contains("morn1"))

        val eveningCandidates = getWelcomePhraseCandidates(phrases, LocalTime.of(17, 0))
        assertTrue(eveningCandidates.contains("dusk1"))
        assertFalse(eveningCandidates.contains("noon1"))

        val lateNightCandidates = getWelcomePhraseCandidates(phrases, LocalTime.of(23, 0))
        assertTrue(lateNightCandidates.contains("nite1"))
        assertFalse(lateNightCandidates.contains("dusk1"))

        val earlyMorningCandidates = getWelcomePhraseCandidates(phrases, LocalTime.of(5, 59))
        assertTrue(earlyMorningCandidates.contains("nite1"))
        assertFalse(earlyMorningCandidates.contains("morn1"))
    }

    private fun buildGroupedRaw(): String {
        val morning = (1..5).map { "morn$it" }
        val afternoon = (1..5).map { "noon$it" }
        val evening = (1..5).map { "dusk$it" }
        val lateNight = (1..5).map { "nite$it" }
        val general = (1..10).map { "anyt$it" }

        return listOf(morning, afternoon, evening, lateNight, general)
            .joinToString("&") { it.joinToString("#") }
    }
}

