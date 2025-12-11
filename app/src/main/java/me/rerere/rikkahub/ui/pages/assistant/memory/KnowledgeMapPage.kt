package me.rerere.rikkahub.ui.pages.assistant.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.MemoryTier
import me.rerere.rikkahub.data.memory.entity.NodeType

/**
 * View mode for the knowledge map page.
 */
enum class ViewMode { MAP, LIST }

/**
 * Main knowledge map page showing the assistant's memories.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KnowledgeMapPage(
    viewModel: KnowledgeMapViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    
    var viewMode by remember { mutableStateOf(ViewMode.MAP) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var selectedTypes by remember { mutableStateOf(setOf<NodeType>()) }
    var selectedTiers by remember { mutableStateOf(setOf<MemoryTier>()) }
    
    // Filter nodes
    val filteredNodes = remember(state.nodes, searchQuery, selectedTypes, selectedTiers) {
        state.nodes.filter { node ->
            val matchesSearch = searchQuery.isBlank() || 
                node.label.contains(searchQuery, ignoreCase = true) ||
                node.content.contains(searchQuery, ignoreCase = true)
            val matchesType = selectedTypes.isEmpty() || node.nodeType in selectedTypes
            val matchesTier = selectedTiers.isEmpty() || node.tier in selectedTiers
            matchesSearch && matchesType && matchesTier
        }
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Header with stats
        if (state.stats != null) {
            StatsCard(stats = state.stats!!)
        }
        
        // Search and controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search memories...") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            
            Spacer(Modifier.width(8.dp))
            
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(
                    Icons.Outlined.FilterList,
                    contentDescription = "Filters",
                    tint = if (selectedTypes.isNotEmpty() || selectedTiers.isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
            }
        }
        
        // View mode toggle
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            SegmentedButton(
                selected = viewMode == ViewMode.MAP,
                onClick = { viewMode = ViewMode.MAP },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)) }
            ) {
                Text("Map")
            }
            SegmentedButton(
                selected = viewMode == ViewMode.LIST,
                onClick = { viewMode = ViewMode.LIST },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Outlined.ViewList, null, Modifier.size(18.dp)) }
            ) {
                Text("List")
            }
        }
        
        // Filters
        if (showFilters) {
            FilterSection(
                selectedTypes = selectedTypes,
                onTypesChange = { selectedTypes = it },
                selectedTiers = selectedTiers,
                onTiersChange = { selectedTiers = it }
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Content
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading knowledge graph...")
                    }
                }
            }
            
            viewModel.migrationState.collectAsState().value is MigrationState.InProgress -> {
                val migrationProgress = viewModel.migrationState.collectAsState().value as MigrationState.InProgress
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Migrating memories to new system...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { migrationProgress.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = migrationProgress.stage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            filteredNodes.isEmpty() -> {
                KnowledgeMapEmptyState()
            }
            
            else -> {
                when (viewMode) {
                    ViewMode.MAP -> {
                        KnowledgeMapView(
                            nodes = filteredNodes,
                            edges = state.edges.filter { edge ->
                                filteredNodes.any { it.id == edge.sourceId } &&
                                filteredNodes.any { it.id == edge.targetId }
                            },
                            onNodeTap = { node -> viewModel.selectNode(node) },
                            selectedNodeId = state.selectedNode?.id,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ViewMode.LIST -> {
                        MemoryNodeList(
                            nodes = filteredNodes,
                            onNodeTap = { node -> viewModel.selectNode(node) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        // Node detail sheet
        state.selectedNode?.let { node ->
            val connectedNodes = remember(node, state.edges, state.nodes) {
                val connectedIds = state.edges
                    .filter { it.sourceId == node.id || it.targetId == node.id }
                    .flatMap { listOf(it.sourceId, it.targetId) }
                    .filter { it != node.id }
                    .toSet()
                state.nodes.filter { it.id in connectedIds }
            }
            
            NodeDetailSheet(
                node = node,
                connectedNodes = connectedNodes,
                edges = state.edges.filter { 
                    it.sourceId == node.id || it.targetId == node.id 
                },
                onDismiss = { viewModel.selectNode(null) },
                onPromoteToCore = { viewModel.promoteToCore(node) },
                onDemoteFromCore = { viewModel.demoteFromCore(node) },
                onArchive = { viewModel.archiveNode(node) },
                onDelete = { viewModel.deleteNode(node) },
                onNavigateToNode = { viewModel.selectNode(it) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    selectedTypes: Set<NodeType>,
    onTypesChange: (Set<NodeType>) -> Unit,
    selectedTiers: Set<MemoryTier>,
    onTiersChange: (Set<MemoryTier>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Filter by Type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NodeType.entries.forEach { type ->
                FilterChip(
                    selected = type in selectedTypes,
                    onClick = {
                        onTypesChange(
                            if (type in selectedTypes) selectedTypes - type
                            else selectedTypes + type
                        )
                    },
                    label = { Text("${type.icon} ${type.name.lowercase()}") }
                )
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "Filter by Tier",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MemoryTier.entries.forEach { tier ->
                FilterChip(
                    selected = tier in selectedTiers,
                    onClick = {
                        onTiersChange(
                            if (tier in selectedTiers) selectedTiers - tier
                            else selectedTiers + tier
                        )
                    },
                    label = { Text(tier.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }
    }
}

@Composable
private fun StatsCard(stats: KnowledgeGraphStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(value = stats.totalNodes.toString(), label = "Nodes")
            StatItem(value = stats.totalEdges.toString(), label = "Edges")
            StatItem(value = stats.coreCount.toString(), label = "Core")
            StatItem(value = stats.recallCount.toString(), label = "Recall")
            StatItem(value = stats.archivalCount.toString(), label = "Archived")
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MemoryNodeList(
    nodes: List<MemoryNode>,
    onNodeTap: (MemoryNode) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(nodes.size, key = { nodes[it].id }) { index ->
            val node = nodes[index]
            MemoryNodeCard(node = node, onClick = { onNodeTap(node) })
        }
    }
}

@Composable
private fun MemoryNodeCard(
    node: MemoryNode,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = node.nodeType.icon,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = node.content.take(100),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            
            // Tier indicator
            val tierColor = when (node.tier) {
                MemoryTier.CORE -> Color(0xFFFFD700)
                MemoryTier.RECALL -> Color(0xFF4A90D9)
                MemoryTier.ARCHIVAL -> Color(0xFF9E9E9E)
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .padding(2.dp)
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    drawCircle(tierColor)
                }
            }
        }
    }
}

data class KnowledgeGraphStats(
    val totalNodes: Int,
    val totalEdges: Int,
    val coreCount: Int,
    val recallCount: Int,
    val archivalCount: Int
)
