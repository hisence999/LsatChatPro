package me.rerere.rikkahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed interface ChatTarget {
    @Serializable
    @SerialName("assistant")
    data class Assistant(
        val assistantId: Uuid,
    ) : ChatTarget

    @Serializable
    @SerialName("group_chat")
    data class GroupChat(
        val templateId: Uuid,
    ) : ChatTarget
}

val ChatTarget.id: Uuid
    get() = when (this) {
        is ChatTarget.Assistant -> assistantId
        is ChatTarget.GroupChat -> templateId
    }

