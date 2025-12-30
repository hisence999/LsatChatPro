package me.rerere.rikkahub.ui.pages.assistant.groupchat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.GroupChatSeat
import me.rerere.rikkahub.data.model.GroupChatSeatOverrides
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun GroupChatTemplateDetailPage(
    id: String,
) {
    val vm: GroupChatTemplateDetailVM = koinViewModel(
        parameters = { parametersOf(id) }
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    val template by vm.template.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    val haptics = rememberPremiumHaptics()
    val defaultName = stringResource(R.string.group_chat_default_name)

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddMemberSheet by remember { mutableStateOf(false) }
    var expandedSeatId by remember(template?.id) { mutableStateOf<Uuid?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = template?.name?.ifBlank { defaultName } ?: defaultName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Thud)
                            showDeleteDialog = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.group_chat_template_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val currentTemplate = template
            if (currentTemplate == null) {
                Text(
                    text = "Template not found ($id)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                return@Column
            }

            SettingsGroup(title = stringResource(R.string.assistant_page_group_identity)) {
                SettingGroupItem(
                    title = stringResource(R.string.group_chat_template_name),
                    subtitle = stringResource(R.string.assistant_page_name_desc),
                    trailing = {
                        DebouncedTextField(
                            value = currentTemplate.name,
                            onValueChange = vm::updateName,
                            stateKey = currentTemplate.id,
                            modifier = Modifier.fillMaxWidth(0.5f),
                            singleLine = true,
                        )
                    }
                )
            }

            SettingsGroup(title = stringResource(R.string.group_chat_template_host_model)) {
                SettingGroupItem(
                    title = stringResource(R.string.group_chat_template_host_model),
                    subtitle = stringResource(R.string.group_chat_template_host_model_desc),
                    trailing = {
                        ModelSelector(
                            modelId = currentTemplate.hostModelId,
                            providers = settings.providers,
                            type = ModelType.CHAT,
                            onSelect = { model ->
                                vm.updateHostModel(model.id)
                            },
                        )
                    }
                )
            }

            SettingsGroup(title = stringResource(R.string.group_chat_template_members)) {
                currentTemplate.seats.forEach { seat ->
                    val assistant = settings.assistants.find { it.id == seat.assistantId }
                    val seatTitle = assistant?.name?.ifBlank { defaultName } ?: defaultName

                    val effectiveModelId = seat.overrides.chatModelId
                        ?: assistant?.chatModelId
                        ?: settings.chatModelId
                    val seatSubtitle = settings.findModelById(effectiveModelId)?.displayName
                        ?: stringResource(R.string.model_list_select_model)

                    val expanded = expandedSeatId == seat.id
                    SettingGroupItem(
                        title = seatTitle,
                        subtitle = seatSubtitle,
                        icon = {
                            UIAvatar(
                                name = seatTitle,
                                value = assistant?.avatar ?: me.rerere.rikkahub.data.model.Avatar.Dummy,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailing = {
                            HapticSwitch(
                                checked = seat.defaultEnabled,
                                onCheckedChange = { enabled ->
                                    vm.setSeatEnabled(seat.id, enabled)
                                }
                            )
                        },
                        onClick = {
                            expandedSeatId = if (expanded) null else seat.id
                        }
                    )
                    AnimatedVisibility(visible = expanded) {
                        SeatOverridesEditor(
                            settings = settings,
                            providers = settings.providers,
                            seat = seat,
                            assistant = assistant,
                            onUpdateOverrides = { transform ->
                                vm.updateSeatOverrides(seat.id, transform)
                            },
                            onRemove = {
                                vm.removeSeat(seat.id)
                                expandedSeatId = null
                            },
                        )
                    }
                }

                SettingGroupItem(
                    title = stringResource(R.string.group_chat_template_add_member),
                    icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
                    onClick = {
                        showAddMemberSheet = true
                    }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.group_chat_template_delete)) },
            text = {
                Text(
                    text = stringResource(R.string.group_chat_default_name),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.perform(HapticPattern.Error)
                        vm.deleteTemplate()
                        showDeleteDialog = false
                        navController.popBackStack()
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddMemberSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddMemberSheet = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.group_chat_template_add_member),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                settings.assistants.forEach { assistant ->
                    val assistantName = assistant.name.ifBlank { defaultName }
                    Surface(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            vm.addSeat(assistant.id)
                            showAddMemberSheet = false
                        },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = AppShapes.ListItem,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            UIAvatar(
                                name = assistantName,
                                value = assistant.avatar,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = assistantName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SeatOverridesEditor(
    settings: Settings,
    providers: List<ProviderSetting>,
    seat: GroupChatSeat,
    assistant: Assistant?,
    onUpdateOverrides: ((GroupChatSeatOverrides) -> GroupChatSeatOverrides) -> Unit,
    onRemove: () -> Unit,
) {
    val defaultThinkingBudget = assistant?.thinkingBudget ?: 0
    val effectiveThinkingBudget = seat.overrides.thinkingBudget ?: defaultThinkingBudget
    val effectiveModelId = seat.overrides.chatModelId ?: assistant?.chatModelId ?: settings.chatModelId

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = AppShapes.CardMedium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.assistant_page_chat_model),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ModelSelector(
                    modelId = effectiveModelId,
                    providers = providers,
                    type = ModelType.CHAT,
                    onSelect = { model ->
                        onUpdateOverrides { it.copy(chatModelId = model.id) }
                    },
                )
                IconButton(
                    enabled = seat.overrides.chatModelId != null,
                    onClick = { onUpdateOverrides { it.copy(chatModelId = null) } },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.a11y_clear),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.assistant_page_thinking_budget),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ReasoningButton(
                    reasoningTokens = effectiveThinkingBudget,
                    onUpdateReasoningTokens = { tokens ->
                        onUpdateOverrides { it.copy(thinkingBudget = tokens) }
                    }
                )
                IconButton(
                    enabled = seat.overrides.thinkingBudget != null,
                    onClick = { onUpdateOverrides { it.copy(thinkingBudget = null) } },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.a11y_clear),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.assistant_page_max_tokens),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = seat.overrides.maxTokens?.toString() ?: "",
                    onValueChange = { raw ->
                        val tokens = raw.toIntOrNull()?.takeIf { it > 0 }
                        onUpdateOverrides { it.copy(maxTokens = tokens) }
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.auto)) },
                )
            }

            TextButton(onClick = onRemove) {
                Text(
                    text = stringResource(R.string.group_chat_template_remove_member),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
