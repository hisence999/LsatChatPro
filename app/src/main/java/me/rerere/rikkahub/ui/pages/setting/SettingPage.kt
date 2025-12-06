package me.rerere.rikkahub.ui.pages.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.isNotConfigured
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.utils.countChatFiles
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    if(settings.developerMode) {
                        IconButton(
                            onClick = {
                                navController.navigate(Screen.Developer)
                            }
                        ) {
                            Icon(Icons.Rounded.Build, "Developer")
                        }
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (settings.isNotConfigured()) {
                item {
                    ProviderConfigWarningCard(navController)
                }
            }

            // General Settings Section
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_general_settings)
                ) {
                    var colorMode by rememberColorMode()
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_color_mode),
                        icon = { Icon(Icons.Rounded.InvertColors, null, modifier = Modifier.size(20.dp)) },
                        trailing = {
                            Select(
                                options = ColorMode.entries,
                                selectedOption = colorMode,
                                onOptionSelected = {
                                    colorMode = it
                                    navController.navigate(Screen.Setting) {
                                        launchSingleTop = true
                                        popUpTo(Screen.Setting) { inclusive = true }
                                    }
                                },
                                optionToString = {
                                    when (it) {
                                        ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                        ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                        ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                                    }
                                },
                                modifier = Modifier.width(130.dp)
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_display_setting),
                        subtitle = stringResource(R.string.setting_page_display_setting_desc),
                        icon = { Icon(Icons.Rounded.DesktopWindows, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingDisplay) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_assistant),
                        subtitle = stringResource(R.string.setting_page_assistant_desc),
                        icon = { Icon(Icons.Rounded.Group, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.Assistant) }
                    )
                }
            }

            // Models & Services Section
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_model_and_services)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_default_model),
                        subtitle = stringResource(R.string.setting_page_default_model_desc),
                        icon = { Icon(Icons.Rounded.AccountTree, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingModels) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_providers),
                        subtitle = stringResource(R.string.setting_page_providers_desc),
                        icon = { Icon(Icons.Rounded.Cloud, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingProvider) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_search_service),
                        subtitle = stringResource(R.string.setting_page_search_service_desc),
                        icon = { Icon(Icons.Rounded.Public, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingSearch) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_tts_service),
                        subtitle = stringResource(R.string.setting_page_tts_service_desc),
                        icon = { Icon(Icons.Rounded.RecordVoiceOver, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingTTS) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_mcp),
                        subtitle = stringResource(R.string.setting_page_mcp_desc),
                        icon = { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingMcp) }
                    )
                }
            }

            // Data Section
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_data_settings)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_data_backup),
                        subtitle = stringResource(R.string.setting_page_data_backup_desc),
                        icon = { Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.Backup) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val context = LocalContext.current
                    val storageState by produceState(-1 to 0L) {
                        value = context.countChatFiles()
                    }
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_chat_storage),
                        subtitle = if (storageState.first == -1) {
                            stringResource(R.string.calculating)
                        } else {
                            stringResource(
                                R.string.setting_page_chat_storage_desc,
                                storageState.first,
                                storageState.second / 1024 / 1024.0
                            )
                        },
                        icon = { Icon(Icons.Rounded.Storage, null, modifier = Modifier.size(20.dp)) }
                    )
                }
            }

            // About Section
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_about)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_about),
                        subtitle = stringResource(R.string.setting_page_about_desc),
                        icon = { Icon(Icons.Rounded.Info, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingAbout) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingGroupItem(
    title: String,
    subtitle: String? = null,
    icon: @Composable () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "scale"
    )
    
    Surface(
        onClick = {
            if (onClick != null) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
        },
        enabled = onClick != null,
        color = Color.Transparent,
        interactionSource = interactionSource,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProviderConfigWarningCard(navController: NavHostController) {
    Card(
        modifier = Modifier.padding(8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.setting_page_config_api_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.setting_page_config_api_desc))
                },
                leadingContent = {
                    Icon(Icons.Rounded.Warning, null)
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )

            TextButton(
                onClick = {
                    navController.navigate(Screen.SettingProvider)
                }
            ) {
                Text(stringResource(R.string.setting_page_config))
            }
        }
    }
}

@Composable
fun SettingItem(
    navController: NavHostController,
    title: @Composable () -> Unit,
    description: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    link: Screen? = null,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = rememberPremiumHaptics()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "setting_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "setting_alpha"
    )
    
    Surface(
        onClick = {
            haptics.perform(HapticPattern.Tick)
            if (link != null) navController.navigate(link)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    ) {
        ListItem(
            headlineContent = {
                title()
            },
            supportingContent = {
                description()
            },
            leadingContent = {
                icon()
            }
        )
    }
}
