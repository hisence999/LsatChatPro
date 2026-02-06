package me.rerere.rikkahub.ui.pages.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentListStateTest {
    private data class FakeAttachment(
        val path: String,
        val bytes: Long,
    )

    @Test
    fun `buildAttachmentListState limits items and reports hasMore`() {
        val items = listOf("a", "b", "c", "d")
        val state = buildAttachmentListState(
            allItems = items,
            limit = 2,
            totalBytes = 120L,
        )

        assertEquals(listOf("a", "b"), state.items)
        assertEquals(4, state.totalCount)
        assertEquals(120L, state.totalBytes)
        assertTrue(state.hasMore)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun `buildAttachmentListState returns all items when limit exceeds size`() {
        val items = listOf("a", "b", "c")
        val state = buildAttachmentListState(
            allItems = items,
            limit = 10,
            totalBytes = 90L,
        )

        assertEquals(items, state.items)
        assertEquals(3, state.totalCount)
        assertEquals(90L, state.totalBytes)
        assertFalse(state.hasMore)
    }

    @Test
    fun `buildAttachmentListState supports zero limit and loading state`() {
        val items = listOf("a", "b")
        val state = buildAttachmentListState(
            allItems = items,
            limit = 0,
            totalBytes = 50L,
            isLoadingMore = true,
        )

        assertEquals(emptyList<String>(), state.items)
        assertEquals(2, state.totalCount)
        assertEquals(50L, state.totalBytes)
        assertTrue(state.hasMore)
        assertTrue(state.isLoadingMore)
    }

    @Test
    fun `delete convergence keeps global pagination stable`() {
        val allItems = listOf(
            FakeAttachment("a", 10L),
            FakeAttachment("b", 20L),
            FakeAttachment("c", 30L),
            FakeAttachment("d", 40L),
            FakeAttachment("e", 50L),
        )
        val limit = 3

        val afterDelete = allItems.filterNot { it.path == "b" }
        val state = buildAttachmentListState(
            allItems = afterDelete,
            limit = limit,
            totalBytes = afterDelete.sumOf { it.bytes },
        )

        assertEquals(listOf("a", "c", "d"), state.items.map { it.path })
        assertEquals(4, state.totalCount)
        assertEquals(130L, state.totalBytes)
        assertTrue(state.hasMore)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun `delete convergence keeps assistant list fully loaded`() {
        val allItems = listOf(
            FakeAttachment("a", 10L),
            FakeAttachment("b", 20L),
            FakeAttachment("c", 30L),
        )

        val afterDelete = allItems.filterNot { it.path in setOf("a", "c") }
        val state = buildAttachmentListState(
            allItems = afterDelete,
            limit = afterDelete.size,
            totalBytes = afterDelete.sumOf { it.bytes },
        )

        assertEquals(listOf("b"), state.items.map { it.path })
        assertEquals(1, state.totalCount)
        assertEquals(20L, state.totalBytes)
        assertFalse(state.hasMore)
        assertFalse(state.isLoadingMore)
    }
}
