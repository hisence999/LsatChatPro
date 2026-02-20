package me.rerere.rikkahub.ui.pages.chat

import me.rerere.rikkahub.data.datastore.ConversationReadPosition
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
    val parsed = parseReadPositionNodeId(nodeId) ?: return -1
    return messageNodes.indexOfFirst { it.id == parsed }
}

internal fun parseReadPositionNodeId(nodeId: String?): Uuid? {
    if (nodeId.isNullOrBlank()) return null
    return runCatching { Uuid.parse(nodeId) }.getOrNull()
}

internal fun isCachedScrollPositionUsable(
    cachedPosition: Pair<Int, Int>?,
    itemCount: Int,
): Boolean {
    val position = cachedPosition ?: return false
    if (itemCount <= 0) return false
    return position.first in 0 until itemCount
}

internal fun shouldPersistConversationReadPosition(
    existing: ConversationReadPosition?,
    incoming: ConversationReadPosition,
): Boolean {
    if (existing == null) return true
    return existing.nodeId != incoming.nodeId ||
        existing.offset != incoming.offset ||
        existing.itemIndex != incoming.itemIndex
}
