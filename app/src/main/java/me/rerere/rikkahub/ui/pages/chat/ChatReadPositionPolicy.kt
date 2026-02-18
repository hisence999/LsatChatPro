package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.model.MessageNode
import kotlin.uuid.Uuid

internal fun shouldRunReadPositionRestore(
    initialSearchQuery: String?,
    pendingJumpNodeId: Uuid?,
    previewMode: Boolean,
): Boolean {
    return initialSearchQuery.isNullOrBlank() && pendingJumpNodeId == null && !previewMode
}

internal fun resolveReadPositionNodeIndex(
    messageNodes: List<MessageNode>,
    nodeId: String?,
): Int {
    if (nodeId.isNullOrBlank()) return -1
    val parsed = runCatching { Uuid.parse(nodeId) }.getOrNull() ?: return -1
    return messageNodes.indexOfFirst { it.id == parsed }
}
