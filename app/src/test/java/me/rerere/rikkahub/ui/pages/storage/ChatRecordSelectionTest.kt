package me.rerere.rikkahub.ui.pages.storage

import me.rerere.rikkahub.data.repository.ChatRecordsMonthEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRecordSelectionTest {
    @Test
    fun `filterChatRecordSelectionsByValidMonths drops invalid keys`() {
        val selections = mapOf(
            "2026-01" to ChatRecordMonthSelection.All(selectedCount = 10),
            "2026-02" to ChatRecordMonthSelection.Some(conversationIds = setOf("a", "b")),
            "2025-12" to ChatRecordMonthSelection.All(selectedCount = 5),
        )
        val valid = setOf("2026-01", "2026-02")

        val filtered = filterChatRecordSelectionsByValidMonths(selections, valid)

        assertEquals(2, filtered.size)
        assertTrue(filtered.containsKey("2026-01"))
        assertTrue(filtered.containsKey("2026-02"))
    }

    @Test
    fun `buildChatRecordClearTargets splits months and conversation ids`() {
        val selections = mapOf(
            "2026-01" to ChatRecordMonthSelection.All(selectedCount = 10),
            "2026-02" to ChatRecordMonthSelection.Some(conversationIds = setOf("id_1", "id_2")),
        )

        val targets = buildChatRecordClearTargets(selections)

        assertEquals(setOf("2026-01"), targets.yearMonths)
        assertEquals(setOf("id_1", "id_2"), targets.conversationIds)
    }

    @Test
    fun `buildChatRecordSelectionSummary counts months and conversations`() {
        val monthEntries = listOf(
            ChatRecordsMonthEntry(yearMonth = "2026-02", conversationCount = 20),
            ChatRecordsMonthEntry(yearMonth = "2026-01", conversationCount = 10),
        )
        val selections = mapOf(
            "2026-01" to ChatRecordMonthSelection.All(selectedCount = 10),
            "2026-02" to ChatRecordMonthSelection.Some(conversationIds = setOf("a", "b")),
        )

        val summary = buildChatRecordSelectionSummary(monthEntries, selections)

        assertEquals(2, summary.selectedMonthCount)
        assertEquals(12, summary.selectedConversationCount)
        assertEquals(30, summary.totalConversationCount)
    }
}

