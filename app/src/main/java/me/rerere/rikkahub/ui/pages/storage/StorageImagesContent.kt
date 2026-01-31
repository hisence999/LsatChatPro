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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.AssistantAttachmentStats
import me.rerere.rikkahub.data.repository.AssistantImageEntry
import me.rerere.rikkahub.data.repository.StorageCategoryUsage
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.UiState
import kotlin.uuid.Uuid

@Composable
fun StorageImagesScaffoldContent(
    innerPadding: PaddingValues,
    usageState: UiState<StorageCategoryUsage>,
    assistants: List<Assistant>,
    selectedAssistantId: Uuid?,
    onSelectAssistant: (Uuid?) -> Unit,
    attachmentStatsState: UiState<AssistantAttachmentStats>,
    assistantImagesState: UiState<List<AssistantImageEntry>>,
    onClearAssistantImages: (Uuid) -> Unit,
    onDeleteAssistantImages: (Uuid, List<String>) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberPremiumHaptics()

    var selectedPaths by rememberSaveable(selectedAssistantId) { mutableStateOf(emptySet<String>()) }
    var showConfirmDelete by rememberSaveable(selectedAssistantId) { mutableStateOf(false) }
    var previewIndex by rememberSaveable(selectedAssistantId) { mutableStateOf<Int?>(null) }

    val images = (assistantImagesState as? UiState.Success<List<AssistantImageEntry>>)
        ?.data
        .orEmpty()

    LaunchedEffect(images) {
        if (selectedPaths.isEmpty()) return@LaunchedEffect
        val valid = images.asSequence().map { it.absolutePath }.toSet()
        selectedPaths = selectedPaths.intersect(valid)
    }

    val selectedCount = selectedPaths.size
    val selectedBytes = remember(images, selectedPaths) {
        if (selectedPaths.isEmpty()) 0L else images.asSequence()
            .filter { it.absolutePath in selectedPaths }
            .sumOf { it.bytes }
    }
    val selectedBytesText = runCatching { Formatter.formatShortFileSize(context, selectedBytes) }
        .getOrNull()
        ?: "${selectedBytes} B"

    val previewImages = remember(images, previewIndex) {
        val idx = previewIndex ?: 0
        val urls = images.map { it.url }
        if (idx <= 0 || urls.isEmpty()) urls else urls.drop(idx) + urls.take(idx)
    }
    if (previewIndex != null && images.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            onDismissRequest = { previewIndex = null },
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "usage", span = { GridItemSpan(maxLineSpan) }) {
            CategoryUsageCard(usageState = usageState)
        }

        item(key = "assistant_filter", span = { GridItemSpan(maxLineSpan) }) {
            AssistantFilterRow(
                assistants = assistants,
                selected = selectedAssistantId,
                onSelect = onSelectAssistant,
            )
        }

        item(key = "assistant_clear", span = { GridItemSpan(maxLineSpan) }) {
            AssistantAttachmentsCard(
                title = stringResource(R.string.storage_images_assistant_title),
                kind = AttachmentKind.Images,
                assistants = assistants,
                selectedAssistantId = selectedAssistantId,
                statsState = attachmentStatsState,
                onConfirmClear = onClearAssistantImages,
            )
        }

        item(key = "assistant_images_actions", span = { GridItemSpan(maxLineSpan) }) {
            AssistantImagesGalleryCard(
                selectedAssistantId = selectedAssistantId,
                imagesState = assistantImagesState,
                selectedCount = selectedCount,
                selectedBytesText = selectedBytesText,
                onSelectAll = {
                    if (assistantImagesState is UiState.Success && assistantImagesState.data.isNotEmpty()) {
                        haptics.perform(HapticPattern.Pop)
                        selectedPaths = assistantImagesState.data.asSequence()
                            .map { it.absolutePath }
                            .toSet()
                    }
                },
                onClearSelection = {
                    if (selectedPaths.isEmpty()) return@AssistantImagesGalleryCard
                    haptics.perform(HapticPattern.Pop)
                    selectedPaths = emptySet()
                },
                onRequestDelete = {
                    if (selectedPaths.isEmpty()) return@AssistantImagesGalleryCard
                    haptics.perform(HapticPattern.Pop)
                    showConfirmDelete = true
                },
            )
        }

        if (assistantImagesState is UiState.Success) {
            itemsIndexed(
                items = images,
                key = { _, entry -> entry.absolutePath },
            ) { index, entry ->
                val selectionMode = selectedCount > 0
                val isSelected = entry.absolutePath in selectedPaths
                AssistantImageThumb(
                    entry = entry,
                    selected = isSelected,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) {
                            haptics.perform(HapticPattern.Pop)
                            selectedPaths = if (isSelected) selectedPaths - entry.absolutePath else selectedPaths + entry.absolutePath
                        } else {
                            haptics.perform(HapticPattern.Pop)
                            previewIndex = index
                        }
                    },
                    onLongClick = {
                        haptics.perform(HapticPattern.Pop)
                        selectedPaths = if (isSelected) selectedPaths - entry.absolutePath else selectedPaths + entry.absolutePath
                    },
                )
            }
        }
    }

    if (showConfirmDelete && selectedAssistantId != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text(stringResource(R.string.storage_confirm_delete_selected_images_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.storage_confirm_delete_selected_images_desc,
                        selectedCount,
                        selectedBytesText,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.perform(HapticPattern.Thud)
                        showConfirmDelete = false
                        val targets = selectedPaths.toList()
                        selectedPaths = emptySet()
                        onDeleteAssistantImages(selectedAssistantId, targets)
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AssistantImagesGalleryCard(
    selectedAssistantId: Uuid?,
    imagesState: UiState<List<AssistantImageEntry>>,
    selectedCount: Int,
    selectedBytesText: String,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRequestDelete: () -> Unit,
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
                text = stringResource(R.string.storage_images_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.storage_images_preview_hint),
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

            when (imagesState) {
                UiState.Idle,
                UiState.Loading,
                -> Text(
                    text = stringResource(R.string.storage_manager_loading_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is UiState.Error -> Text(
                    text = imagesState.error.message ?: "Error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                is UiState.Success -> {
                    if (imagesState.data.isEmpty()) {
                        Text(
                            text = stringResource(R.string.storage_images_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        return@Column
                    }

                    if (selectedCount > 0) {
                        Text(
                            text = stringResource(R.string.storage_images_selected_summary, selectedCount, selectedBytesText),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(onClick = onSelectAll) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.storage_action_select_all))
                        }

                        FilledTonalButton(
                            enabled = selectedCount > 0,
                            onClick = onClearSelection,
                        ) {
                            Text(stringResource(R.string.storage_action_clear_selection))
                        }

                        FilledTonalButton(
                            enabled = selectedCount > 0,
                            onClick = onRequestDelete,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.storage_action_delete_selected))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantImageThumb(
    entry: AssistantImageEntry,
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
        label = "storage_image_thumb_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(AppShapes.CardMedium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        AsyncImage(
            model = entry.url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
            )
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        } else if (selectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .width(18.dp)
                    .aspectRatio(1f)
                    .clip(AppShapes.ButtonPill)
                    .background(Color.White.copy(alpha = 0.5f)),
            )
        }
    }
}
