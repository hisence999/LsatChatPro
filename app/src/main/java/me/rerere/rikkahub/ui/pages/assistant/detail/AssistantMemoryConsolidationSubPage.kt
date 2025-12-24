package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Psychology
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.db.entity.ChatEpisodeEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.utils.toLocalString

@Composable
fun AssistantMemoryConsolidationSubPage(
    vm: AssistantDetailVM,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    allModels: List<Model>,
    onConsolidate: (Boolean) -> Unit
) {
    val episodes: List<ChatEpisodeEntity> by vm.episodes.collectAsStateWithLifecycle(initialValue = emptyList())
    val stats by vm.episodeStats.collectAsStateWithLifecycle()
    val snackbarMessage: String? by vm.snackbarMessage.collectAsStateWithLifecycle(initialValue = null)

    // Prepare model selection options
    val defaultModel = Model("default", stringResource(R.string.assistant_page_model_default))
    val modelOptions = listOf(defaultModel) + allModels
    val selectedModel = allModels.find { it.id == assistant.summarizerModelId } ?: defaultModel

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Settings Card
        item {
            Card(
                shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                colors = CardDefaults.cardColors(
                    containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.assistant_page_advanced_memory_settings_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }


                    // Enable Memory Consolidation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.assistant_page_rag_advanced_memory_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.assistant_page_rag_advanced_memory_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HapticSwitch(
                            checked = assistant.enableMemory,
                            onCheckedChange = { onUpdate(assistant.copy(enableMemory = it)) }
                        )
                    }

                    if (assistant.enableMemory) {
                        // Human-like Memory
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.assistant_page_rag_reflection_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.assistant_page_rag_reflection_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HapticSwitch(
                                checked = assistant.enableHumanMemory,
                                onCheckedChange = { onUpdate(assistant.copy(enableHumanMemory = it)) }
                            )
                        }

                        // Summarizer Model
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.assistant_page_consolidation_summarizer_model),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.assistant_page_consolidation_summarizer_model_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Select(
                                options = modelOptions,
                                selectedOption = selectedModel,
                                onOptionSelected = { model ->
                                    if (model.id.toString() == "default") {
                                        onUpdate(assistant.copy(summarizerModelId = null))
                                    } else {
                                        onUpdate(assistant.copy(summarizerModelId = model.id))
                                    }
                                },
                                optionToString = { it.displayName },
                                modifier = Modifier.width(250.dp)
                            )
                        }

    

                        // Consolidation Delay
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.assistant_page_consolidation_delay) + ": ${assistant.consolidationDelayMinutes} " + stringResource(R.string.assistant_page_unit_minutes),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.assistant_page_consolidation_delay_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.material3.Slider(
                                value = assistant.consolidationDelayMinutes.toFloat(),
                                onValueChange = { 
                                    onUpdate(assistant.copy(consolidationDelayMinutes = it.toInt())) 
                                },
                                valueRange = 0f..240f, // 0 to 4 hours
                                steps = 23 // 10 min steps approx
                            )
                        }

                        if (assistant.enableHumanMemory) {
                            // Human Memory Interval
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.assistant_page_consolidation_reflection_interval) + ": ${assistant.humanMemoryUpdateIntervalHours} " + stringResource(R.string.assistant_page_unit_hours),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.assistant_page_consolidation_reflection_interval_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                androidx.compose.material3.Slider(
                                    value = assistant.humanMemoryUpdateIntervalHours.toFloat(),
                                    onValueChange = { 
                                        onUpdate(assistant.copy(humanMemoryUpdateIntervalHours = it.toInt())) 
                                    },
                                    valueRange = 1f..72f, // 1 hour to 3 days
                                    steps = 70
                                )
                            }
                        }
                    }
                }
            }
        }

        if (assistant.enableMemory) {
            // Status Card
            item {
                Card(
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(
                                text = stringResource(R.string.assistant_page_memory_statistics_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
    

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                label = stringResource(R.string.assistant_page_memory_stats_core),
                                value = stats.coreMemoryCount.toString()
                            )
                            StatItem(
                                label = stringResource(R.string.assistant_page_memory_stats_episodic),
                                value = stats.totalEpisodes.toString()
                            )
                        }
                        
                        // Detailed Run Stats
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.assistant_page_activity_recent),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Track A Stats
                            Column {
                                Text(
                                    text = stringResource(R.string.assistant_page_consolidation_track_a),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (assistant.lastConsolidationTime > 0) {
                                    val time = java.time.Instant.ofEpochMilli(assistant.lastConsolidationTime)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDateTime()
                                        .toLocalString()
                                    Text(
                                        text = stringResource(R.string.assistant_page_memory_last_run, time),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = stringResource(R.string.assistant_page_activity_result, assistant.lastConsolidationResult),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(stringResource(R.string.assistant_page_memory_no_run), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            
                            // Track B Stats
                            if (assistant.enableHumanMemory) {
                                Spacer(Modifier.size(4.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.assistant_page_consolidation_track_b),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (assistant.lastHumanMemoryUpdateTime > 0) {
                                        val time = java.time.Instant.ofEpochMilli(assistant.lastHumanMemoryUpdateTime)
                                            .atZone(java.time.ZoneId.systemDefault())
                                            .toLocalDateTime()
                                            .toLocalString()
                                        Text(
                                            text = stringResource(R.string.assistant_page_memory_last_run, time),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = stringResource(R.string.assistant_page_activity_result, assistant.lastHumanMemoryUpdateResult),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(stringResource(R.string.assistant_page_memory_no_run), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { onConsolidate(true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Psychology, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.assistant_page_memory_consolidate_now))
                        }
                        
                        if (snackbarMessage != null && snackbarMessage!!.contains("consolidation")) {
                            Text(
                                text = snackbarMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Episodes List Section
            item {
                Text(
                    text = stringResource(R.string.assistant_page_episodes_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            
            item {
                if (episodes.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.assistant_page_episodes_count, episodes.count()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
