package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.roundToInt

@Composable
fun AssistantContextManagementSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    onNavigateToLorebooks: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // LOREBOOKS
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.context_lorebooks_title)) {
            SettingGroupItem(
                title = stringResource(R.string.assistant_lorebooks_title),
                subtitle = stringResource(R.string.context_lorebooks_desc),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = onNavigateToLorebooks
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // MESSAGE HISTORY
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.context_message_history_title)) {
            // Max messages slider
            val maxMessages = assistant.maxHistoryMessages ?: 0
            var sliderValue by remember(maxMessages) { mutableFloatStateOf(maxMessages.toFloat()) }
            
            SliderSettingCard(
                title = stringResource(R.string.context_max_messages),
                value = sliderValue,
                valueText = if (sliderValue.roundToInt() == 0) {
                    stringResource(R.string.context_max_messages_unlimited)
                } else {
                    stringResource(R.string.context_max_messages_value, sliderValue.roundToInt())
                },
                description = stringResource(R.string.context_max_messages_desc),
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    val newValue = sliderValue.roundToInt()
                    onUpdate(assistant.copy(
                        maxHistoryMessages = if (newValue == 0) null else newValue
                    ))
                },
                valueRange = 0f..50f,
                steps = 49
            )
            
            // Summarization toggle
            SettingGroupItem(
                title = stringResource(R.string.context_enable_summarization),
                subtitle = stringResource(R.string.context_enable_summarization_desc),
                trailing = {
                    HapticSwitch(
                        checked = assistant.enableHistorySummarization,
                        onCheckedChange = { enabled ->
                            onUpdate(assistant.copy(enableHistorySummarization = enabled))
                        }
                    )
                },
                onClick = {
                    onUpdate(assistant.copy(enableHistorySummarization = !assistant.enableHistorySummarization))
                }
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // SEARCH RESULTS
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.context_search_results_title)) {
            val maxSearchResults = assistant.maxSearchResultsRetained ?: 0
            var searchSliderValue by remember(maxSearchResults) { mutableFloatStateOf(maxSearchResults.toFloat()) }
            
            SliderSettingCard(
                title = stringResource(R.string.context_max_search_results),
                value = searchSliderValue,
                valueText = if (searchSliderValue.roundToInt() == 0) {
                    stringResource(R.string.context_max_search_results_unlimited)
                } else {
                    stringResource(R.string.context_max_search_results_value, searchSliderValue.roundToInt())
                },
                description = stringResource(R.string.context_max_search_results_desc),
                onValueChange = { searchSliderValue = it },
                onValueChangeFinished = {
                    val newValue = searchSliderValue.roundToInt()
                    onUpdate(assistant.copy(
                        maxSearchResultsRetained = if (newValue == 0) null else newValue
                    ))
                },
                valueRange = 0f..10f,
                steps = 9
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // CONTEXT REFRESH
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.context_refresh_title)) {
            // Enable Context Refresh toggle
            SettingGroupItem(
                title = stringResource(R.string.context_refresh_enable),
                subtitle = stringResource(R.string.context_refresh_enable_desc),
                trailing = {
                    HapticSwitch(
                        checked = assistant.enableContextRefresh,
                        onCheckedChange = { enabled ->
                            onUpdate(assistant.copy(enableContextRefresh = enabled))
                        }
                    )
                },
                onClick = {
                    onUpdate(assistant.copy(enableContextRefresh = !assistant.enableContextRefresh))
                }
            )
            
            // Auto-regenerate toggle (visible when context refresh is enabled)
            AnimatedVisibility(
                visible = assistant.enableContextRefresh,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingGroupItem(
                        title = "Auto-regenerate summary",
                        subtitle = "Automatically update summary after threshold messages",
                        trailing = {
                            HapticSwitch(
                                checked = assistant.autoRegenerateSummary,
                                onCheckedChange = { enabled ->
                                    onUpdate(assistant.copy(autoRegenerateSummary = enabled))
                                }
                            )
                        },
                        onClick = {
                            onUpdate(assistant.copy(autoRegenerateSummary = !assistant.autoRegenerateSummary))
                        }
                    )
                    
                    // Threshold slider (visible when auto-regenerate is enabled)
                    AnimatedVisibility(
                        visible = assistant.autoRegenerateSummary,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val threshold = assistant.summaryRegenerateThreshold.toFloat()
                        var thresholdSlider by remember(threshold) { mutableFloatStateOf(threshold) }
                        
                        SliderSettingCard(
                            title = "Regeneration threshold",
                            value = thresholdSlider,
                            valueText = "${thresholdSlider.roundToInt()} messages",
                            description = "New messages since last summary before auto-regenerating",
                            onValueChange = { thresholdSlider = it },
                            onValueChangeFinished = {
                                onUpdate(assistant.copy(
                                    summaryRegenerateThreshold = thresholdSlider.roundToInt()
                                ))
                            },
                            valueRange = 5f..50f,
                            steps = 8 // 5, 10, 15, 20, 25, 30, 35, 40, 45, 50
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SliderSettingCard(
    title: String,
    value: Float,
    valueText: String,
    description: String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Surface(
        color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
