package me.rerere.rikkahub.service

import java.time.LocalTime
import kotlin.random.Random

internal const val WELCOME_PHRASES_TOTAL = 30

private const val MORNING_COUNT = 5
private const val AFTERNOON_COUNT = 5
private const val EVENING_COUNT = 5
private const val LATE_NIGHT_COUNT = 5
private const val GENERAL_COUNT = 10

private const val SECTION_SEPARATOR = '&'
private const val PHRASE_SEPARATOR = '#'

private val SECTION_SIZES = intArrayOf(
    MORNING_COUNT,
    AFTERNOON_COUNT,
    EVENING_COUNT,
    LATE_NIGHT_COUNT,
    GENERAL_COUNT,
)

internal fun parseWelcomePhrases(raw: String): List<String> {
    val normalized = raw
        .replace("\r", "")
        .replace("\n", "")
        .replace('＃', PHRASE_SEPARATOR)
        .replace('＆', SECTION_SEPARATOR)
        .trim()

    val bySection = normalized
        .split(SECTION_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (bySection.size == SECTION_SIZES.size) {
        val phrases = mutableListOf<String>()
        bySection.forEachIndexed { sectionIndex, sectionRaw ->
            val expectedCount = SECTION_SIZES[sectionIndex]
            val items = sectionRaw
                .split(PHRASE_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val validItems = items.filter { isWelcomePhraseLengthValid(it) }

            require(validItems.size >= expectedCount) {
                "Section $sectionIndex expected >= $expectedCount valid phrases, got ${validItems.size}"
            }
            phrases += validItems.take(expectedCount)
        }
        return phrases
    }

    return normalized
        .split(PHRASE_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filter { isWelcomePhraseLengthValid(it) }
        .take(WELCOME_PHRASES_TOTAL)
}

internal fun selectWelcomePhrase(
    phrases: List<String>,
    now: LocalTime = LocalTime.now(),
    random: Random = Random.Default,
): String? {
    val candidates = getWelcomePhraseCandidates(phrases, now)
    return candidates.takeIf { it.isNotEmpty() }?.random(random)
}

internal fun getWelcomePhraseCandidates(
    phrases: List<String>,
    now: LocalTime,
): List<String> {
    if (phrases.isEmpty()) return emptyList()
    if (phrases.size < WELCOME_PHRASES_TOTAL) return phrases

    val timeSlice = when {
        now >= LocalTime.of(6, 0) && now < LocalTime.of(11, 0) -> 0 // Morning
        now >= LocalTime.of(11, 0) && now < LocalTime.of(17, 0) -> 1 // Afternoon
        now >= LocalTime.of(17, 0) && now < LocalTime.of(22, 0) -> 2 // Evening
        else -> 3 // Late night
    }

    val perSlotStart = timeSlice * MORNING_COUNT
    val perSlot = phrases.subList(perSlotStart, perSlotStart + MORNING_COUNT)
    val general = phrases.subList(MORNING_COUNT + AFTERNOON_COUNT + EVENING_COUNT + LATE_NIGHT_COUNT, WELCOME_PHRASES_TOTAL)

    return perSlot + general
}

private fun isWelcomePhraseLengthValid(phrase: String): Boolean {
    val length = phrase.codePointCount(0, phrase.length)
    return length in 3..14
}
