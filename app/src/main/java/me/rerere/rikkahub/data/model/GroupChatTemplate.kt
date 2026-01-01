package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class GroupChatTemplate(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val hostModelId: Uuid? = null,
    val integrationModelId: Uuid? = null,
    val seats: List<GroupChatSeat> = emptyList(),
)

@Serializable
data class GroupChatSeat(
    val id: Uuid = Uuid.random(),
    val assistantId: Uuid,
    val instanceNumber: Int = 1,
    val overrides: GroupChatSeatOverrides = GroupChatSeatOverrides(),
    val defaultEnabled: Boolean = true,
)

@Serializable
data class GroupChatSeatOverrides(
    val chatModelId: Uuid? = null,
    val thinkingBudget: Int? = null,
    val maxTokens: Int? = null,
    val searchEnabled: Boolean = false,
    val memoryEnabled: Boolean = false,
    val searchMode: AssistantSearchMode = AssistantSearchMode.Off,
    val preferBuiltInSearch: Boolean = false,
    val mcpServerIds: Set<Uuid> = emptySet(),
)
