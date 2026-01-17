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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
            // Max messages input
            val maxMessages = assistant.maxHistoryMessages ?: 0

            IntInputSettingCard(
                title = stringResource(R.string.context_max_messages),
                value = maxMessages,
                valueText = if (maxMessages == 0) {
                    stringResource(R.string.context_max_messages_unlimited)
                } else {
                    stringResource(R.string.context_max_messages_value, maxMessages)
                },
                description = stringResource(R.string.context_max_messages_desc),
                onCommitValue = { newValue ->
                    onUpdate(
                        assistant.copy(
                            maxHistoryMessages = if (newValue == 0) null else newValue.coerceAtLeast(0)
                        )
                    )
                }
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
        // MESSAGE SUMMARIZATION
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
                val maxMsgs = assistant.maxHistoryMessages
                val autoSummarizeDesc = if (maxMsgs != null) {
                    stringResource(R.string.context_refresh_auto_summarize_desc, maxMsgs)
                } else {
                    stringResource(R.string.context_refresh_auto_summarize_desc_disabled)
                }
                SettingGroupItem(
                    title = stringResource(R.string.context_refresh_auto_summarize_title),
                    subtitle = autoSummarizeDesc,
                    trailing = {
                        HapticSwitch(
                            checked = assistant.autoRegenerateSummary,
                            enabled = maxMsgs != null,
                            onCheckedChange = { enabled ->
                                onUpdate(assistant.copy(autoRegenerateSummary = enabled))
                            }
                        )
                    },
                    onClick = {
                        if (maxMsgs != null) {
                            onUpdate(assistant.copy(autoRegenerateSummary = !assistant.autoRegenerateSummary))
                        }
                    }
                )
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // SEARCH RESULTS
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.context_search_results_title)) {
            val maxSearchResults = assistant.maxSearchResultsRetained ?: 0
            var searchSliderValue by remember(maxSearchResults) { mutableFloatStateOf(maxSearchResults.toFloat()) }
            
            SliderSettingCard(
                title = if (searchSliderValue.roundToInt() == 0) {
                    stringResource(R.string.context_max_search_results_unlimited)
                } else {
                    stringResource(R.string.context_max_search_results, searchSliderValue.roundToInt())
                },
                value = searchSliderValue,
                valueText = "", // Title already shows the value
                description = stringResource(R.string.context_max_search_results_desc),
                onValueChange = { searchSliderValue = it },
                onValueChangeFinished = {
                    val newValue = searchSliderValue.roundToInt()
                    onUpdate(assistant.copy(
                        maxSearchResultsRetained = if (newValue == 0) null else newValue
                    ))
                },
                valueRange = 0f..50f,
                steps = 49
            )
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
            if (valueText.isNotEmpty()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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

@Composable
private fun IntInputSettingCard(
    title: String,
    value: Int,
    valueText: String,
    description: String,
    onCommitValue: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var localText by remember(value) { mutableStateOf(value.toString()) }

    fun commit() {
        val trimmed = localText.trim()
        if (trimmed.isEmpty()) {
            localText = value.toString()
            return
        }

        val parsed = trimmed.toLongOrNull()
        val safeValue = when {
            parsed == null -> Int.MAX_VALUE
            parsed > Int.MAX_VALUE.toLong() -> Int.MAX_VALUE
            else -> parsed.toInt()
        }.coerceAtLeast(0)

        if (safeValue != value) {
            onCommitValue(safeValue)
        }
        localText = safeValue.toString()
    }

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
            if (valueText.isNotEmpty()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedTextField(
                value = localText,
                onValueChange = { newValue ->
                    localText = newValue.filter { it in '0'..'9' }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            commit()
                        }
                    },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                singleLine = true,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
