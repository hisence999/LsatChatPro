package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.service.WelcomePhrasesService
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Advanced tab - Notifications and custom request settings.
 * Designed with cohesive SettingsGroup pattern.
 */
@Composable
fun AssistantAdvancedSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val welcomePhrasesService = koinInject<WelcomePhrasesService>()
    val scope = rememberCoroutineScope()
    var isRefreshingWelcomePhrases by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onUpdate(assistant.copy(enableSpontaneous = true))
    }

    LaunchedEffect(assistant.id, assistant.enableWelcomePhrases, assistant.welcomePhrases) {
        if (assistant.enableWelcomePhrases && assistant.welcomePhrases.isEmpty()) {
            welcomePhrasesService.refreshForAssistantIfNeeded(assistant.id)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // NOTIFICATIONS GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.assistant_page_group_spontaneous_messaging)) {
            // Enable toggle
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_enable_spontaneous_messages_title),
                subtitle = stringResource(R.string.assistant_page_enable_spontaneous_messages_subtitle),
                trailing = {
                    HapticSwitch(
                        checked = assistant.enableSpontaneous,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onUpdate(assistant.copy(enableSpontaneous = true))
                                }
                            } else {
                                onUpdate(assistant.copy(enableSpontaneous = false))
                            }
                        }
                    )
                }
            )
            
            // Settings (only when enabled)
            AnimatedVisibility(
                visible = assistant.enableSpontaneous,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Active Hours
                    SettingGroupItem(
                        title = stringResource(R.string.assistant_page_active_hours_title),
                        subtitle = stringResource(R.string.assistant_page_active_hours_subtitle),
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = assistant.notificationStartHour.toString(),
                                    onValueChange = { 
                                        val hour = it.toIntOrNull()?.coerceIn(0, 23) ?: 7
                                        onUpdate(assistant.copy(notificationStartHour = hour))
                                    },
                                    label = { Text(stringResource(R.string.start)) },
                                    modifier = Modifier.width(70.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                OutlinedTextField(
                                    value = assistant.notificationEndHour.toString(),
                                    onValueChange = { 
                                        val hour = it.toIntOrNull()?.coerceIn(0, 23) ?: 22
                                        onUpdate(assistant.copy(notificationEndHour = hour))
                                    },
                                    label = { Text(stringResource(R.string.end)) },
                                    modifier = Modifier.width(70.dp),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    )

                    // Frequency
                    SettingGroupItem(
                        title = stringResource(R.string.assistant_page_frequency_title),
                        subtitle = stringResource(R.string.assistant_page_frequency_subtitle),
                        trailing = {
                            OutlinedTextField(
                                value = assistant.notificationFrequencyHours.toString(),
                                onValueChange = { 
                                    val hours = it.toIntOrNull()?.coerceAtLeast(1) ?: 4
                                    onUpdate(assistant.copy(notificationFrequencyHours = hours))
                                },
                                modifier = Modifier.width(70.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                suffix = { Text(stringResource(R.string.unit_hours_abbrev)) }
                            )
                        }
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        // CUSTOM REQUEST GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = stringResource(R.string.assistant_page_group_custom_request)) {
            // Custom Headers - component has its own title
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CustomHeaders(
                        headers = assistant.customHeaders,
                        onUpdate = { onUpdate(assistant.copy(customHeaders = it)) }
                    )
                }
            }
            
            // Custom Bodies - component has its own title
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CustomBodies(
                        customBodies = assistant.customBodies,
                        onUpdate = { onUpdate(assistant.copy(customBodies = it)) }
                    )
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.assistant_page_group_other)) {
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_welcome_phrases_title),
                subtitle = stringResource(R.string.assistant_page_welcome_phrases_desc),
                trailing = {
                    HapticSwitch(
                        checked = assistant.enableWelcomePhrases,
                        onCheckedChange = { enabled ->
                            onUpdate(assistant.copy(enableWelcomePhrases = enabled))
                        }
                    )
                }
            )

            if (assistant.enableWelcomePhrases) {
                SettingGroupItem(
                    title = stringResource(R.string.assistant_page_refresh_welcome_phrases_title),
                    subtitle = stringResource(R.string.assistant_page_refresh_welcome_phrases_desc),
                    trailing = {
                        if (isRefreshingWelcomePhrases) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.a11y_refresh),
                            )
                        }
                    },
                    onClick = if (isRefreshingWelcomePhrases) {
                        null
                    } else {
                        {
                            isRefreshingWelcomePhrases = true
                            scope.launch {
                                try {
                                    welcomePhrasesService.forceRefreshForAssistant(assistant.id)
                                } finally {
                                    isRefreshingWelcomePhrases = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
