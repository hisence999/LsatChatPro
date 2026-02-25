package me.rerere.rikkahub.ui.pages.chat

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode

internal object LargeContextWarningPolicy {
    const val MESSAGE_COUNT_THRESHOLD = 768
    const val ASSISTANT_PROMPT_TOKENS_THRESHOLD = 300000

    fun resolveMessageCount(conversation: Conversation): Int {
        return maxOf(conversation.totalMessageNodeCount, conversation.messageNodes.size)
    }

    fun findLatestAssistantPromptTokens(conversation: Conversation): Int {
        for (index in conversation.messageNodes.lastIndex downTo 0) {
            val message = conversation.messageNodes[index].currentMessageOrNull() ?: continue
            if (message.role == MessageRole.ASSISTANT) {
                return message.usage?.promptTokens ?: 0
            }
        }
        return 0
    }

    fun shouldShowWarning(
        messageCount: Int,
        latestAssistantPromptTokens: Int,
        hasBeenShown: Boolean,
    ): Boolean {
        return !hasBeenShown &&
            messageCount > MESSAGE_COUNT_THRESHOLD &&
            latestAssistantPromptTokens > ASSISTANT_PROMPT_TOKENS_THRESHOLD
    }
}

private fun MessageNode.currentMessageOrNull(): UIMessage? {
    return messages.getOrNull(selectIndex)
}
