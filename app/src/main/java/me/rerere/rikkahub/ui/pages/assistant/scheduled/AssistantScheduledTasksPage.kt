package me.rerere.rikkahub.ui.pages.assistant.scheduled

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.entity.ScheduledTaskEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.uuid.Uuid
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun AssistantScheduledTasksPage(assistantId: String) {
    val navController = LocalNavController.current
    val haptics = rememberPremiumHaptics()

    val vm: AssistantScheduledTasksVM = koinViewModel(
        parameters = { parametersOf(assistantId) }
    )
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val assistant = runCatching { Uuid.parse(assistantId) }.getOrNull()?.let { id ->
        settings.getAssistantById(id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = assistant?.name?.ifBlank { stringResource(R.string.assistant_page_default_assistant) }
                            ?.let { "$it · ${stringResource(R.string.scheduled_tasks_title)}" }
                            ?: stringResource(R.string.scheduled_tasks_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    HapticIconButton(
                        onClick = {
                            navController.navigate(Screen.AssistantScheduledTaskEdit(assistantId = assistantId, taskId = null))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.add),
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            SettingsGroup(title = stringResource(R.string.scheduled_tasks_list_group)) {
                if (tasks.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.scheduled_tasks_empty),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    tasks.forEach { task ->
                        ScheduledTaskListItem(
                            task = task,
                            assistant = assistant,
                            modelName = resolveModelName(settings, assistant, task),
                            onToggleEnabled = { enabled ->
                                vm.setEnabled(task.id, enabled)
                            },
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                navController.navigate(
                                    Screen.AssistantScheduledTaskEdit(
                                        assistantId = assistantId,
                                        taskId = task.id,
                                    )
                                )
                            },
                            onDelete = {
                                vm.deleteTask(task.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledTaskListItem(
    task: ScheduledTaskEntity,
    assistant: Assistant?,
    modelName: String?,
    onToggleEnabled: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "task_item_scale"
    )

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.scheduled_tasks_delete_title)) },
            text = { Text(stringResource(R.string.scheduled_tasks_delete_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.perform(HapticPattern.Thud)
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.assistant_page_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.assistant_page_cancel))
                }
            }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.perform(HapticPattern.Thud)
                    showDeleteDialog = true
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name.ifBlank { stringResource(R.string.scheduled_tasks_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val repeatText = taskRepeatText(task)
                val nextText = taskNextRunText(task)
                val modelText = modelName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.scheduled_tasks_model_unknown)

                Text(
                    text = listOfNotNull(
                        nextText?.let { "${stringResource(R.string.scheduled_tasks_next_run)}: $it" },
                        repeatText?.let { "${stringResource(R.string.scheduled_tasks_repeat)}: $it" },
                        "${stringResource(R.string.scheduled_tasks_model)}: $modelText",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HapticSwitch(
                checked = task.enabled,
                onCheckedChange = onToggleEnabled,
            )
        }
    }
}

@Composable
private fun HapticIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "icon_button_scale"
    )

    IconButton(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            onClick()
        },
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        interactionSource = interactionSource
    ) {
        content()
    }
}

@Composable
private fun taskNextRunText(task: ScheduledTaskEntity): String? {
    val next = task.nextRunAt ?: return null
    val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(next), ZoneId.systemDefault())
    return zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault()))
}

@Composable
private fun taskRepeatText(task: ScheduledTaskEntity): String? {
    return when (task.repeatType) {
        me.rerere.rikkahub.service.scheduledtask.ScheduledTaskRepeatType.ONCE -> stringResource(R.string.scheduled_tasks_repeat_once)
        me.rerere.rikkahub.service.scheduledtask.ScheduledTaskRepeatType.DAILY -> stringResource(R.string.scheduled_tasks_repeat_daily)
        me.rerere.rikkahub.service.scheduledtask.ScheduledTaskRepeatType.WEEKLY -> stringResource(R.string.scheduled_tasks_repeat_weekly)
        me.rerere.rikkahub.service.scheduledtask.ScheduledTaskRepeatType.MONTHLY -> stringResource(R.string.scheduled_tasks_repeat_monthly)
        me.rerere.rikkahub.service.scheduledtask.ScheduledTaskRepeatType.INTERVAL -> stringResource(R.string.scheduled_tasks_repeat_interval)
        else -> null
    }
}

private fun resolveModelName(
    settings: me.rerere.rikkahub.data.datastore.Settings,
    assistant: Assistant?,
    task: ScheduledTaskEntity,
): String? {
    val overrideId = task.overrideModelId
        ?.takeIf { it.isNotBlank() }
        ?.let { id -> runCatching { Uuid.parse(id) }.getOrNull() }
    if (overrideId != null) {
        return settings.findModelById(overrideId)?.displayName
    }

    val backgroundId = assistant?.backgroundModelId ?: assistant?.chatModelId ?: settings.chatModelId
    return settings.findModelById(backgroundId)?.displayName
}
