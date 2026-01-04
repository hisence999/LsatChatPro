package me.rerere.rikkahub.ui.pages.setting

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionNotification
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.pages.setting.components.PresetThemeButtonGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingDisplayPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var amoledDarkMode by rememberAmoledDarkMode()

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(
            settings.copy(
                displaySetting = setting
            )
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()

    val permissionState = rememberPermissionState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setOf(
            PermissionNotification
        ) else emptySet(),
    )
    PermissionManager(permissionState = permissionState)

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_display_page_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Theme Settings
            item {
                var useExpressiveFont by me.rerere.rikkahub.ui.hooks.rememberExpressiveFont()
                
                SettingsGroup(
                    title = stringResource(R.string.setting_page_theme_setting)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_dynamic_color),
                        subtitle = stringResource(R.string.setting_page_dynamic_color_desc),
                        trailing = {
                            HapticSwitch(
                                checked = settings.dynamicColor,
                                onCheckedChange = {
                                    vm.updateSettings(settings.copy(dynamicColor = it))
                                },
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_font_style_title),
                        subtitle = if (useExpressiveFont) {
                            stringResource(R.string.setting_display_page_font_style_expressive)
                        } else {
                            stringResource(R.string.setting_display_page_font_style_normal)
                        },
                        trailing = {
                            HapticSwitch(
                                checked = useExpressiveFont,
                                onCheckedChange = { useExpressiveFont = it }
                            )
                        }
                    )
                }
            }

            if (!settings.dynamicColor) {
                item {
                    PresetThemeButtonGroup(
                        themeId = settings.themeId,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        onChangeTheme = {
                            vm.updateSettings(settings.copy(themeId = it))
                        }
                    )
                }
            }


            // Basic Settings
            item {
                var createNewConversationOnStart by rememberSharedPreferenceBoolean(
                    "create_new_conversation_on_start",
                    true
                )
                SettingsGroup(
                    title = stringResource(R.string.setting_page_basic_settings)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_create_new_conversation_on_start_title),
                        subtitle = stringResource(R.string.setting_display_page_create_new_conversation_on_start_desc),
                        trailing = {
                            HapticSwitch(
                                checked = createNewConversationOnStart,
                                onCheckedChange = {
                                    createNewConversationOnStart = it
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_notification_message_generated),
                        subtitle = stringResource(R.string.setting_display_page_notification_message_generated_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.enableNotificationOnMessageGeneration,
                                onCheckedChange = {
                                    if (it && !permissionState.allPermissionsGranted) {
                                        permissionState.requestPermissions()
                                    }
                                    updateDisplaySetting(displaySetting.copy(enableNotificationOnMessageGeneration = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_check_updates_title),
                        subtitle = stringResource(R.string.setting_display_page_check_updates_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.checkForUpdates,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(checkForUpdates = it))
                                }
                            )
                        }
                    )
                }
            }

//            item {
//                ListItem(
//                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
//                    headlineContent = {
//                        Text(stringResource(R.string.setting_display_page_developer_mode))
//                    },
//                    supportingContent = {
//                        Text(stringResource(R.string.setting_display_page_developer_mode_desc))
//                    },
//                    trailingContent = {
//                        HapticSwitch(
//                            checked = settings.developerMode,
//                            onCheckedChange = {
//                                vm.updateSettings(settings.copy(developerMode = it))
//                            }
//                        )
//                    },
//                )
//            }

            // Chat Display Settings
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_chat_settings)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_show_user_avatar_title),
                        subtitle = stringResource(R.string.setting_display_page_show_user_avatar_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.showUserAvatar,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showUserAvatar = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = "Show Assistant Avatar",
                        subtitle = "Show the assistant's avatar before messages",
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.showModelIcon,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showModelIcon = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_show_token_usage_title),
                        subtitle = stringResource(R.string.setting_display_page_show_token_usage_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.showTokenUsage,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showTokenUsage = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_auto_collapse_thinking_title),
                        subtitle = stringResource(R.string.setting_display_page_auto_collapse_thinking_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.autoCloseThinking,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(autoCloseThinking = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = "Show Context Stacks",
                        subtitle = "Show context sources (modes, memories, lorebooks) in message toolbar",
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.showContextStacks,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showContextStacks = it))
                                }
                            )
                        }
                    )
                }
            }
            
            // Message Jumper Settings
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_display_page_section_message_jumper)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_show_message_jumper_title),
                        subtitle = stringResource(R.string.setting_display_page_show_message_jumper_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.showMessageJumper,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(showMessageJumper = it))
                                }
                            )
                        }
                    )
                    if (displaySetting.showMessageJumper) {
                        SettingGroupItem(
                            title = stringResource(R.string.setting_display_page_message_jumper_position_title),
                            subtitle = stringResource(R.string.setting_display_page_message_jumper_position_desc),
                            trailing = {
                                HapticSwitch(
                                    checked = displaySetting.messageJumperOnLeft,
                                    onCheckedChange = {
                                        updateDisplaySetting(displaySetting.copy(messageJumperOnLeft = it))
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // Haptics Settings
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_display_page_section_haptics)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_title),
                        subtitle = stringResource(R.string.setting_display_page_enable_message_generation_haptic_effect_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.enableMessageGenerationHapticEffect,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableMessageGenerationHapticEffect = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_ui_haptics_title),
                        subtitle = stringResource(R.string.setting_display_page_ui_haptics_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.enableUIHaptics,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(enableUIHaptics = it))
                                }
                            )
                        }
                    )
                }
            }

            // Media Settings
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_display_page_section_media)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_skip_crop_image_title),
                        subtitle = stringResource(R.string.setting_display_page_skip_crop_image_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.skipCropImage,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(skipCropImage = it))
                                }
                            )
                        }
                    )
                }
            }

            // Code Blocks Settings
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_display_page_section_code_blocks)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_code_block_auto_wrap_title),
                        subtitle = stringResource(R.string.setting_display_page_code_block_auto_wrap_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.codeBlockAutoWrap,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(codeBlockAutoWrap = it))
                                }
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_code_block_auto_collapse_title),
                        subtitle = stringResource(R.string.setting_display_page_code_block_auto_collapse_desc),
                        trailing = {
                            HapticSwitch(
                                checked = displaySetting.codeBlockAutoCollapse,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(codeBlockAutoCollapse = it))
                                }
                            )
                        }
                    )
                }
            }

            // Advanced Settings (RP Optimizations & Font Size)
            item {
                val navController = me.rerere.rikkahub.ui.context.LocalNavController.current
                SettingsGroup(
                    title = stringResource(R.string.setting_display_page_section_advanced)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_display_page_rp_optimizations_title),
                        subtitle = stringResource(R.string.setting_display_page_rp_optimizations_desc),
                        onClick = { navController.navigate(me.rerere.rikkahub.Screen.SettingRpOptimizations) }
                    )
                }
            }

            // Font Size Slider
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_display_page_font_size_title)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Slider(
                            value = displaySetting.fontSizeRatio,
                            onValueChange = {
                                updateDisplaySetting(displaySetting.copy(fontSizeRatio = it))
                            },
                            valueRange = 0.5f..2f,
                            steps = 11,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(displaySetting.fontSizeRatio * 100).toInt()}%",
                        )
                    }
                    MarkdownBlock(
                        content = stringResource(R.string.setting_display_page_font_size_preview),
                        modifier = Modifier.padding(8.dp),
                        style = LocalTextStyle.current.copy(
                            fontSize = LocalTextStyle.current.fontSize * displaySetting.fontSizeRatio,
                            lineHeight = LocalTextStyle.current.lineHeight * displaySetting.fontSizeRatio,
                        )
                    )
                }
            }
        }
    }
}
