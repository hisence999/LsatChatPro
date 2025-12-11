package me.rerere.rikkahub.ui.pages.assistant.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.NodeType
import me.rerere.rikkahub.data.memory.entity.MemoryTier

/**
 * Insights Tab - Display patterns, routines, and generalizations the system has learned.
 * 
 * Features:
 * - Pattern cards (generalizations from consolidation)
 * - Routine visualization
 * - Preference summary
 * - Belief/Goal tracking with confidence
 */
@Composable
fun InsightsTab(
    viewModel: KnowledgeMapViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Group insights by type - using CONCEPT for patterns since we don't have GENERALIZATION
    val patterns = remember(uiState.nodes) {
        uiState.nodes.filter { it.nodeType == NodeType.CONCEPT }
            .sortedByDescending { it.confidence }
    }
    
    val routines = remember(uiState.nodes) {
        uiState.nodes.filter { it.nodeType == NodeType.ROUTINE }
            .sortedByDescending { it.accessCount }
    }
    
    val preferences = remember(uiState.nodes) {
        uiState.nodes.filter { it.nodeType == NodeType.PREFERENCE }
            .sortedByDescending { it.confidence }
    }
    
    val beliefs = remember(uiState.nodes) {
        uiState.nodes.filter { it.nodeType == NodeType.BELIEF }
    }
    
    val goals = remember(uiState.nodes) {
        uiState.nodes.filter { it.nodeType == NodeType.GOAL }
    }
    
    val hasAnyInsights = patterns.isNotEmpty() || routines.isNotEmpty() || 
        preferences.isNotEmpty() || beliefs.isNotEmpty() || goals.isNotEmpty()
    
    // Calculate network statistics
    val totalNodes = uiState.nodes.size
    val totalEdges = uiState.edges.size
    val avgConfidence = remember(uiState.nodes) {
        if (uiState.nodes.isEmpty()) 0f
        else uiState.nodes.map { it.confidence }.average().toFloat()
    }
    
    // Find most connected entity
    val mostConnected = remember(uiState.nodes, uiState.edges) {
        uiState.nodes.maxByOrNull { node ->
            uiState.edges.count { it.sourceId == node.id || it.targetId == node.id }
        }
    }
    val mostConnectedCount = remember(mostConnected, uiState.edges) {
        mostConnected?.let { node ->
            uiState.edges.count { it.sourceId == node.id || it.targetId == node.id }
        } ?: 0
    }
    
    // Count by type for Topic Distribution
    val typeDistribution = remember(uiState.nodes) {
        NodeType.entries.associateWith { type ->
            uiState.nodes.count { it.nodeType == type }
        }.filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }
    }
    
    if (totalNodes == 0) {
        EmptyInsightsState()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Memory Network Summary Card
            item {
                MemoryNetworkSummaryCard(
                    totalNodes = totalNodes,
                    totalEdges = totalEdges,
                    avgConfidence = avgConfidence,
                    mostConnectedLabel = mostConnected?.label,
                    mostConnectedCount = mostConnectedCount
                )
            }
            
            // Topic Distribution Section
            if (typeDistribution.isNotEmpty()) {
                item {
                    InsightSection(
                        title = "What I Know About",
                        subtitle = "Topics by category",
                        icon = Icons.Outlined.Category,
                        color = Color(0xFF10B981)
                    )
                }
                
                item {
                    TopicDistributionCard(distribution = typeDistribution)
                }
            }
            // Patterns Section
            if (patterns.isNotEmpty()) {
                item {
                    InsightSection(
                        title = "Discovered Patterns",
                        subtitle = "Things I've noticed about you",
                        icon = Icons.Outlined.Lightbulb,
                        color = Color(0xFFA855F7)
                    )
                }
                
                items(patterns.take(5)) { pattern ->
                    PatternCard(pattern = pattern)
                }
            }
            
            // Routines Section
            if (routines.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    InsightSection(
                        title = "Your Routines",
                        subtitle = "Regular activities I've learned",
                        icon = Icons.Outlined.Repeat,
                        color = Color(0xFFEC4899)
                    )
                }
                
                item {
                    RoutinesGrid(routines = routines.take(6))
                }
            }
            
            // Preferences Section
            if (preferences.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    InsightSection(
                        title = "Your Preferences",
                        subtitle = "Things you like and dislike",
                        icon = Icons.Outlined.Star,
                        color = Color(0xFF06B6D4)
                    )
                }
                
                item {
                    PreferencesCard(preferences = preferences.take(8))
                }
            }
            
            // Beliefs & Goals
            if (beliefs.isNotEmpty() || goals.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    InsightSection(
                        title = "Beliefs & Goals",
                        subtitle = "Your values and aspirations",
                        icon = Icons.Outlined.Psychology,
                        color = Color(0xFF6366F1)
                    )
                }
                
                if (beliefs.isNotEmpty()) {
                    items(beliefs.take(3)) { belief ->
                        BeliefCard(belief = belief)
                    }
                }
                
                if (goals.isNotEmpty()) {
                    items(goals.take(3)) { goal ->
                        GoalCard(goal = goal)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PatternCard(pattern: MemoryNode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFA855F7).copy(alpha = 0.1f),
                            Color(0xFF6366F1).copy(alpha = 0.1f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFA855F7),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pattern.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Confidence
                    ConfidenceBadge(confidence = pattern.confidence)
                }
                
                if (!pattern.content.isNullOrBlank()) {
                    Text(
                        text = pattern.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutinesGrid(routines: List<MemoryNode>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        routines.chunked(2).forEach { rowRoutines ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowRoutines.forEach { routine ->
                    RoutineChip(
                        routine = routine,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty space if odd number
                if (rowRoutines.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RoutineChip(
    routine: MemoryNode,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardSmall,
        color = Color(0xFFEC4899).copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFFEC4899).copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = Color(0xFFEC4899),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!routine.content.isNullOrBlank()) {
                    Text(
                        text = routine.content.take(30),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferencesCard(preferences: List<MemoryNode>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            preferences.forEach { pref ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF06B6D4))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${pref.label}: ${pref.content?.take(50) ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    ConfidenceBadge(confidence = pref.confidence)
                }
            }
        }
    }
}

@Composable
private fun BeliefCard(belief: MemoryNode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF6366F1).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Psychology,
                contentDescription = null,
                tint = Color(0xFF6366F1),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = belief.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                if (!belief.content.isNullOrBlank()) {
                    Text(
                        text = belief.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ConfidenceBadge(confidence = belief.confidence)
        }
    }
}

@Composable
private fun GoalCard(goal: MemoryNode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF84CC16).copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.TrendingUp,
                contentDescription = null,
                tint = Color(0xFF84CC16),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                if (!goal.content.isNullOrBlank()) {
                    Text(
                        text = goal.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Goal progress (if tracked)
            val progress = goal.confidence // Using confidence as a proxy for progress
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF84CC16)
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .width(60.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF84CC16),
                    trackColor = Color(0xFF84CC16).copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Float) {
    val color = when {
        confidence >= 0.8f -> Color(0xFF10B981)
        confidence >= 0.5f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = "${(confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun EmptyInsightsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Lightbulb,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No Insights Yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "As you chat more, I'll discover patterns, routines, and preferences that make you unique.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "💡 Tip: Have more conversations to unlock insights!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MemoryNetworkSummaryCard(
    totalNodes: Int,
    totalEdges: Int,
    avgConfidence: Float,
    mostConnectedLabel: String?,
    mostConnectedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.15f),
                            Color(0xFF8B5CF6).copy(alpha = 0.1f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Memory Network",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF6366F1).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${(avgConfidence * 100).toInt()}% confident",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF6366F1),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        value = totalNodes.toString(),
                        label = "Entities",
                        color = Color(0xFF10B981)
                    )
                    StatItem(
                        value = totalEdges.toString(),
                        label = "Connections",
                        color = Color(0xFFF59E0B)
                    )
                }
                
                if (mostConnectedLabel != null && mostConnectedCount > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Most Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = mostConnectedLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "$mostConnectedCount links",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TopicDistributionCard(
    distribution: List<Pair<NodeType, Int>>
) {
    val maxCount = distribution.maxOfOrNull { it.second } ?: 1
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            distribution.take(6).forEach { (type, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type icon
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(type.color).copy(alpha = 0.15f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = getNodeTypeIcon(type),
                                contentDescription = null,
                                tint = Color(type.color),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    // Type name
                    Text(
                        text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(80.dp)
                    )
                    
                    // Progress bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = count.toFloat() / maxCount)
                                .background(
                                    Color(type.color),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Count
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(type.color),
                        modifier = Modifier.width(24.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

private fun getNodeTypeIcon(type: NodeType): ImageVector {
    return when (type) {
        NodeType.PERSON -> Icons.Outlined.Favorite
        NodeType.PLACE -> Icons.Outlined.Category
        NodeType.ORGANIZATION -> Icons.Outlined.Category
        NodeType.OBJECT -> Icons.Outlined.Category
        NodeType.CONCEPT -> Icons.Outlined.Lightbulb
        NodeType.SKILL -> Icons.Outlined.TrendingUp
        NodeType.INTEREST -> Icons.Outlined.Favorite
        NodeType.FACT -> Icons.Outlined.Category
        NodeType.PREFERENCE -> Icons.Outlined.Star
        NodeType.BELIEF -> Icons.Outlined.Psychology
        NodeType.GOAL -> Icons.Outlined.TrendingUp
        NodeType.EVENT -> Icons.Outlined.CalendarMonth
        NodeType.ROUTINE -> Icons.Outlined.Repeat
        NodeType.REMINDER -> Icons.Outlined.CalendarMonth
        NodeType.EMOTION -> Icons.Outlined.Favorite
    }
}
