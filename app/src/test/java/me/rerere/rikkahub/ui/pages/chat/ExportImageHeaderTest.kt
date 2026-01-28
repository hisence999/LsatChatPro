package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ExportImageHeaderTest {
    private val defaultAssistantName = "Assistant"

    @Test
    fun resolveExportedAssistantHeaderInfo_shouldPreferAssistantWhenEnabled() {
        val assistant = Assistant(name = "Alice", useAssistantAvatar = true)
        val model = Model(modelId = "gpt-4o-mini", displayName = "GPT-4o Mini")

        val headerInfo = resolveExportedAssistantHeaderInfo(
            assistant = assistant,
            model = model,
            forceUseAssistantAvatar = false,
            defaultAssistantName = defaultAssistantName,
        )

        assertSame(assistant, headerInfo.identity)
        assertEquals("Alice", headerInfo.name)
    }

    @Test
    fun resolveExportedAssistantHeaderInfo_shouldUseModelDisplayNameWhenAssistantAvatarDisabled() {
        val assistant = Assistant(name = "Alice", useAssistantAvatar = false)
        val model = Model(modelId = "gpt-4o-mini", displayName = "GPT-4o Mini")

        val headerInfo = resolveExportedAssistantHeaderInfo(
            assistant = assistant,
            model = model,
            forceUseAssistantAvatar = false,
            defaultAssistantName = defaultAssistantName,
        )

        assertNull(headerInfo.identity)
        assertEquals("GPT-4o Mini", headerInfo.name)
    }

    @Test
    fun resolveExportedAssistantHeaderInfo_shouldForceAssistantInGroupChat() {
        val assistant = Assistant(name = "Alice", useAssistantAvatar = false)
        val model = Model(modelId = "gpt-4o-mini", displayName = "GPT-4o Mini")

        val headerInfo = resolveExportedAssistantHeaderInfo(
            assistant = assistant,
            model = model,
            forceUseAssistantAvatar = true,
            defaultAssistantName = defaultAssistantName,
        )

        assertSame(assistant, headerInfo.identity)
        assertEquals("Alice", headerInfo.name)
    }

    @Test
    fun resolveExportedAssistantHeaderInfo_shouldFallbackToDefaultAssistantName() {
        val headerInfo = resolveExportedAssistantHeaderInfo(
            assistant = null,
            model = null,
            forceUseAssistantAvatar = false,
            defaultAssistantName = defaultAssistantName,
        )

        assertNull(headerInfo.identity)
        assertEquals(defaultAssistantName, headerInfo.name)
    }

    @Test
    fun resolveExportedAssistantHeaderInfo_shouldUseDefaultNameWhenAssistantNameBlank() {
        val assistant = Assistant(name = "", useAssistantAvatar = true)
        val model = Model(modelId = "gpt-4o-mini", displayName = "GPT-4o Mini")

        val headerInfo = resolveExportedAssistantHeaderInfo(
            assistant = assistant,
            model = model,
            forceUseAssistantAvatar = false,
            defaultAssistantName = defaultAssistantName,
        )

        assertSame(assistant, headerInfo.identity)
        assertEquals(defaultAssistantName, headerInfo.name)
    }
}

