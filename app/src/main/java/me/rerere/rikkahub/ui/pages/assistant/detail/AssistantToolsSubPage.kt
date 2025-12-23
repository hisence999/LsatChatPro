package me.rerere.rikkahub.ui.pages.assistant.detail

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.ui.components.ai.McpPickerButton
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.search.SearchServiceOptions

/**
 * Tools & Search tab - Combined search, local tools, and MCP settings.
 * Designed with cohesive SettingsGroup pattern.
 */
@Composable
fun AssistantToolsSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM,
    mcpServerConfigs: List<McpServerConfig>
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // SEARCH GROUP
        // ═══════════════════════════════════════════════════════════════════
        SettingsGroup(title = "Search") {
            // Search Mode dropdown
            var searchExpanded by remember { mutableStateOf(false) }
            val currentSearchMode = assistant.searchMode
            val displayText = when (currentSearchMode) {
                is AssistantSearchMode.Off -> "Off"
                is AssistantSearchMode.BuiltIn -> "Off"
                is AssistantSearchMode.Provider -> {
                    settings.searchServices.getOrNull(currentSearchMode.index)?.let {
                        SearchServiceOptions.TYPES[it::class] ?: "Provider ${currentSearchMode.index + 1}"
                    } ?: "Provider ${currentSearchMode.index + 1}"
                }
            }
            
            SettingGroupItem(
                title = "Search Provider",
                subtitle = displayText,
                trailing = {
                    Box {
                        Surface(
                            onClick = { searchExpanded = !searchExpanded },
                            tonalElevation = 4.dp,
                            shape = AppShapes.ButtonPill
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(displayText, style = MaterialTheme.typography.labelMedium)
                                Icon(
                                    imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = searchExpanded,
                            onDismissRequest = { searchExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Off") },
                                onClick = {
                                    onUpdate(assistant.copy(searchMode = AssistantSearchMode.Off))
                                    searchExpanded = false
                                }
                            )
                            settings.searchServices.forEachIndexed { index, service ->
                                val name = SearchServiceOptions.TYPES[service::class] ?: "Provider ${index + 1}"
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onUpdate(assistant.copy(searchMode = AssistantSearchMode.Provider(index)))
                                        searchExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
            
            // Prefer Built-in Search
            SettingGroupItem(
                title = "Prefer Built-in Search",
                subtitle = "Use model's native search when available",
                trailing = {
                    Switch(
                        checked = assistant.preferBuiltInSearch,
                        onCheckedChange = { onUpdate(assistant.copy(preferBuiltInSearch = it)) }
                    )
                }
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // LOCAL TOOLS GROUP
        // ═══════════════════════════════════════════════════════════════════
        val deviceControlPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            val newLocalTools = assistant.localTools + LocalToolOption.DeviceControl
            onUpdate(assistant.copy(localTools = newLocalTools))
        }
        
        SettingsGroup(title = stringResource(R.string.assistant_page_tab_local_tools)) {
            // JavaScript Engine
            SettingGroupItem(
                title = stringResource(R.string.assistant_page_local_tools_javascript_engine_title),
                subtitle = stringResource(R.string.assistant_page_local_tools_javascript_engine_desc),
                trailing = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
                        onCheckedChange = { enabled ->
                            val newLocalTools = if (enabled) {
                                assistant.localTools + LocalToolOption.JavascriptEngine
                            } else {
                                assistant.localTools - LocalToolOption.JavascriptEngine
                            }
                            onUpdate(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            )
            
            // Device Control
            SettingGroupItem(
                title = "Device Control",
                subtitle = "Notifications, apps, alarms, reminders",
                trailing = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.DeviceControl),
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val permissions = mutableListOf<String>()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissions.add(Manifest.permission.CAMERA)
                                
                                if (permissions.isNotEmpty()) {
                                    deviceControlPermissionLauncher.launch(permissions.toTypedArray())
                                } else {
                                    val newLocalTools = assistant.localTools + LocalToolOption.DeviceControl
                                    onUpdate(assistant.copy(localTools = newLocalTools))
                                }
                            } else {
                                val newLocalTools = assistant.localTools - LocalToolOption.DeviceControl
                                onUpdate(assistant.copy(localTools = newLocalTools))
                            }
                        }
                    )
                }
            )
        }

        // ═══════════════════════════════════════════════════════════════════
        // MCP GROUP (only show if servers configured)
        // ═══════════════════════════════════════════════════════════════════
        if (mcpServerConfigs.isNotEmpty()) {
            SettingsGroup(title = stringResource(R.string.assistant_page_tab_mcp)) {
            SettingGroupItem(
                    title = "MCP Servers",
                    subtitle = "Enable external tool servers",
                    trailing = {
                        McpPickerButton(
                            assistant = assistant,
                            servers = mcpServerConfigs,
                            mcpManager = org.koin.compose.koinInject(),
                            onUpdateAssistant = onUpdate
                        )
                    }
                )
            }
        }
    }
}
