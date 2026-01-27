package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.uuid.Uuid

class LorebookEntryHistoryTest {
    @Test
    fun applyUndo_create_removesEntryById() {
        val e1 = LorebookEntry(id = Uuid.parse("11111111-1111-1111-1111-111111111111"), name = "A")
        val e2 = LorebookEntry(id = Uuid.parse("22222222-2222-2222-2222-222222222222"), name = "B")
        val lorebook = Lorebook(entries = listOf(e1, e2))

        val result = LorebookEntryHistory.applyUndo(
            lorebook = lorebook,
            action = LorebookEntryHistory.Action.CREATE,
            entryId = e2.id.toString(),
            entryIndex = 1,
            before = null,
        )

        assertEquals(listOf(e1.id.toString()), result.entries.map { it.id.toString() })
    }

    @Test
    fun applyUndo_delete_insertsEntryAtIndex() {
        val e1 = LorebookEntry(id = Uuid.parse("11111111-1111-1111-1111-111111111111"), name = "A")
        val e2 = LorebookEntry(id = Uuid.parse("22222222-2222-2222-2222-222222222222"), name = "B")
        val e3 = LorebookEntry(id = Uuid.parse("33333333-3333-3333-3333-333333333333"), name = "C")
        val lorebook = Lorebook(entries = listOf(e1, e3))

        val result = LorebookEntryHistory.applyUndo(
            lorebook = lorebook,
            action = LorebookEntryHistory.Action.DELETE,
            entryId = e2.id.toString(),
            entryIndex = 1,
            before = e2,
        )

        assertEquals(
            listOf(e1.id.toString(), e2.id.toString(), e3.id.toString()),
            result.entries.map { it.id.toString() },
        )
    }

    @Test
    fun applyUndo_update_replacesExistingEntry() {
        val before = LorebookEntry(id = Uuid.parse("22222222-2222-2222-2222-222222222222"), name = "Before")
        val after = before.copy(name = "After")
        val lorebook = Lorebook(entries = listOf(after))

        val result = LorebookEntryHistory.applyUndo(
            lorebook = lorebook,
            action = LorebookEntryHistory.Action.UPDATE,
            entryId = before.id.toString(),
            entryIndex = 0,
            before = before,
        )

        assertEquals("Before", result.entries.single().name)
    }

    @Test
    fun applyUndo_update_missingBefore_returnsOriginal() {
        val entry = LorebookEntry(id = Uuid.parse("22222222-2222-2222-2222-222222222222"), name = "After")
        val lorebook = Lorebook(entries = listOf(entry))

        val result = LorebookEntryHistory.applyUndo(
            lorebook = lorebook,
            action = LorebookEntryHistory.Action.UPDATE,
            entryId = entry.id.toString(),
            entryIndex = 0,
            before = null,
        )

        assertSame(lorebook, result)
    }

    @Test
    fun applyUndo_delete_outOfRangeIndex_appendsToEnd() {
        val e1 = LorebookEntry(id = Uuid.parse("11111111-1111-1111-1111-111111111111"), name = "A")
        val e2 = LorebookEntry(id = Uuid.parse("22222222-2222-2222-2222-222222222222"), name = "B")
        val lorebook = Lorebook(entries = listOf(e1))

        val result = LorebookEntryHistory.applyUndo(
            lorebook = lorebook,
            action = LorebookEntryHistory.Action.DELETE,
            entryId = e2.id.toString(),
            entryIndex = 999,
            before = e2,
        )

        assertEquals(
            listOf(e1.id.toString(), e2.id.toString()),
            result.entries.map { it.id.toString() },
        )
    }
}
