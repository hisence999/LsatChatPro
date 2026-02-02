package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonElement
import kotlin.uuid.Uuid

data class ToolApprovalRequest(
    val conversationId: Uuid,
    val toolCallId: String,
    val toolName: String,
    val arguments: JsonElement,
)

fun interface ToolApprovalHandler {
    suspend fun requestApproval(request: ToolApprovalRequest): Boolean
}

