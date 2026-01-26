package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.isEmptyUIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.toLocalString

@Composable
fun ChatMessageUserAvatar(
    message: UIMessage,
    previousRole: MessageRole?,
    avatar: Avatar,
    nickname: String,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val effectiveDisplay = settings.getEffectiveDisplaySetting()
    if (message.role == MessageRole.USER && previousRole != MessageRole.USER && !message.parts.isEmptyUIMessage() && effectiveDisplay.showUserAvatar) {
        Row(
            modifier = modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier,
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = message.createdAt.toJavaLocalDateTime().toLocalTime().toString().substring(0, 5), // HH:mm format
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    maxLines = 1,
                )
                Text(
                    text = nickname.ifEmpty { stringResource(R.string.user_default_name) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    color = LocalContentColor.current.copy(alpha = 0.85f),
                )
            }
            UIAvatar(
                name = nickname,
                modifier = Modifier.size(36.dp),
                value = avatar,
                loading = false,
            )
        }
    }
}

@Composable
fun ChatMessageAssistantAvatar(
    message: UIMessage,
    previousRole: MessageRole?,
    loading: Boolean,
    model: Model?,
    assistant: Assistant?,
    forceUseAssistantAvatar: Boolean = false,
    onAvatarLongPress: ((Assistant) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val effectiveDisplay = settings.getEffectiveDisplaySetting(assistant)
    val showIcon = effectiveDisplay.showModelIcon
    val haptics = rememberPremiumHaptics(enabled = effectiveDisplay.enableUIHaptics)
    if (message.role == MessageRole.ASSISTANT && previousRole != message.role) {
        Row(
            modifier = modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val assistantIdentity = assistant?.takeIf { forceUseAssistantAvatar || it.useAssistantAvatar || model == null }
            val enableMention = assistant != null && onAvatarLongPress != null
            val avatarInteractionSource = remember { MutableInteractionSource() }
            val isAvatarPressed by avatarInteractionSource.collectIsPressedAsState()
            val avatarScale by animateFloatAsState(
                targetValue = if (enableMention && isAvatarPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                label = "assistant_message_avatar_scale",
            )

            val avatarModifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = avatarScale
                    scaleY = avatarScale
                }
                .combinedClickable(
                    interactionSource = avatarInteractionSource,
                    indication = null,
                    enabled = enableMention,
                    onClick = {},
                    onLongClick = {
                        val safeAssistant = assistant ?: return@combinedClickable
                        haptics.perform(HapticPattern.Pop)
                        onAvatarLongPress?.invoke(safeAssistant)
                    },
                )

            if (showIcon) {
                when {
                    assistantIdentity != null -> {
                        UIAvatar(
                            name = assistantIdentity.name,
                            modifier = avatarModifier,
                            value = assistantIdentity.avatar,
                            loading = loading,
                        )
                    }

                    model != null -> {
                        AutoAIIcon(
                            name = model.modelId,
                            modifier = avatarModifier,
                            loading = loading,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (effectiveDisplay.showModelName) {
                    Text(
                        text = message.createdAt.toJavaLocalDateTime().toLocalTime().toString()
                            .substring(0, 5), // HH:mm format
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                        maxLines = 1,
                    )

                    when {
                        assistantIdentity != null -> {
                            Text(
                                text = assistantIdentity.name.ifEmpty { stringResource(R.string.assistant_page_default_assistant) },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                        }

                        model != null -> {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.assistant_page_default_assistant),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
