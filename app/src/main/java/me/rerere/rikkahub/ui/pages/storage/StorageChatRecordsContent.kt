package me.rerere.rikkahub.ui.pages.storage

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.AssistantAttachmentStats
import me.rerere.rikkahub.data.repository.ChatRecordsMonthEntry
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.UiState
import java.time.YearMonth
import kotlin.uuid.Uuid

@Composable
fun StorageChatRecordsScaffoldContent(
    innerPadding: PaddingValues,
    assistants: List<Assistant>,
    selectedAssistantId: Uuid?,
    onSelectAssistant: (Uuid?) -> Unit,
    monthEntriesState: UiState<List<ChatRecordsMonthEntry>>,
    conversationCountState: UiState<Int>,
    attachmentStatsState: UiState<AssistantAttachmentStats>,
    onClearChatRecordMonths: (Uuid?, Set<String>) -> Unit,
) {
    val haptics = rememberPremiumHaptics()

    var selectedYearMonths by rememberSaveable(selectedAssistantId) { mutableStateOf(emptySet<String>()) }
    var showConfirmClear by rememberSaveable(selectedAssistantId) { mutableStateOf(false) }

    val monthsState = monthEntriesState as? UiState.Success<List<ChatRecordsMonthEntry>>
    val months = monthsState?.data.orEmpty()

    LaunchedEffect(months) {
        if (selectedYearMonths.isEmpty()) return@LaunchedEffect
        val valid = months.asSequence().map { it.yearMonth }.toSet()
        selectedYearMonths = selectedYearMonths.intersect(valid)
    }

    val selectedMonthCount = selectedYearMonths.size
    val totalConversationCount = remember(months) { months.sumOf { it.conversationCount } }
    val selectedConversationCount = remember(months, selectedYearMonths) {
        if (selectedYearMonths.isEmpty()) 0 else months
            .asSequence()
            .filter { it.yearMonth in selectedYearMonths }
            .sumOf { it.conversationCount }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "assistant_filter") {
            AssistantFilterRow(
                assistants = assistants,
                selected = selectedAssistantId,
                onSelect = onSelectAssistant,
            )
        }

        item(key = "chat_records_card") {
            ChatRecordsActionCard(
                selectedAssistantId = selectedAssistantId,
                conversationCountState = conversationCountState,
                attachmentStatsState = attachmentStatsState,
                monthEntriesState = monthEntriesState,
                selectedMonthCount = selectedMonthCount,
                totalConversationCount = totalConversationCount,
                selectedConversationCount = selectedConversationCount,
                onSelectAll = {
                    if (months.isEmpty()) return@ChatRecordsActionCard
                    haptics.perform(HapticPattern.Pop)
                    selectedYearMonths = months.asSequence().map { it.yearMonth }.toSet()
                },
                onClearSelection = {
                    if (selectedYearMonths.isEmpty()) return@ChatRecordsActionCard
                    haptics.perform(HapticPattern.Pop)
                    selectedYearMonths = emptySet()
                },
                onRequestClear = {
                    if (selectedYearMonths.isEmpty()) return@ChatRecordsActionCard
                    haptics.perform(HapticPattern.Pop)
                    showConfirmClear = true
                },
            )
        }

        if (monthEntriesState is UiState.Success) {
            if (months.isEmpty()) {
                item(key = "chat_records_empty") {
                    Text(
                        text = stringResource(R.string.storage_chat_records_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = months,
                    key = { it.yearMonth },
                ) { entry ->
                    val isSelected = entry.yearMonth in selectedYearMonths
                    val selectionMode = selectedMonthCount > 0
                    ChatRecordsMonthRow(
                        entry = entry,
                        selected = isSelected,
                        selectionMode = selectionMode,
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            selectedYearMonths = if (isSelected) {
                                selectedYearMonths - entry.yearMonth
                            } else {
                                selectedYearMonths + entry.yearMonth
                            }
                        },
                        onLongClick = {
                            haptics.perform(HapticPattern.Pop)
                            selectedYearMonths = if (isSelected) {
                                selectedYearMonths - entry.yearMonth
                            } else {
                                selectedYearMonths + entry.yearMonth
                            }
                        },
                    )
                }
            }
        }
    }

    if (showConfirmClear) {
        val assistantName = assistants
            .firstOrNull { it.id == selectedAssistantId }
            ?.name
            ?.trim()
            ?.ifBlank { null }
        val assistantLabel = assistantName ?: stringResource(R.string.storage_filter_all_assistants)

        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text(stringResource(R.string.storage_confirm_clear_records_title)) },
            text = {
                Text(
                    text = buildString {
                        append(assistantLabel)
                        append(" · ")
                        append(stringResource(R.string.storage_confirm_clear_records_desc))
                        append("\n")
                        append(
                            stringResource(
                                R.string.storage_chat_records_months_selected_summary,
                                selectedMonthCount,
                                selectedConversationCount,
                            )
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.perform(HapticPattern.Thud)
                        showConfirmClear = false
                        val targets = selectedYearMonths.toSet()
                        selectedYearMonths = emptySet()
                        onClearChatRecordMonths(selectedAssistantId, targets)
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ChatRecordsActionCard(
    selectedAssistantId: Uuid?,
    conversationCountState: UiState<Int>,
    attachmentStatsState: UiState<AssistantAttachmentStats>,
    monthEntriesState: UiState<List<ChatRecordsMonthEntry>>,
    selectedMonthCount: Int,
    totalConversationCount: Int,
    selectedConversationCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRequestClear: () -> Unit,
) {
    val context = LocalContext.current

    Card(
        shape = AppShapes.CardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_chat_records_assistant_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = stringResource(R.string.storage_chat_records_preview_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selectedAssistantId != null) {
                val convCountText = when (conversationCountState) {
                    UiState.Idle,
                    UiState.Loading,
                    -> stringResource(R.string.storage_manager_loading_placeholder)

                    is UiState.Error -> conversationCountState.error.message ?: "Error"
                    is UiState.Success -> stringResource(R.string.storage_chat_records_count, conversationCountState.data)
                }

                Text(
                    text = convCountText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (attachmentStatsState is UiState.Success) {
                    val stats = attachmentStatsState.data
                    val totalAttachmentBytes = stats.imageBytes + stats.fileBytes
                    val totalAttachmentCount = stats.imageCount + stats.fileCount
                    val sizeText = runCatching { Formatter.formatShortFileSize(context, totalAttachmentBytes) }.getOrNull()
                        ?: "${totalAttachmentBytes} B"
                    Text(
                        text = stringResource(
                            R.string.storage_chat_records_attachments_hint,
                            sizeText,
                            totalAttachmentCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (monthEntriesState) {
                UiState.Idle,
                UiState.Loading,
                -> Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is UiState.Error -> Text(
                    text = monthEntriesState.error.message ?: "Error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                is UiState.Success -> {
                    if (monthEntriesState.data.isNotEmpty()) {
                        Text(
                            text = if (selectedMonthCount > 0) {
                                stringResource(
                                    R.string.storage_chat_records_months_selected_summary,
                                    selectedMonthCount,
                                    selectedConversationCount,
                                )
                            } else {
                                stringResource(
                                    R.string.storage_chat_records_months_total_summary,
                                    monthEntriesState.data.size,
                                    totalConversationCount,
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    enabled = monthEntriesState is UiState.Success && monthEntriesState.data.isNotEmpty(),
                    onClick = onSelectAll,
                ) {
                    Icon(Icons.Rounded.SelectAll, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.storage_action_select_all))
                }

                FilledTonalButton(
                    enabled = selectedMonthCount > 0,
                    onClick = onClearSelection,
                ) {
                    Text(stringResource(R.string.storage_action_clear_selection))
                }

                FilledTonalButton(
                    enabled = selectedMonthCount > 0,
                    onClick = onRequestClear,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.storage_action_clear_records_only))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatRecordsMonthRow(
    entry: ChatRecordsMonthEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "storage_chat_records_month_row_scale",
    )

    val yearMonth = remember(entry.yearMonth) { runCatching { YearMonth.parse(entry.yearMonth) }.getOrNull() }
    val titleText = if (yearMonth == null) {
        entry.yearMonth
    } else {
        stringResource(R.string.storage_chat_records_month_label, yearMonth.year, yearMonth.monthValue)
    }

    Card(
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                )
            }

            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.storage_chat_records_count, entry.conversationCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                } else if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(AppShapes.ButtonPill)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)),
                    )
                }
            }
        }
    }
}
