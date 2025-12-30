package me.rerere.rikkahub.ui.pages.logs

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.AIRequestSource
import me.rerere.rikkahub.data.ai.displayNameZh
import me.rerere.rikkahub.data.db.entity.AIRequestLogEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import org.koin.androidx.compose.koinViewModel

@Composable
fun RequestLogsOverviewPage(vm: RequestLogsVM = koinViewModel()) {
    val navController = LocalNavController.current
    val haptics = rememberPremiumHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val logs by vm.logs.collectAsStateWithLifecycle(initialValue = emptyList())
    val sourceFilter by vm.sourceFilter.collectAsStateWithLifecycle()
    val sources by vm.availableSources.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.developer_page_tab_request_logs),
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            vm.clearAll()
                        }
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (sources.isNotEmpty()) {
                item(key = "filters") {
                    SourceFilterRow(
                        selected = sourceFilter,
                        sources = sources,
                        onSelect = { vm.setSourceFilter(it) },
                    )
                }
            }

            if (logs.isEmpty()) {
                item(key = "empty") {
                    RequestLogEmptyState(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.request_logs_empty),
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }
            } else {
                items(
                    items = logs,
                    key = { it.id }
                ) { log ->
                    RequestLogOverviewItem(
                        log = log,
                        onClick = {
                            navController.navigate(Screen.RequestLogDetail(log.id))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceFilterRow(
    selected: AIRequestSource?,
    sources: List<AIRequestSource>,
    onSelect: (AIRequestSource?) -> Unit,
) {
    val haptics = rememberPremiumHaptics()

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = {
                haptics.perform(HapticPattern.Pop)
                onSelect(null)
            },
            label = { Text(stringResource(R.string.request_logs_filter_all)) },
        )
        sources.forEach { source ->
            FilterChip(
                selected = selected == source,
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    onSelect(source)
                },
                label = { Text(source.displayNameZh()) },
            )
        }
    }
}

@Composable
private fun RequestLogOverviewItem(
    log: AIRequestLogEntity,
    onClick: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "log_item_scale",
    )

    val sourceLabel = remember(log.source) { resolveSourceLabel(log.source) }
    val time = remember(log.createdAt) { formatLogTime(log.createdAt, "HH:mm:ss") }
    val durationText = log.durationMs?.let { "${it}ms" }.orEmpty()

    val statusText = if (log.error != null) {
        stringResource(R.string.request_log_status_error)
    } else {
        stringResource(R.string.request_log_status_ok)
    }
    val statusColor = if (log.error != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
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
                    onClick()
                }
            )
            .animateContentSize(animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)),
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceTag(text = sourceLabel)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (durationText.isNotBlank()) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "${log.providerName} · ${log.modelDisplayName.ifBlank { log.modelId }}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = log.requestUrl.ifBlank { "-" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                )
                if (log.stream) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "stream",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
