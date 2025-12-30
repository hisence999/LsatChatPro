package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ChatTarget
import me.rerere.rikkahub.data.model.GroupChatTemplate

@Composable
fun rememberChatTargetState(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
): ChatTargetState {
    return remember(settings, onUpdateSettings) {
        ChatTargetState(settings, onUpdateSettings)
    }
}

class ChatTargetState(
    private val settings: Settings,
    private val onUpdateSettings: (Settings) -> Unit,
) {
    val currentTarget: ChatTarget = settings.chatTarget

    val currentAssistant: Assistant?
        get() = when (val target = currentTarget) {
            is ChatTarget.Assistant -> settings.assistants.find { it.id == target.assistantId }
            is ChatTarget.GroupChat -> null
        }

    val currentGroupChat: GroupChatTemplate?
        get() = when (val target = currentTarget) {
            is ChatTarget.Assistant -> null
            is ChatTarget.GroupChat -> settings.groupChatTemplates.find { it.id == target.templateId }
        }

    fun selectAssistant(assistant: Assistant) {
        onUpdateSettings(
            settings.copy(
                assistantId = assistant.id,
                chatTarget = ChatTarget.Assistant(assistant.id),
            )
        )
    }

    fun selectGroupChat(template: GroupChatTemplate) {
        onUpdateSettings(
            settings.copy(
                chatTarget = ChatTarget.GroupChat(template.id),
            )
        )
    }
}

