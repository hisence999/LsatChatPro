package me.rerere.rikkahub.ui.pages.chat

import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime as JavaLocalDateTime
import kotlin.uuid.Uuid

class ExportMarkdownTest {
    @Test
    fun markdownExport_shouldExcludeReasoningAndIncludeSecondTimestamps() {
        val conversation = Conversation(
            assistantId = Uuid.random(),
            title = "Test Conversation",
            messageNodes = emptyList(),
        )

        val userMessage = UIMessage(
            role = MessageRole.USER,
            parts = listOf(
                UIMessagePart.Text("Hello"),
                UIMessagePart.Reasoning("secret reasoning"),
            ),
            createdAt = LocalDateTime(2026, 1, 2, 3, 4, 5),
        )

        val assistantMessage = UIMessage(
            role = MessageRole.ASSISTANT,
            parts = listOf(
                UIMessagePart.Thinking("secret thinking"),
                UIMessagePart.Text("Hi there"),
            ),
            createdAt = LocalDateTime(2026, 1, 2, 3, 4, 6),
        )

        val markdown = buildChatMarkdown(
            conversation = conversation,
            messages = listOf(userMessage, assistantMessage),
            exportedAt = JavaLocalDateTime.of(2026, 1, 16, 12, 34, 56),
        )

        assertFalse(markdown.contains("secret reasoning"))
        assertFalse(markdown.contains("secret thinking"))
        assertTrue(markdown.contains("**User** · 2026-01-02 03:04:05"))
        assertTrue(markdown.contains("**Assistant** · 2026-01-02 03:04:06"))
        assertTrue(markdown.contains("Hello"))
        assertTrue(markdown.contains("Hi there"))
        assertTrue(markdown.contains("*Exported on 2026-01-16 12:34:56*"))
    }
}

