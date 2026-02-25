package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LargeContextWarningPolicyTest {
    @Test
    fun resolveMessageCount_prefersTotalCountWhenConversationIsWindowed() {
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(messageNode(MessageRole.USER, "hello")),
            totalMessageNodeCount = 820,
        )

        assertEquals(820, LargeContextWarningPolicy.resolveMessageCount(conversation))
    }

    @Test
    fun findLatestAssistantPromptTokens_usesMostRecentAssistantMessage() {
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(
                messageNode(MessageRole.ASSISTANT, "old assistant", promptTokens = 120000),
                messageNode(MessageRole.USER, "user follow-up"),
                messageNode(MessageRole.ASSISTANT, "latest assistant", promptTokens = 360000),
                messageNode(MessageRole.USER, "new user message"),
            ),
        )

        assertEquals(360000, LargeContextWarningPolicy.findLatestAssistantPromptTokens(conversation))
    }

    @Test
    fun findLatestAssistantPromptTokens_returnsZeroWhenNoAssistantMessage() {
        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(
                messageNode(MessageRole.USER, "hello"),
                messageNode(MessageRole.TOOL, "tool output"),
            ),
        )

        assertEquals(0, LargeContextWarningPolicy.findLatestAssistantPromptTokens(conversation))
    }

    @Test
    fun shouldShowWarning_requiresStrictGreaterThanAndNotShown() {
        assertTrue(
            LargeContextWarningPolicy.shouldShowWarning(
                messageCount = 769,
                latestAssistantPromptTokens = 300001,
                hasBeenShown = false,
            )
        )
        assertFalse(
            LargeContextWarningPolicy.shouldShowWarning(
                messageCount = 768,
                latestAssistantPromptTokens = 300001,
                hasBeenShown = false,
            )
        )
        assertFalse(
            LargeContextWarningPolicy.shouldShowWarning(
                messageCount = 769,
                latestAssistantPromptTokens = 300000,
                hasBeenShown = false,
            )
        )
        assertFalse(
            LargeContextWarningPolicy.shouldShowWarning(
                messageCount = 769,
                latestAssistantPromptTokens = 300001,
                hasBeenShown = true,
            )
        )
    }

    private fun messageNode(
        role: MessageRole,
        text: String,
        promptTokens: Int = 0,
    ): MessageNode {
        return MessageNode(
            messages = listOf(
                UIMessage(
                    role = role,
                    parts = listOf(UIMessagePart.Text(text)),
                    usage = if (promptTokens > 0) {
                        TokenUsage(promptTokens = promptTokens)
                    } else {
                        null
                    }
                )
            )
        )
    }
}
