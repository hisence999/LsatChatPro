package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.hooks.useThrottle
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateDownload
import me.rerere.rikkahub.utils.Version
import me.rerere.rikkahub.utils.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant

private object UpdateEntryButtonSession {
    var suppressErrorUntilRestart by mutableStateOf(false)
    var errorRetryRequested by mutableStateOf(false)
    var errorRetryStarted by mutableStateOf(false)
}

@OptIn(ExperimentalTime::class)
@Composable
fun UpdateEntryButton(
    vm: ChatVM,
    modifier: Modifier = Modifier,
) {
    val state by vm.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    var showDetail by remember { mutableStateOf(false) }
    val current = remember { Version(BuildConfig.VERSION_NAME) }
    val updateInfo = (state as? UiState.Success)?.data
    val latest = remember(updateInfo) { updateInfo?.let { Version(it.version) } }
    val hasUpdate = latest != null && latest > current

    LaunchedEffect(state) {
        when (state) {
            is UiState.Loading -> {
                if (UpdateEntryButtonSession.errorRetryRequested && !UpdateEntryButtonSession.errorRetryStarted) {
                    UpdateEntryButtonSession.errorRetryStarted = true
                }
            }

            is UiState.Success -> {
                UpdateEntryButtonSession.errorRetryRequested = false
                UpdateEntryButtonSession.errorRetryStarted = false
            }

            is UiState.Error -> {
                if (UpdateEntryButtonSession.errorRetryStarted) {
                    UpdateEntryButtonSession.suppressErrorUntilRestart = true
                    UpdateEntryButtonSession.errorRetryRequested = false
                    UpdateEntryButtonSession.errorRetryStarted = false
                }
            }

            else -> Unit
        }
    }

    val isRetryInProgress = UpdateEntryButtonSession.errorRetryRequested || UpdateEntryButtonSession.errorRetryStarted
    val showButton = (state is UiState.Error && !UpdateEntryButtonSession.suppressErrorUntilRestart && !isRetryInProgress) || hasUpdate
    if (!showButton) return

    val isError = state is UiState.Error
    val containerColor =
        if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val contentColor =
        if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer

    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 300f,
        ),
        label = "update_button_scale",
    )

    Surface(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            if (isError) {
                if (!UpdateEntryButtonSession.errorRetryRequested && !UpdateEntryButtonSession.errorRetryStarted) {
                    UpdateEntryButtonSession.errorRetryRequested = true
                    vm.retryUpdateCheck()
                }
            } else {
                showDetail = true
            }
        },
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
    ) {
        Box(
            modifier = Modifier
                .padding(10.dp)
                .size(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = if (isError) {
                    stringResource(R.string.update_card_failed_to_check_updates)
                } else {
                    stringResource(R.string.setting_page_update_available_title)
                },
            )
        }
    }

    if (showDetail && updateInfo != null) {
        val downloadStartedText = stringResource(R.string.update_card_download_started)
        val downloadHandler = useThrottle<UpdateDownload>(500) { item ->
            vm.updateChecker.downloadUpdate(context, item)
            showDetail = false
            toaster.show(downloadStartedText, type = ToastType.Info)
        }
        ModalBottomSheet(
            onDismissRequest = { showDetail = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = updateInfo.version,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = Instant.parse(updateInfo.publishedAt).toJavaInstant().toLocalDateTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                MarkdownBlock(
                    content = updateInfo.changelog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                updateInfo.downloads.fastForEach { downloadItem ->
                    OutlinedCard(
                        onClick = {
                            downloadHandler(downloadItem)
                        },
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = downloadItem.name,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = downloadItem.size,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
