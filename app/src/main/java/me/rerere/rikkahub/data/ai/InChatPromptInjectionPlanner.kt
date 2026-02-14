package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.model.InjectionPosition

internal data class InChatPromptInjection(
    val position: InjectionPosition,
    val prompt: String,
    val depth: Int = 0,
)

internal fun applyInChatPromptInjections(
    baseMessages: List<UIMessage>,
    injections: List<InChatPromptInjection>,
): List<UIMessage> {
    if (injections.isEmpty()) return baseMessages

    val slots = Array(baseMessages.size + 1) { mutableListOf<InChatPromptInjection>() }
    injections.forEach { injection ->
        if (injection.prompt.isBlank()) return@forEach

        val slotIndex = when (injection.position) {
            InjectionPosition.TOP_OF_CHAT -> 0
            InjectionPosition.BEFORE_LATEST -> {
                val lastUserIndex = baseMessages.indexOfLast { it.role == MessageRole.USER }
                if (lastUserIndex >= 0) lastUserIndex else baseMessages.size
            }

            InjectionPosition.AT_DEPTH -> {
                val normalizedDepth = injection.depth.coerceAtLeast(0)
                when {
                    normalizedDepth == 0 -> baseMessages.size
                    normalizedDepth >= baseMessages.size -> 0
                    else -> baseMessages.size - normalizedDepth
                }
            }

            else -> null
        }

        if (slotIndex != null) {
            slots[slotIndex].add(injection)
        }
    }

    if (slots.all { it.isEmpty() }) return baseMessages

    return buildList {
        for (slotIndex in 0..baseMessages.size) {
            slots[slotIndex].forEach { injection ->
                add(UIMessage.user(injection.prompt))
            }
            if (slotIndex < baseMessages.size) {
                add(baseMessages[slotIndex])
            }
        }
    }
}
