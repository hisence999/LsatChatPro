package me.rerere.rikkahub.ui.pages.storage

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.repository.StorageCategoryKey
import me.rerere.rikkahub.data.repository.StorageCategoryUsage
import me.rerere.rikkahub.data.repository.StorageOverview
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun StorageManagerPage(
    vm: StorageManagerVM = koinViewModel(),
) {
    val navController = LocalNavController.current
    val haptics = rememberPremiumHaptics()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val overviewState by vm.overview.collectAsStateWithLifecycle()

    var selectedCategory by rememberSaveable { mutableStateOf<StorageCategoryKey?>(null) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.storage_manager_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            vm.refresh()
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
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
            item(key = "chart") {
                StorageOverviewChartCard(
                    overviewState = overviewState,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { category ->
                        haptics.perform(HapticPattern.Pop)
                        selectedCategory = category
                    },
                )
            }

            val categories = (overviewState as? me.rerere.rikkahub.utils.UiState.Success<StorageOverview>)
                ?.data
                ?.categories
                .orEmpty()

            items(
                items = categories,
                key = { it.category.key },
            ) { usage ->
                StorageCategoryItem(
                    usage = usage,
                    selected = usage.category == selectedCategory,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        when (usage.category) {
                            StorageCategoryKey.LOGS -> navController.navigate(Screen.RequestLogs)
                            else -> navController.navigate(Screen.StorageCategory(usage.category.key))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StorageOverviewChartCard(
    overviewState: me.rerere.rikkahub.utils.UiState<StorageOverview>,
    selectedCategory: StorageCategoryKey?,
    onSelectCategory: (StorageCategoryKey?) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        shape = AppShapes.CardLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        when (overviewState) {
            me.rerere.rikkahub.utils.UiState.Idle,
            me.rerere.rikkahub.utils.UiState.Loading,
            -> {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.storage_manager_total),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.storage_manager_loading_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is me.rerere.rikkahub.utils.UiState.Error -> {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.storage_manager_total),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = overviewState.error.message ?: "Error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is me.rerere.rikkahub.utils.UiState.Success -> {
                val scheme = MaterialTheme.colorScheme
                val overview = overviewState.data
                val totalText = runCatching { Formatter.formatShortFileSize(context, overview.totalBytes) }
                    .getOrNull()
                    ?: "${overview.totalBytes} B"

                val slices = overview.categories
                    .asSequence()
                    .filterNot { it.category == StorageCategoryKey.LOGS }
                    .filter { it.bytes > 0 }
                    .map { usage ->
                        DonutSlice(
                            category = usage.category,
                            value = usage.bytes,
                            color = categoryColor(usage.category, scheme),
                        )
                    }
                    .toList()

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.storage_manager_total),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = totalText,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    StorageDonutChart(
                        slices = slices,
                        selected = selectedCategory,
                        onSelect = onSelectCategory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )

                    Text(
                        text = stringResource(R.string.storage_manager_chart_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class DonutSlice(
    val category: StorageCategoryKey,
    val value: Long,
    val color: Color,
)

@Composable
private fun StorageDonutChart(
    slices: List<DonutSlice>,
    selected: StorageCategoryKey?,
    onSelect: (StorageCategoryKey?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sparkleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val total = slices.sumOf { it.value }.coerceAtLeast(1L)
    val thicknessScale by animateFloatAsState(
        targetValue = if (selected == null) 1f else 1.05f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "donut_thickness",
    )

    Box(
        modifier = modifier
            .pointerInput(slices, selected) {
                detectTapGestures { tap: Offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = tap.x - center.x
                    val dy = tap.y - center.y
                    val distance = sqrt(dx * dx + dy * dy)
                    val outer = min(size.width, size.height) / 2f
                    val strokeWidth = outer * 0.22f * thicknessScale
                    val inner = outer - strokeWidth

                    if (distance < inner || distance > outer) return@detectTapGestures

                    // Convert angle: 0 at top, clockwise.
                    val raw = (atan2(dy, dx) * 180f / PI.toFloat() + 360f + 90f) % 360f

                    var start = 0f
                    slices.forEach { slice ->
                        val sweep = (slice.value.toFloat() / total.toFloat()) * 360f
                        val end = start + sweep
                        if (raw >= start && raw < end) {
                            onSelect(if (selected == slice.category) null else slice.category)
                            return@detectTapGestures
                        }
                        start = end
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outer = min(size.width, size.height) / 2f
            val strokeWidth = outer * 0.22f * thicknessScale
            val topLeft = Offset(
                x = (size.width - outer * 2f) / 2f,
                y = (size.height - outer * 2f) / 2f,
            )
            val arcSize = Size(outer * 2f, outer * 2f)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

            var startAngle = -90f
            val gapDegrees = 2.0f

            slices.forEach { slice ->
                val sweep = (slice.value.toFloat() / total.toFloat()) * 360f
                val isSelected = selected == slice.category
                val alpha = if (selected == null || isSelected) 1f else 0.25f
                val adjustedSweep = (sweep - gapDegrees).coerceAtLeast(0f)

                drawArc(
                    color = slice.color.copy(alpha = alpha),
                    startAngle = startAngle + gapDegrees / 2f,
                    sweepAngle = adjustedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )

                startAngle += sweep
            }

            // Tiny sparkle dot at the top, for "fidget" feel.
            val dotRadius = strokeWidth * 0.18f
            val dotCenter = Offset(
                x = size.width / 2f + cos((-90f) * PI.toFloat() / 180f) * (outer - strokeWidth / 2f),
                y = size.height / 2f + sin((-90f) * PI.toFloat() / 180f) * (outer - strokeWidth / 2f),
            )
            drawCircle(
                color = sparkleColor,
                radius = dotRadius,
                center = dotCenter,
            )
        }
    }
}

@Composable
private fun StorageCategoryItem(
    usage: StorageCategoryUsage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "storage_item_scale",
    )

    val bytesText = runCatching { Formatter.formatShortFileSize(context, usage.bytes) }
        .getOrNull()
        ?: "${usage.bytes} B"

    val titleRes = categoryTitleRes(usage.category)
    val icon = categoryIcon(usage.category)
    val color = categoryColor(usage.category, scheme)

    val subtitleText = when (usage.category) {
        StorageCategoryKey.LOGS -> stringResource(R.string.storage_category_logs_subtitle, usage.fileCount)
        else -> stringResource(
            R.string.storage_category_subtitle,
            bytesText,
            usage.fileCount,
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        shape = AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else color,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun categoryTitleRes(category: StorageCategoryKey): Int = when (category) {
    StorageCategoryKey.IMAGES -> R.string.storage_category_images
    StorageCategoryKey.FILES -> R.string.storage_category_files
    StorageCategoryKey.CHAT_RECORDS -> R.string.storage_category_chat_records
    StorageCategoryKey.CACHE -> R.string.storage_category_cache
    StorageCategoryKey.HISTORY_FILES -> R.string.storage_category_history_files
    StorageCategoryKey.LOGS -> R.string.storage_category_logs
}

private fun categoryIcon(category: StorageCategoryKey) = when (category) {
    StorageCategoryKey.IMAGES -> Icons.Rounded.Image
    StorageCategoryKey.FILES -> Icons.Rounded.InsertDriveFile
    StorageCategoryKey.CHAT_RECORDS -> Icons.Rounded.Storage
    StorageCategoryKey.CACHE -> Icons.Rounded.Cached
    StorageCategoryKey.HISTORY_FILES -> Icons.Rounded.AutoDelete
    StorageCategoryKey.LOGS -> Icons.Rounded.History
}

private fun categoryColor(category: StorageCategoryKey, scheme: ColorScheme): Color = when (category) {
    StorageCategoryKey.IMAGES -> scheme.primary
    StorageCategoryKey.FILES -> scheme.tertiary
    StorageCategoryKey.CHAT_RECORDS -> scheme.secondary
    StorageCategoryKey.CACHE -> scheme.primary
    StorageCategoryKey.HISTORY_FILES -> scheme.error
    StorageCategoryKey.LOGS -> scheme.secondary
}
