package me.rerere.rikkahub.ui.pages.storage

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.InsertDriveFile
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.AssistantFileEntry
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.UiState
import java.io.File
import kotlin.uuid.Uuid

@Composable
fun StorageFilesScaffoldContent(
    innerPadding: PaddingValues,
    assistants: List<Assistant>,
    selectedAssistantId: Uuid?,
    onSelectAssistant: (Uuid?) -> Unit,
    assistantFilesState: UiState<List<AssistantFileEntry>>,
    onClearAssistantFiles: (Uuid) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()

    val files = (assistantFilesState as? UiState.Success<List<AssistantFileEntry>>)
        ?.data
        .orEmpty()

    val totalBytes = remember(files) { files.sumOf { it.bytes } }
    val totalBytesText = runCatching { Formatter.formatShortFileSize(context, totalBytes) }.getOrNull()
        ?: "${totalBytes} B"

    var showConfirmClear by rememberSaveable(selectedAssistantId) { mutableStateOf(false) }

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

        item(key = "files_card") {
            AssistantFilesCard(
                selectedAssistantId = selectedAssistantId,
                filesState = assistantFilesState,
                totalBytesText = totalBytesText,
                totalCount = files.size,
                onRequestClear = {
                    if (selectedAssistantId == null) return@AssistantFilesCard
                    haptics.perform(HapticPattern.Pop)
                    showConfirmClear = true
                },
            )
        }

        if (selectedAssistantId != null && assistantFilesState is UiState.Success) {
            items(
                items = files,
                key = { it.absolutePath },
            ) { entry ->
                AssistantFileRow(entry = entry)
            }
        }
    }

    if (showConfirmClear && selectedAssistantId != null) {
        val assistantName = assistants
            .firstOrNull { it.id == selectedAssistantId }
            ?.name
            ?.trim()
            ?.ifBlank { null }

        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text(stringResource(R.string.storage_confirm_clear_files_title)) },
            text = {
                Text(
                    text = buildString {
                        assistantName?.let {
                            append(it)
                            append(" · ")
                        }
                        append(stringResource(R.string.storage_confirm_clear_files_desc))
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.perform(HapticPattern.Thud)
                        showConfirmClear = false
                        onClearAssistantFiles(selectedAssistantId)
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
private fun AssistantFilesCard(
    selectedAssistantId: Uuid?,
    filesState: UiState<List<AssistantFileEntry>>,
    totalBytesText: String,
    totalCount: Int,
    onRequestClear: () -> Unit,
) {
    Card(
        shape = AppShapes.CardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_files_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.storage_files_preview_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selectedAssistantId == null) {
                Text(
                    text = stringResource(R.string.storage_select_assistant_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            when (filesState) {
                UiState.Idle,
                UiState.Loading,
                -> Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is UiState.Error -> Text(
                    text = filesState.error.message ?: "Error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                is UiState.Success -> {
                    if (filesState.data.isEmpty()) {
                        Text(
                            text = stringResource(R.string.storage_files_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        return@Column
                    }

                    Text(
                        text = stringResource(R.string.storage_files_total_summary, totalBytesText, totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FilledTonalButton(
                        onClick = onRequestClear,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.storage_action_clear_files))
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantFileRow(
    entry: AssistantFileEntry,
) {
    val context = LocalContext.current
    val sizeText = remember(entry.bytes) {
        runCatching { Formatter.formatShortFileSize(context, entry.bytes) }.getOrNull()
            ?: "${entry.bytes} B"
    }

    Card(
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.fileName.trim().ifBlank { File(entry.absolutePath).name },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
