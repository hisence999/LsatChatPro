package me.rerere.rikkahub.ui.pages.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.AssistantAttachmentStats
import me.rerere.rikkahub.data.repository.ChatRecordsMonthEntry
import me.rerere.rikkahub.data.repository.LightConversationEntity
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.UiState
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
    onLoadChatRecordConversationsByYearMonth: suspend (Uuid?, String) -> List<LightConversationEntity>,
    onClearChatRecordSelection: (Uuid?, Set<String>, Set<String>) -> Unit,
) {
    val haptics = rememberPremiumHaptics()

    var showConfirmClear by rememberSaveable(selectedAssistantId) { mutableStateOf(false) }

    var selections by remember(selectedAssistantId) { mutableStateOf<Map<String, ChatRecordMonthSelection>>(emptyMap()) }

    val monthsState = monthEntriesState as? UiState.Success<List<ChatRecordsMonthEntry>>
    val months = monthsState?.data.orEmpty()

    LaunchedEffect(months) {
        val valid = months.asSequence().map { it.yearMonth }.toSet()
        selections = filterChatRecordSelectionsByValidMonths(selections, valid)
    }

    var showConversationSheet by remember(selectedAssistantId) { mutableStateOf(false) }
    var activeYearMonth by remember(selectedAssistantId) { mutableStateOf<String?>(null) }
    var conversationSheetState by remember(selectedAssistantId) { mutableStateOf<UiState<List<LightConversationEntity>>>(UiState.Idle) }

    val summary = remember(months, selections) { buildChatRecordSelectionSummary(monthEntries = months, selections = selections) }
    val selectedMonthCount = summary.selectedMonthCount
    val totalConversationCount = summary.totalConversationCount
    val selectedConversationCount = summary.selectedConversationCount

    LaunchedEffect(showConversationSheet, activeYearMonth, selectedAssistantId) {
        val yearMonth = activeYearMonth ?: return@LaunchedEffect
        if (!showConversationSheet) return@LaunchedEffect

        conversationSheetState = UiState.Loading
        conversationSheetState = runCatching {
            onLoadChatRecordConversationsByYearMonth(selectedAssistantId, yearMonth)
        }.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { UiState.Error(it) },
        )
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
                    selections = months.asSequence()
                        .associate { it.yearMonth to ChatRecordMonthSelection.All(it.conversationCount) }
                },
                onClearSelection = {
                    if (selections.isEmpty()) return@ChatRecordsActionCard
                    haptics.perform(HapticPattern.Pop)
                    selections = emptyMap()
                },
                onRequestClear = {
                    if (selections.isEmpty()) return@ChatRecordsActionCard
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
                    val selectedCount = selections[entry.yearMonth]?.selectedCount ?: 0
                    val selectionMode = selectedMonthCount > 0
                    ChatRecordsMonthRow(
                        entry = entry,
                        selectedCount = selectedCount,
                        selectionMode = selectionMode,
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            conversationSheetState = UiState.Idle
                            activeYearMonth = entry.yearMonth
                            showConversationSheet = true
                        },
                        onLongClick = {
                            haptics.perform(HapticPattern.Pop)
                            selections = when (selections[entry.yearMonth]) {
                                is ChatRecordMonthSelection.All -> selections - entry.yearMonth
                                else -> selections + (entry.yearMonth to ChatRecordMonthSelection.All(entry.conversationCount))
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
                        val targets = buildChatRecordClearTargets(selections)
                        selections = emptyMap()
                        onClearChatRecordSelection(
                            selectedAssistantId,
                            targets.yearMonths,
                            targets.conversationIds,
                        )
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

    val sheetYearMonth = activeYearMonth
    if (showConversationSheet && sheetYearMonth != null) {
        val yearMonth = sheetYearMonth
        val entry = months.firstOrNull { it.yearMonth == yearMonth }
        val totalForMonth = entry?.conversationCount
            ?: (conversationSheetState as? UiState.Success)?.data?.size
            ?: 0

        ChatRecordConversationsBottomSheet(
            yearMonth = yearMonth,
            totalCount = totalForMonth,
            conversationsState = conversationSheetState,
            initialSelection = selections[yearMonth],
            onDismissRequest = {
                showConversationSheet = false
                activeYearMonth = null
                conversationSheetState = UiState.Idle
            },
            onApplySelection = { selectedConversationIds ->
                selections = when {
                    selectedConversationIds.isEmpty() -> selections - yearMonth
                    totalForMonth > 0 && selectedConversationIds.size >= totalForMonth -> {
                        selections + (yearMonth to ChatRecordMonthSelection.All(totalForMonth))
                    }

                    else -> selections + (yearMonth to ChatRecordMonthSelection.Some(selectedConversationIds.toSet()))
                }
            },
        )
    }
}
