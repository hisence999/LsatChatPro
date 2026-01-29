package me.rerere.rikkahub.ui.pages.storage

import android.text.format.Formatter
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.AssistantAttachmentStats
import me.rerere.rikkahub.data.repository.AssistantChatCleanupMode
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.UiState
import kotlin.uuid.Uuid

enum class AttachmentKind {
    Images,
    Files,
}

@Composable
fun AssistantAttachmentsCard(
    title: String,
    kind: AttachmentKind,
    assistants: List<Assistant>,
    selectedAssistantId: Uuid?,
    statsState: UiState<AssistantAttachmentStats>,
    onConfirmClear: (Uuid) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "assistant_action_scale",
    )

    var showConfirm by rememberSaveable { mutableStateOf(false) }

    val assistantName = assistants
        .firstOrNull { it.id == selectedAssistantId }
        ?.name
        ?.trim()
        ?.ifBlank { null }

    val confirmTitleRes = when (kind) {
        AttachmentKind.Images -> R.string.storage_confirm_clear_images_title
        AttachmentKind.Files -> R.string.storage_confirm_clear_files_title
    }
    val confirmDescRes = when (kind) {
        AttachmentKind.Images -> R.string.storage_confirm_clear_images_desc
        AttachmentKind.Files -> R.string.storage_confirm_clear_files_desc
    }
    val actionLabelRes = when (kind) {
        AttachmentKind.Images -> R.string.storage_action_clear_images
        AttachmentKind.Files -> R.string.storage_action_clear_files
    }

    Card(
        shape = AppShapes.CardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )

            if (selectedAssistantId == null) {
                Text(
                    text = stringResource(R.string.storage_select_assistant_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                when (statsState) {
                    UiState.Idle,
                    UiState.Loading,
                    -> Text(
                        text = stringResource(R.string.storage_manager_loading_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    is UiState.Error -> Text(
                        text = statsState.error.message ?: "Error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )

                    is UiState.Success -> {
                        val stats = statsState.data
                        val bytes = when (kind) {
                            AttachmentKind.Images -> stats.imageBytes
                            AttachmentKind.Files -> stats.fileBytes
                        }
                        val count = when (kind) {
                            AttachmentKind.Images -> stats.imageCount
                            AttachmentKind.Files -> stats.fileCount
                        }
                        val sizeText = runCatching { Formatter.formatShortFileSize(context, bytes) }.getOrNull()
                            ?: "${bytes} B"
                        Text(
                            text = stringResource(R.string.storage_assistant_file_stats, sizeText, count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                FilledTonalButton(
                    enabled = selectedAssistantId != null,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showConfirm = true
                    },
                    interactionSource = interactionSource,
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(actionLabelRes))
                }
            }
        }
    }

    if (showConfirm && selectedAssistantId != null) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(confirmTitleRes)) },
            text = {
                Text(
                    text = buildString {
                        assistantName?.let {
                            append(it)
                            append(" · ")
                        }
                        append(stringResource(confirmDescRes))
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.perform(HapticPattern.Thud)
                        showConfirm = false
                        onConfirmClear(selectedAssistantId)
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun AssistantChatRecordsCard(
    assistants: List<Assistant>,
    selectedAssistantId: Uuid?,
    conversationCountState: UiState<Int>,
    attachmentStatsState: UiState<AssistantAttachmentStats>,
    onConfirmClear: (Uuid, AssistantChatCleanupMode) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = rememberPremiumHaptics()
    var dialogMode by rememberSaveable { mutableStateOf<AssistantChatCleanupMode?>(null) }

    val assistantName = assistants
        .firstOrNull { it.id == selectedAssistantId }
        ?.name
        ?.trim()
        ?.ifBlank { null }

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

            if (selectedAssistantId == null) {
                Text(
                    text = stringResource(R.string.storage_select_assistant_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

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
                    text = stringResource(R.string.storage_chat_records_attachments_hint, sizeText, totalAttachmentCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    enabled = selectedAssistantId != null,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        dialogMode = AssistantChatCleanupMode.RECORDS_ONLY
                    },
                ) { Text(stringResource(R.string.storage_action_clear_records_only)) }

                FilledTonalButton(
                    enabled = selectedAssistantId != null,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        dialogMode = AssistantChatCleanupMode.FILES_ONLY
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(stringResource(R.string.storage_action_clear_attachments_only)) }

                FilledTonalButton(
                    enabled = selectedAssistantId != null,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        dialogMode = AssistantChatCleanupMode.RECORDS_AND_FILES
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(stringResource(R.string.storage_action_clear_records_and_attachments)) }
            }
        }
    }

    val mode = dialogMode
    if (mode != null && selectedAssistantId != null) {
        val (titleRes, descRes) = when (mode) {
            AssistantChatCleanupMode.RECORDS_ONLY ->
                R.string.storage_confirm_clear_records_title to R.string.storage_confirm_clear_records_desc

            AssistantChatCleanupMode.FILES_ONLY ->
                R.string.storage_confirm_clear_attachments_title to R.string.storage_confirm_clear_attachments_desc

            AssistantChatCleanupMode.RECORDS_AND_FILES ->
                R.string.storage_confirm_clear_records_and_attachments_title to R.string.storage_confirm_clear_records_and_attachments_desc
        }

        AlertDialog(
            onDismissRequest = { dialogMode = null },
            title = { Text(stringResource(titleRes)) },
            text = {
                Text(
                    text = buildString {
                        assistantName?.let {
                            append(it)
                            append(" · ")
                        }
                        append(stringResource(descRes))
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.perform(HapticPattern.Thud)
                        dialogMode = null
                        onConfirmClear(selectedAssistantId, mode)
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { dialogMode = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

