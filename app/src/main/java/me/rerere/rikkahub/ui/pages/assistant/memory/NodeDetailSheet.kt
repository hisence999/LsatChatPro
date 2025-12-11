package me.rerere.rikkahub.ui.pages.assistant.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.memory.engine.ActivationEngine
import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.MemoryTier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom sheet showing details of a selected memory node.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NodeDetailSheet(
    node: MemoryNode,
    connectedNodes: List<MemoryNode>,
    edges: List<MemoryEdge>,
    onDismiss: () -> Unit,
    onPromoteToCore: () -> Unit,
    onDemoteFromCore: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToNode: (MemoryNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activation = ActivationEngine.calculateActivation(node)
    val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Type icon
                Text(
                    text = node.nodeType.icon,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = node.nodeType.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(node.nodeType.color)
                    )
                }
                
                // Tier badge
                TierBadge(tier = node.tier)
            }
            
            HorizontalDivider()
            
            // Content
            if (node.content.isNotBlank()) {
                Column {
                    Text(
                        text = "Content",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = node.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Activation & Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Activation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when {
                                activation > 2f -> Color(0xFF4CAF50)
                                activation > 0f -> Color(0xFFFFC107)
                                else -> Color(0xFFE53935)
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%.2f".format(activation),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Confidence",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { node.confidence },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = when {
                            node.confidence > 0.8f -> Color(0xFF4CAF50)
                            node.confidence > 0.5f -> Color(0xFFFFC107)
                            else -> Color(0xFFE53935)
                        }
                    )
                    Text(
                        text = "${(node.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Emotional context
            if (node.emotionalValence != 0f || node.dominantEmotion != null) {
                Column {
                    Text(
                        text = "Emotional Context",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Valence: ${formatValence(node.emotionalValence)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Arousal: ${formatArousal(node.emotionalArousal)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        node.dominantEmotion?.let { emotion ->
                            Text(
                                text = emotion.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Access stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Access Count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${node.accessCount} times",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column {
                    Text(
                        text = "Last Accessed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = dateFormat.format(Date(node.lastAccessedAt)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Connected nodes
            if (connectedNodes.isNotEmpty()) {
                Column {
                    Text(
                        text = "Connected To (${connectedNodes.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        connectedNodes.take(10).forEach { connected ->
                            val edge = edges.find { 
                                (it.sourceId == node.id && it.targetId == connected.id) ||
                                (it.targetId == node.id && it.sourceId == connected.id)
                            }
                            
                            AssistChip(
                                onClick = { onNavigateToNode(connected) },
                                label = {
                                    Text(
                                        text = "${connected.nodeType.icon} ${connected.label}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                        
                        if (connectedNodes.size > 10) {
                            AssistChip(
                                onClick = { },
                                label = { Text("+${connectedNodes.size - 10} more") }
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider()
            
            // Actions
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tier actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (node.tier) {
                        MemoryTier.CORE -> {
                            FilledTonalButton(
                                onClick = onDemoteFromCore,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.StarBorder, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Remove from Core")
                            }
                        }
                        MemoryTier.RECALL -> {
                            FilledTonalButton(
                                onClick = onPromoteToCore,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Star, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Promote to Core")
                            }
                            OutlinedButton(
                                onClick = onArchive,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Archive, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Archive")
                            }
                        }
                        MemoryTier.ARCHIVAL -> {
                            FilledTonalButton(
                                onClick = onPromoteToCore,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Star, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Restore")
                            }
                        }
                    }
                }
                
                // Delete
                Button(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Outlined.Delete, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Memory")
                }
            }
        }
    }
}

@Composable
private fun TierBadge(tier: MemoryTier) {
    val (color, text) = when (tier) {
        MemoryTier.CORE -> Color(0xFFFFD700) to "Core"
        MemoryTier.RECALL -> Color(0xFF4A90D9) to "Recall"
        MemoryTier.ARCHIVAL -> Color(0xFF9E9E9E) to "Archived"
    }
    
    androidx.compose.material3.SuggestionChip(
        onClick = { },
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.2f),
            labelColor = color
        )
    )
}

private fun formatValence(valence: Float): String = when {
    valence > 0.5f -> "Very Positive 😊"
    valence > 0.2f -> "Positive 🙂"
    valence > -0.2f -> "Neutral 😐"
    valence > -0.5f -> "Negative 🙁"
    else -> "Very Negative 😢"
}

private fun formatArousal(arousal: Float): String = when {
    arousal > 0.7f -> "High ⚡"
    arousal > 0.3f -> "Medium"
    else -> "Low 😌"
}
