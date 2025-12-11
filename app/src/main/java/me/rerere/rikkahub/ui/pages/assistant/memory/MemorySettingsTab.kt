package me.rerere.rikkahub.ui.pages.assistant.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant

/**
 * Memory Settings Tab - Unified configuration for the Knowledge Graph memory system.
 * 
 * Features:
 * - Memory enable/disable toggle
 * - RAG retrieval settings
 * - Consolidation settings
 * - Data management (export/import/clear)
 */
@Composable
fun MemorySettingsTab(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    viewModel: KnowledgeMapViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var ragLimit by remember(assistant.ragLimit) { mutableIntStateOf(assistant.ragLimit) }
    var ragThreshold by remember(assistant.ragSimilarityThreshold) { 
        mutableFloatStateOf(assistant.ragSimilarityThreshold) 
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Memory System Toggle
        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Psychology,
                    title = "Memory System",
                    subtitle = "Enable to remember things from conversations",
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    Switch(
                        checked = assistant.enableMemory,
                        onCheckedChange = {
                            onUpdateAssistant(assistant.copy(enableMemory = it))
                        }
                    )
                }
            }
        }
        
        // RAG Settings Section
        if (assistant.enableMemory) {
            item {
                Text(
                    text = "Retrieval Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    Column {
                        // RAG Limit
                        SettingsRow(
                            icon = Icons.Outlined.Memory,
                            title = "Memory Limit",
                            subtitle = "Max memories per message: $ragLimit",
                            iconTint = MaterialTheme.colorScheme.primary
                        ) {}
                        
                        Slider(
                            value = ragLimit.toFloat(),
                            onValueChange = { ragLimit = it.toInt() },
                            onValueChangeFinished = {
                                onUpdateAssistant(assistant.copy(ragLimit = ragLimit))
                            },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        // Similarity Threshold
                        SettingsRow(
                            icon = Icons.Outlined.Verified,
                            title = "Similarity Threshold",
                            subtitle = "Minimum relevance: ${(ragThreshold * 100).toInt()}%",
                            iconTint = MaterialTheme.colorScheme.tertiary
                        ) {}
                        
                        Slider(
                            value = ragThreshold,
                            onValueChange = { ragThreshold = it },
                            onValueChangeFinished = {
                                onUpdateAssistant(assistant.copy(ragSimilarityThreshold = ragThreshold))
                            },
                            valueRange = 0.3f..0.95f,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        // Include Core Memories
                        SettingsRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = "Include Core Entities",
                            subtitle = "Include important facts about people, places, etc.",
                            iconTint = MaterialTheme.colorScheme.tertiary
                        ) {
                            Switch(
                                checked = assistant.ragIncludeCore,
                                onCheckedChange = {
                                    onUpdateAssistant(assistant.copy(ragIncludeCore = it))
                                }
                            )
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        // Include Episodic Memories
                        SettingsRow(
                            icon = Icons.Outlined.Schedule,
                            title = "Include Episodes",
                            subtitle = "Include conversation summaries and events",
                            iconTint = MaterialTheme.colorScheme.secondary
                        ) {
                            Switch(
                                checked = assistant.ragIncludeEpisodes,
                                onCheckedChange = {
                                    onUpdateAssistant(assistant.copy(ragIncludeEpisodes = it))
                                }
                            )
                        }
                    }
                }
            }
            
            // Consolidation Section
            item {
                Text(
                    text = "Consolidation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    Column {
                        SettingsRow(
                            icon = Icons.Outlined.SyncAlt,
                            title = "Memory Consolidation",
                            subtitle = "Delay: ${assistant.consolidationDelayMinutes} min",
                            iconTint = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Consolidation Delay Slider
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Slider(
                                value = assistant.consolidationDelayMinutes.toFloat(),
                                onValueChange = { 
                                    onUpdateAssistant(assistant.copy(consolidationDelayMinutes = it.toInt()))
                                },
                                valueRange = 15f..240f,
                                steps = 14 // (240-15)/15 = 15 steps approx
                            )
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        SettingsRow(
                            icon = Icons.Outlined.Speed,
                            title = "Learning Mode",
                            subtitle = if(assistant.learningMode) "Aggressive processing (Higher token usage)" else "Standard processing",
                            iconTint = MaterialTheme.colorScheme.tertiary
                        ) {
                            Switch(
                                checked = assistant.learningMode,
                                onCheckedChange = {
                                    onUpdateAssistant(assistant.copy(learningMode = it))
                                }
                            )
                        }
                    }
                }
            }
            
            // Statistics
            item {
                Text(
                    text = "Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            item {
                SettingsCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatRow("Total Entities", uiState.nodes.size.toString())
                        StatRow("Relationships", uiState.edges.size.toString())
                        StatRow("Core Memories", uiState.nodes.count { 
                            it.tier == me.rerere.rikkahub.data.memory.entity.MemoryTier.CORE 
                        }.toString())
                        StatRow("Patterns Discovered", uiState.nodes.count { 
                            it.nodeType == me.rerere.rikkahub.data.memory.entity.NodeType.CONCEPT 
                        }.toString())
                    }
                }
            }
            
        }
    }
}

@Composable
private fun SettingsCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        action()
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
