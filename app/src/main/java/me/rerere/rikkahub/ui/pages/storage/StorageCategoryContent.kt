package me.rerere.rikkahub.ui.pages.storage

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.AssistantAttachmentStats
import me.rerere.rikkahub.data.repository.AssistantFileEntry
import me.rerere.rikkahub.data.repository.AssistantImageEntry
import me.rerere.rikkahub.data.repository.AssistantChatCleanupMode
import me.rerere.rikkahub.data.repository.OrphanScanResult
import me.rerere.rikkahub.data.repository.StorageCategoryKey
import me.rerere.rikkahub.data.repository.StorageCategoryUsage
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.UiState
import kotlin.uuid.Uuid

@Composable
fun StorageCategoryScaffoldContent(
    category: StorageCategoryKey,
    innerPadding: PaddingValues,
    usageState: UiState<StorageCategoryUsage>,
    assistants: List<Assistant>,
    selectedAssistantId: Uuid?,
    onSelectAssistant: (Uuid?) -> Unit,
    attachmentStatsState: UiState<AssistantAttachmentStats>,
    conversationCountState: UiState<Int>,
    assistantImagesState: UiState<List<AssistantImageEntry>>,
    assistantFilesState: UiState<List<AssistantFileEntry>>,
    onDeleteAssistantImages: (Uuid, List<String>) -> Unit,
    onClearAssistantFiles: (Uuid) -> Unit,
    onClearAssistantChats: (Uuid, AssistantChatCleanupMode) -> Unit,
    orphanScanState: UiState<OrphanScanResult>,
    onScanOrphans: () -> Unit,
    onClearAllOrphans: () -> Unit,
    onClearCache: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    if (category == StorageCategoryKey.IMAGES) {
        StorageImagesScaffoldContent(
            innerPadding = innerPadding,
            assistants = assistants,
            selectedAssistantId = selectedAssistantId,
            onSelectAssistant = onSelectAssistant,
            assistantImagesState = assistantImagesState,
            onDeleteAssistantImages = onDeleteAssistantImages,
        )
        return
    }

    if (category == StorageCategoryKey.FILES) {
        StorageFilesScaffoldContent(
            innerPadding = innerPadding,
            assistants = assistants,
            selectedAssistantId = selectedAssistantId,
            onSelectAssistant = onSelectAssistant,
            assistantFilesState = assistantFilesState,
            onClearAssistantFiles = onClearAssistantFiles,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.padding(innerPadding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "usage") {
            CategoryUsageCard(usageState = usageState)
        }

        when (category) {
            StorageCategoryKey.IMAGES -> {
                // handled in StorageImagesScaffoldContent
            }

            StorageCategoryKey.FILES -> {
                item(key = "assistant_filter") {
                    AssistantFilterRow(
                        assistants = assistants,
                        selected = selectedAssistantId,
                        onSelect = onSelectAssistant,
                    )
                }

                item(key = "assistant") {
                    AssistantAttachmentsCard(
                        title = stringResource(R.string.storage_files_assistant_title),
                        kind = AttachmentKind.Files,
                        assistants = assistants,
                        selectedAssistantId = selectedAssistantId,
                        statsState = attachmentStatsState,
                        onConfirmClear = onClearAssistantFiles,
                    )
                }
            }

            StorageCategoryKey.CHAT_RECORDS -> {
                item(key = "assistant_filter") {
                    AssistantFilterRow(
                        assistants = assistants,
                        selected = selectedAssistantId,
                        onSelect = onSelectAssistant,
                    )
                }

                item(key = "assistant") {
                    AssistantChatRecordsCard(
                        assistants = assistants,
                        selectedAssistantId = selectedAssistantId,
                        conversationCountState = conversationCountState,
                        attachmentStatsState = attachmentStatsState,
                        onConfirmClear = onClearAssistantChats,
                    )
                }
            }

            StorageCategoryKey.CACHE -> {
                item(key = "cache") {
                    StorageCacheCard(onClearCache = onClearCache)
                }
            }

            StorageCategoryKey.HISTORY_FILES -> {
                item(key = "history") {
                    HistoryFilesCard(
                        scanState = orphanScanState,
                        onScan = onScanOrphans,
                        onClearAll = onClearAllOrphans,
                    )
                }
            }

            StorageCategoryKey.LOGS -> {
                item(key = "logs") {
                    StorageLogsCard(onOpenLogs = onOpenLogs)
                }
            }
        }
    }
}

@Composable
internal fun CategoryUsageCard(
    usageState: UiState<StorageCategoryUsage>,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        shape = AppShapes.CardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_category_usage_title),
                style = MaterialTheme.typography.titleMedium,
            )

            when (usageState) {
                UiState.Idle,
                UiState.Loading,
                -> Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is UiState.Error -> Text(
                    text = usageState.error.message ?: "Error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                is UiState.Success -> {
                    val bytes = usageState.data.bytes
                    val count = usageState.data.fileCount
                    val sizeText = runCatching { Formatter.formatShortFileSize(context, bytes) }.getOrNull()
                        ?: "${bytes} B"

                    Text(
                        text = stringResource(R.string.storage_category_usage_value, sizeText, count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (usageState.data.category == StorageCategoryKey.LOGS) {
                        Text(
                            text = stringResource(R.string.storage_logs_included_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AssistantFilterRow(
    assistants: List<Assistant>,
    selected: Uuid?,
    onSelect: (Uuid?) -> Unit,
) {
    val haptics = rememberPremiumHaptics()

    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = {
                haptics.perform(HapticPattern.Pop)
                onSelect(null)
            },
            label = { Text(stringResource(R.string.storage_filter_all_assistants)) },
        )

        assistants.forEach { assistant ->
            val name = assistant.name.trim().ifBlank { stringResource(R.string.storage_assistant_unnamed) }
            FilterChip(
                selected = selected == assistant.id,
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    onSelect(assistant.id)
                },
                label = { Text(name) },
            )
        }
    }
}
