package me.rerere.rikkahub.ui.pages.developer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.DeleteForever
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.AILogging
import me.rerere.rikkahub.data.ai.AIRequestSource
import me.rerere.rikkahub.data.ai.displayNameZh
import me.rerere.rikkahub.data.db.entity.AIRequestLogEntity
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DeveloperPage(vm: DeveloperVM = koinViewModel()) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.developer_page_title),
                        maxLines = 1,
                    )
                },
                actions = {
                    if (pager.currentPage == 0) {
                        IconButton(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                vm.clearRequestLogs()
                            }
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                NavigationBarItem(
                    selected = pager.currentPage == 0,
                    onClick = { scope.launch { pager.animateScrollToPage(0) } },
                    label = {
                        Text(text = stringResource(R.string.developer_page_tab_request_logs))
                    },
                    icon = {
                        Icon(Icons.Rounded.History, null)
                    }
                )
                NavigationBarItem(
                    selected = pager.currentPage == 1,
                    onClick = { scope.launch { pager.animateScrollToPage(1) } },
                    label = {
                        Text(text = stringResource(R.string.developer_page_tab_developer))
                    },
                    icon = {
                        Icon(Icons.Rounded.Description, null)
                    }
                )
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pager,
            contentPadding = innerPadding
        ) { page ->
            when (page) {
                0 -> {
                    RequestLogsPaging(vm = vm)
                }
                1 -> {
                    LoggingPaging(vm = vm)
                }
            }
        }
    }
}

@Composable
fun LoggingPaging(vm: DeveloperVM) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(logs) { log ->
            when (log) {
                is AILogging.Generation -> {
                    Card {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RequestLogsPaging(vm: DeveloperVM) {
    val logs by vm.requestLogs.collectAsStateWithLifecycle(initialValue = emptyList())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items = logs,
            key = { it.id }
        ) { log ->
            RequestLogItem(log = log)
        }
    }
}

@Composable
private fun RequestLogItem(log: AIRequestLogEntity) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "request_log_item_scale",
    )

    var expanded by remember(log.id) { mutableStateOf(false) }

    val source = runCatching { AIRequestSource.valueOf(log.source) }.getOrNull()
    val sourceLabel = source?.displayNameZh() ?: log.source

    val time = remember(log.createdAt) {
        Instant.ofEpochMilli(log.createdAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
    }

    Card(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    expanded = !expanded
                },
                onLongClick = {
                    haptics.perform(HapticPattern.Pop)
                    expanded = true
                }
            )
            .animateContentSize(animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)),
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row {
                Surface(
                    shape = AppShapes.Tag,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = sourceLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${log.providerName} · ${log.modelDisplayName.ifBlank { log.modelId }}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = time,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }

            if (log.error != null) {
                Text(
                    text = log.error,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = log.requestPreview,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )

            if (log.responsePreview.isNotBlank()) {
                Text(
                    text = log.responsePreview,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                SelectionContainer {
                    Text(
                        text = log.paramsJson,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                SelectionContainer {
                    Text(
                        text = log.requestMessagesJson,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
