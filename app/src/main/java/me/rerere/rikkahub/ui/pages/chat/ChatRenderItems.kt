package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.highlight.HighlightText
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.AtomOneDarkPalette
import me.rerere.rikkahub.ui.theme.AtomOneLightPalette
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.JsonInstantPretty
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

internal sealed interface ChatRenderItem {
    val key: String
    val anchorNodeId: Uuid
    val startIndex: Int
    val endIndex: Int
}

internal data class ChatNodeRenderItem(
    val node: MessageNode,
    override val startIndex: Int,
) : ChatRenderItem {
    override val key: String = node.id.toString()
    override val anchorNodeId: Uuid = node.id
    override val endIndex: Int = startIndex
}

internal data class AssistantTurnRenderItem(
    val nodes: List<MessageNode>,
    override val startIndex: Int,
    override val endIndex: Int,
) : ChatRenderItem {
    override val key: String = "turn-${nodes.first().id}"
    override val anchorNodeId: Uuid = nodes.first().id

    val finalAssistantNode: MessageNode? = nodes.lastOrNull { it.currentMessage.role == MessageRole.ASSISTANT }
    val processEvents: List<AssistantTurnProcessEvent> = buildAssistantTurnProcessEvents(nodes)
    val nodeIds: Set<Uuid> = nodes.map { it.id }.toSet()
    val reasoningCount: Int = processEvents.count { it is AssistantTurnProcessEvent.ReasoningEvent }
    val toolCount: Int = processEvents.count { it is AssistantTurnProcessEvent.ToolEvent }
    val pendingApprovals: Int = processEvents.count {
        it is AssistantTurnProcessEvent.ToolEvent && it.approval?.state == ToolApprovalState.Pending
    }
}

internal sealed interface AssistantTurnProcessEvent {
    val key: String

    data class ReasoningEvent(
        override val key: String,
        val reasoning: UIMessagePart.Reasoning,
    ) : AssistantTurnProcessEvent

    data class ToolEvent(
        override val key: String,
        val toolCallId: String,
        val toolName: String,
        val arguments: JsonElement,
        val approval: UIMessagePart.ToolApproval? = null,
        val result: UIMessagePart.ToolResult? = null,
    ) : AssistantTurnProcessEvent
}

internal fun buildChatRenderItems(
    conversation: Conversation,
    enableAssistantTurnGrouping: Boolean,
): List<ChatRenderItem> {
    if (!enableAssistantTurnGrouping) {
        return conversation.messageNodes.mapIndexed { index, node -> ChatNodeRenderItem(node, index) }
    }

    val result = mutableListOf<ChatRenderItem>()
    var index = 0
    while (index < conversation.messageNodes.size) {
        val node = conversation.messageNodes[index]
        if (node.currentMessage.role == MessageRole.USER) {
            result += ChatNodeRenderItem(node, index)
            index++
            continue
        }

        val start = index
        val nodes = mutableListOf<MessageNode>()
        while (index < conversation.messageNodes.size && conversation.messageNodes[index].currentMessage.role != MessageRole.USER) {
            nodes += conversation.messageNodes[index]
            index++
        }
        result += AssistantTurnRenderItem(nodes = nodes, startIndex = start, endIndex = index - 1)
    }
    return result
}

private fun buildAssistantTurnProcessEvents(nodes: List<MessageNode>): List<AssistantTurnProcessEvent> {
    val events = mutableListOf<AssistantTurnProcessEvent>()
    val toolEventIndex = mutableMapOf<String, Int>()

    fun upsertToolEvent(
        toolCallId: String,
        toolName: String,
        arguments: JsonElement,
        approval: UIMessagePart.ToolApproval? = null,
        result: UIMessagePart.ToolResult? = null,
    ) {
        val key = if (toolCallId.isNotBlank()) toolCallId else "tool-${events.size}-$toolName"
        val existingIndex = toolEventIndex[toolCallId]
        val existingEvent = existingIndex?.let { events[it] as? AssistantTurnProcessEvent.ToolEvent }
        val merged = AssistantTurnProcessEvent.ToolEvent(
            key = key,
            toolCallId = toolCallId,
            toolName = toolName,
            arguments = if (isPlaceholderToolArguments(arguments)) {
                existingEvent?.arguments ?: arguments
            } else {
                arguments
            },
            approval = approval ?: existingEvent?.approval,
            result = result ?: existingEvent?.result,
        )
        if (existingIndex == null) {
            toolEventIndex[toolCallId] = events.size
            events += merged
        } else {
            events[existingIndex] = merged
        }
    }

    nodes.forEach { node ->
        node.currentMessage.parts.forEachIndexed { index, part ->
            when (part) {
                is UIMessagePart.Reasoning -> {
                    events += AssistantTurnProcessEvent.ReasoningEvent(
                        key = "reasoning-${node.id}-$index",
                        reasoning = part,
                    )
                }

                is UIMessagePart.ToolCall -> {
                    upsertToolEvent(
                        toolCallId = part.toolCallId,
                        toolName = part.toolName,
                        arguments = runCatching { me.rerere.rikkahub.utils.JsonInstant.parseToJsonElement(part.arguments) }
                            .getOrElse { JsonObject(emptyMap()) },
                    )
                }

                is UIMessagePart.ToolApproval -> {
                    upsertToolEvent(
                        toolCallId = part.toolCallId,
                        toolName = part.toolName,
                        arguments = JsonObject(emptyMap()),
                        approval = part,
                    )
                }

                is UIMessagePart.ToolResult -> {
                    upsertToolEvent(
                        toolCallId = part.toolCallId,
                        toolName = part.toolName,
                        arguments = part.arguments,
                        result = part,
                    )
                }

                else -> Unit
            }
        }
    }

    return events
}

@Composable
internal fun AssistantTurnCard(
    item: AssistantTurnRenderItem,
    conversationId: Uuid,
    assistant: Assistant?,
    model: Model?,
    loading: Boolean,
    isLast: Boolean,
    showAnswer: Boolean = true,
    onCitationClick: (String) -> Unit,
    onRegenerate: (me.rerere.ai.ui.UIMessage) -> Unit,
    onContinue: (me.rerere.ai.ui.UIMessage) -> Unit,
    onEdit: (me.rerere.ai.ui.UIMessage) -> Unit,
    onForkMessage: (me.rerere.ai.ui.UIMessage) -> Unit,
    onDelete: (me.rerere.ai.ui.UIMessage) -> Unit,
    onUpdateMessage: (MessageNode) -> Unit,
) {
    val finalNode = item.finalAssistantNode
    val finalMessage = finalNode?.currentMessage
    val settings = LocalSettings.current
    val effectiveDisplay = settings.getEffectiveDisplaySetting(assistant)
    val hasPendingApproval = item.pendingApprovals > 0
    val hasAnswerOutputStarted = remember(finalMessage?.parts) {
        finalMessage
            ?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.any { it.text.isNotBlank() }
            ?: false
    }
    var processExpanded by remember(item.key, effectiveDisplay.autoCloseThinking) {
        mutableStateOf(!effectiveDisplay.autoCloseThinking && item.processEvents.isNotEmpty())
    }
    var processExpansionControlledByUser by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(item.processEvents.size, loading, hasPendingApproval, hasAnswerOutputStarted, effectiveDisplay.autoCloseThinking) {
        if (processExpansionControlledByUser) return@LaunchedEffect

        processExpanded = when {
            effectiveDisplay.autoCloseThinking -> false
            hasPendingApproval -> true
            hasAnswerOutputStarted -> false
            else -> item.processEvents.isNotEmpty()
        }
    }
    val visibleProcessEvents = remember(
        item.processEvents,
        processExpanded,
        processExpansionControlledByUser,
        effectiveDisplay.autoCloseThinking,
        loading,
        hasPendingApproval,
        hasAnswerOutputStarted,
    ) {
        when {
            processExpanded -> item.processEvents
            processExpansionControlledByUser -> emptyList()
            effectiveDisplay.autoCloseThinking -> {
                if ((loading || hasPendingApproval) && !hasAnswerOutputStarted) {
                    item.processEvents.lastOrNull()?.let(::listOf).orEmpty()
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    val headerInteraction = remember { MutableInteractionSource() }
    val pressed by headerInteraction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "assistant_turn_scale",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val assistantContentStart = 48.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (assistant != null) {
                UIAvatar(
                    name = assistant.name.ifBlank { "Assistant" },
                    value = assistant.avatar,
                    modifier = Modifier.size(36.dp),
                )
            } else {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.padding(8.dp))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = assistant?.name?.ifBlank { "Assistant" } ?: "Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Card(
            shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = headerInteraction, indication = null) {
                            processExpansionControlledByUser = true
                            processExpanded = !processExpanded
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val (icon, text, type) = when {
                                hasPendingApproval -> Triple(Icons.Rounded.HourglassTop, stringResource(R.string.chat_turn_status_waiting_approval), TagType.WARNING)
                                loading -> Triple(Icons.Rounded.Psychology, stringResource(R.string.chat_turn_status_running), TagType.INFO)
                                else -> Triple(Icons.Rounded.CheckCircle, stringResource(R.string.chat_turn_status_completed), TagType.SUCCESS)
                            }
                            Tag(type = type) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(text, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (item.reasoningCount > 0) {
                                Tag(type = TagType.DEFAULT) { Text(stringResource(R.string.chat_turn_reasoning_count, item.reasoningCount), style = MaterialTheme.typography.labelSmall) }
                            }
                            if (item.toolCount > 0) {
                                Tag(type = TagType.DEFAULT) { Text(stringResource(R.string.chat_turn_tool_count, item.toolCount), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    Icon(
                        imageVector = if (processExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                    )
                }

                if (visibleProcessEvents.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        visibleProcessEvents.forEach { event ->
                            when (event) {
                                is AssistantTurnProcessEvent.ReasoningEvent -> {
                                    AssistantTurnReasoningRow(reasoning = event.reasoning)
                                }

                                is AssistantTurnProcessEvent.ToolEvent -> {
                                    AssistantTurnToolRow(
                                        conversationId = conversationId,
                                        event = event,
                                        loading = loading && event.result == null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAnswer) {
            Box(modifier = Modifier.padding(start = assistantContentStart)) {
                finalNode
                    ?.toFinalAssistantNode()
                    ?.takeIf { !it.currentMessage.parts.isEmptyUIMessage() }
                    ?.let { answerNode ->
                        ChatMessage(
                            node = answerNode,
                            previousRole = null,
                            isLast = isLast,
                            conversationId = conversationId,
                            onCitationClick = onCitationClick,
                            loading = loading,
                            model = model,
                            assistant = assistant,
                            onFork = { finalMessage?.let(onForkMessage) },
                            onRegenerate = { finalMessage?.let(onRegenerate) },
                            onContinue = { finalMessage?.let(onContinue) },
                            canContinue = isLast,
                            onEdit = { finalMessage?.let(onEdit) },
                            onShare = { },
                            onDelete = { finalMessage?.let(onDelete) },
                            onUpdate = onUpdateMessage,
                            showAvatarRow = false,
                            showProcessSections = false,
                        )
                    }
            }
        }
    }
}

@Composable
private fun AssistantTurnReasoningRow(
    reasoning: UIMessagePart.Reasoning,
) {
    var expanded by remember(reasoning.createdAt) { mutableStateOf(true) }
    val seconds = reasoning.finishedAt?.let { (it - reasoning.createdAt).inWholeMilliseconds / 1000f }
    val loading = reasoning.finishedAt == null
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AssistantTurnTimelineIcon(icon = Icons.Rounded.Psychology)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = seconds?.let { stringResource(R.string.chat_turn_reasoning_time, it) }
                        ?: stringResource(R.string.chat_turn_reasoning_running),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Text(
                    text = reasoning.reasoning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AssistantTurnToolRow(
    conversationId: Uuid,
    event: AssistantTurnProcessEvent.ToolEvent,
    loading: Boolean,
) {
    var showDetails by remember(event.key) { mutableStateOf(false) }
    val argsSummary = remember(event.toolName, event.arguments) {
        summarizeToolArguments(event.toolName, event.arguments)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AssistantTurnTimelineIcon(icon = Icons.Rounded.Build)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDetails = true },
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chat_message_tool_call_generic, event.toolName),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    argsSummary?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            event.approval?.let { approval ->
                AssistantTurnApprovalRow(
                    conversationId = conversationId,
                    toolCallId = approval.toolCallId,
                    toolName = approval.toolName,
                    arguments = event.arguments,
                    state = approval.state,
                    loading = loading && approval.state == ToolApprovalState.Approved && event.result == null,
                )
            }
        }
    }
    if (showDetails) {
        AssistantTurnToolDetailSheet(
            toolName = event.toolName,
            arguments = event.arguments,
            result = event.result?.content,
            onDismissRequest = { showDetails = false },
        )
    }
}

@Composable
private fun AssistantTurnApprovalRow(
    conversationId: Uuid,
    toolCallId: String,
    toolName: String,
    arguments: JsonElement,
    state: ToolApprovalState,
    loading: Boolean,
) {
    val chatService = koinInject<ChatService>()
    val settings = LocalSettings.current
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val scope = rememberCoroutineScope()
    var locked by remember(toolCallId, state) { mutableStateOf(false) }
    val approvalArgumentsText = remember(arguments) { formatApprovalArguments(arguments) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.mcp_tool_approval_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = approvalArgumentsText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        when (state) {
            ToolApprovalState.Pending -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AssistantTurnApprovalButton(
                        text = stringResource(R.string.mcp_tool_approval_approve),
                        icon = Icons.Rounded.Check,
                        enabled = !locked,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        onClick = {
                            if (locked) return@AssistantTurnApprovalButton
                            locked = true
                            haptics.perform(HapticPattern.Pop)
                            scope.launch {
                                chatService.respondToolApproval(conversationId, toolCallId, true)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    AssistantTurnApprovalButton(
                        text = stringResource(R.string.mcp_tool_approval_reject),
                        icon = Icons.Rounded.Close,
                        enabled = !locked,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = {
                            if (locked) return@AssistantTurnApprovalButton
                            locked = true
                            haptics.perform(HapticPattern.Pop)
                            scope.launch {
                                chatService.respondToolApproval(conversationId, toolCallId, false)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            ToolApprovalState.Approved -> {
                Text(
                    text = if (loading) stringResource(R.string.mcp_tool_approval_approved_calling, toolName) else stringResource(R.string.chat_turn_status_completed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ToolApprovalState.Rejected -> {
                Text(
                    text = stringResource(R.string.mcp_tool_approval_rejected_result),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AssistantTurnApprovalButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "assistant_turn_approval_button_scale",
    )
    Surface(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        shape = me.rerere.rikkahub.ui.theme.AppShapes.ButtonPill,
        color = containerColor,
        contentColor = contentColor,
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AssistantTurnTimelineIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .graphicsLayer { alpha = 0.25f }
                .padding(top = 0.dp)
        ) {
            Surface(color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth().height(24.dp)) {}
        }
    }
}

private fun summarizeToolResult(content: JsonElement): String {
    val obj = content as? JsonObject ?: return content.toString().take(120)
    obj["error"]?.jsonPrimitive?.contentOrNull?.let { return it.take(120) }
    obj["result"]?.jsonPrimitive?.contentOrNull?.let { return it.take(120) }
    obj["content"]?.jsonPrimitive?.contentOrNull?.let { return it.take(120) }
    obj["answer"]?.jsonPrimitive?.contentOrNull?.let { return it.take(120) }
    obj["items"]?.jsonArray?.let { return "${it.size} items" }
    return JsonInstantPretty.encodeToString(content).lineSequence().firstOrNull()?.take(120).orEmpty()
}

private fun isPlaceholderToolArguments(arguments: JsonElement): Boolean {
    return arguments is JsonObject && arguments.isEmpty()
}

private fun summarizeToolArguments(
    @Suppress("UNUSED_PARAMETER") toolName: String,
    arguments: JsonElement,
): String {
    val obj = arguments as? JsonObject ?: return ""
    val description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return normalizeInline(description)
}

private fun formatApprovalArguments(arguments: JsonElement): String {
    val lines = mutableListOf<String>()

    fun appendValue(path: String, value: String) {
        val label = path.ifBlank { "value" }
        if (value.contains('\n') || value.contains('\r')) {
            lines += "$label:"
            value.lineSequence().forEach { line ->
                lines += "  $line"
            }
        } else {
            lines += "$label: $value"
        }
    }

    fun visit(element: JsonElement, path: String) {
        when (element) {
            is JsonObject -> {
                if (element.isEmpty()) {
                    appendValue(path, "(empty)")
                } else {
                    element.forEach { (key, value) ->
                        val nextPath = if (path.isBlank()) key else "$path.$key"
                        visit(value, nextPath)
                    }
                }
            }

            is JsonArray -> {
                if (element.isEmpty()) {
                    appendValue(path, "[]")
                } else {
                    element.forEachIndexed { index, value ->
                        val nextPath = if (path.isBlank()) "[$index]" else "$path[$index]"
                        visit(value, nextPath)
                    }
                }
            }

            else -> appendValue(path, element.jsonPrimitive.contentOrNull ?: element.toString())
        }
    }

    visit(arguments, "")
    return lines.joinToString("\n").trim().ifBlank { arguments.toString() }
}

private fun normalizeInline(value: String): String {
    return value
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("^[\\[\\{\"]+|[\\]\\}\"]+$"), "")
        .trim()
        .let { if (it.length > 80) it.take(77) + "…" else it }
}

@Composable
private fun AssistantTurnToolDetailSheet(
    toolName: String,
    arguments: JsonElement,
    result: JsonElement?,
    onDismissRequest: () -> Unit,
) {
    val argumentsJson = remember(arguments) { JsonInstantPretty.encodeToString(arguments) }
    val resultDisplay = remember(result) { result?.let(::buildToolResultDisplayContent) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_message_tool_call_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = stringResource(R.string.chat_message_tool_call_label, toolName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToolDetailCodeCard(
                code = argumentsJson,
                language = "json",
            )
            resultDisplay?.let { display ->
                Text(
                    text = stringResource(R.string.chat_message_tool_call_result),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToolDetailCodeCard(
                    code = display.code,
                    language = display.language,
                )
            }
        }
    }
}

private data class ToolDetailDisplayContent(
    val code: String,
    val language: String,
)

private fun buildToolResultDisplayContent(result: JsonElement): ToolDetailDisplayContent {
    fun primitiveText(element: JsonElement?): String? = element?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    if (result is JsonArray && result.size == 1) {
        val first = result.firstOrNull()
        val text = primitiveText((first as? JsonObject)?.get("text"))
        if (text != null) {
            return ToolDetailDisplayContent(code = text, language = "text")
        }
    }

    if (result is JsonObject) {
        primitiveText(result["text"])?.let { return ToolDetailDisplayContent(code = it, language = "text") }
        primitiveText(result["content"])?.let { return ToolDetailDisplayContent(code = it, language = "text") }
        primitiveText(result["answer"])?.let { return ToolDetailDisplayContent(code = it, language = "markdown") }
        primitiveText(result["result"])?.let { return ToolDetailDisplayContent(code = it, language = "text") }

        val stdout = primitiveText(result["stdout"])
        val stderr = primitiveText(result["stderr"])
        if (stdout != null || stderr != null) {
            val text = buildString {
                stdout?.let {
                    append("STDOUT")
                    append("\n")
                    append(it)
                }
                if (stdout != null && stderr != null) {
                    append("\n\n")
                }
                stderr?.let {
                    append("STDERR")
                    append("\n")
                    append(it)
                }
            }
            return ToolDetailDisplayContent(code = text, language = "text")
        }
    }

    return ToolDetailDisplayContent(
        code = JsonInstantPretty.encodeToString(result),
        language = "json",
    )
}

@Composable
private fun ToolDetailCodeCard(
    code: String,
    language: String,
) {
    val colorPalette = if (LocalDarkMode.current) AtomOneDarkPalette else AtomOneLightPalette
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (language == "json") {
                        HighlightText(
                            code = code,
                            language = language,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            colors = colorPalette,
                            overflow = TextOverflow.Visible,
                            softWrap = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

internal fun MessageNode.toFinalAssistantNode(): MessageNode {
    val message = currentMessage
    val filteredParts = message.parts.filterNot {
        it is UIMessagePart.Reasoning ||
            it is UIMessagePart.ToolCall ||
            it is UIMessagePart.ToolApproval ||
            it is UIMessagePart.ToolResult
    }
    return copy(
        messages = listOf(message.copy(parts = filteredParts)),
        selectIndex = 0,
    )
}

