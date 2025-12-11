package me.rerere.rikkahub.ui.pages.assistant.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Topic
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.NodeType
import me.rerere.rikkahub.data.memory.entity.MemoryTier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Entities Tab - Browse all known entities with filtering and search.
 * 
 * Features:
 * - Segmented filter by entity type
 * - Search functionality
 * - Entity cards with relationship count
 * - Tap to open entity detail sheet
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EntitiesTab(
    viewModel: KnowledgeMapViewModel,
    onEntityClick: (MemoryNode) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<NodeType?>(null) }
    var selectedNode by remember { mutableStateOf<MemoryNode?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var nodeToDelete by remember { mutableStateOf<MemoryNode?>(null) }
    
    // Filter nodes
    val filteredNodes = remember(uiState.nodes, searchQuery, selectedType) {
        uiState.nodes.filter { node ->
            val matchesSearch = searchQuery.isEmpty() || 
                node.label.contains(searchQuery, ignoreCase = true) ||
                node.content?.contains(searchQuery, ignoreCase = true) == true
            
            val matchesType = selectedType == null || node.nodeType == selectedType
            
            matchesSearch && matchesType
        }.sortedByDescending { it.lastAccessedAt }
    }
    
    // Count by type for filter chips
    val countByType = remember(uiState.nodes) {
        uiState.nodes.groupingBy { it.nodeType }.eachCount()
    }
    
    // Edges for relationship counting
    val edgesByNode = remember(uiState.edges) {
        uiState.edges.groupBy { it.sourceId } + uiState.edges.groupBy { it.targetId }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search entities...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Outlined.Clear,
                            contentDescription = "Clear"
                        )
                    }
                }
            },
            singleLine = true,
            shape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
        
        // Filter Chips Header & Content
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            var isFiltersExpanded by remember { mutableStateOf(true) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isFiltersExpanded = !isFiltersExpanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Filter by Type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Icon(
                    imageVector = if (isFiltersExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isFiltersExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            AnimatedVisibility(
                visible = isFiltersExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // All filter
                    EntityFilterChip(
                        label = "All",
                        count = uiState.nodes.size,
                        selected = selectedType == null,
                        onClick = { selectedType = null },
                        icon = Icons.Outlined.Category,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Type-specific filters
                    NodeType.entries.forEach { type ->
                        val count = countByType[type] ?: 0
                        if (count > 0) {
                            EntityFilterChip(
                                label = type.displayName,
                                count = count,
                                selected = selectedType == type,
                                onClick = { selectedType = if (selectedType == type) null else type },
                                icon = type.iconVector,
                                color = type.colorValue
                            )
                        }
                    }
                }
            }
        }
        
        // Results count
        Text(
            text = "${filteredNodes.size} ${if (filteredNodes.size == 1) "entity" else "entities"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        
        // Entity List
        if (filteredNodes.isEmpty()) {
            EmptyEntitiesState(
                hasFilters = searchQuery.isNotEmpty() || selectedType != null
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = filteredNodes,
                    key = { it.id }
                ) { node ->
                    val relationCount = (edgesByNode[node.id]?.size ?: 0)
                    
                    EntityCard(
                        node = node,
                        relationCount = relationCount,
                        onClick = { selectedNode = node },
                        onDeleteClick = { 
                            nodeToDelete = node
                            showDeleteDialog = true
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
    
    // Entity Detail Bottom Sheet
    if (selectedNode != null) {
        EntityDetailBottomSheet(
            node = selectedNode!!,
            edges = uiState.edges.filter { it.sourceId == selectedNode!!.id || it.targetId == selectedNode!!.id },
            allNodes = uiState.nodes,
            onDismiss = { selectedNode = null },
            onNodeClick = { relatedNode ->
                selectedNode = relatedNode
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog && nodeToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                nodeToDelete = null
            },
            title = { Text("Delete Entity") },
            text = { 
                Text("Are you sure you want to delete \"${nodeToDelete!!.label}\"? This will also remove all related connections.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNode(nodeToDelete!!.id)
                        showDeleteDialog = false
                        nodeToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteDialog = false
                    nodeToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EntityFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    color: Color
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) color.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chipBg"
    )
    
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(label)
                Surface(
                    shape = CircleShape,
                    color = if (selected) color.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.15f),
            selectedLabelColor = color,
            selectedLeadingIconColor = color
        )
    )
}

@Composable
private fun EntityCard(
    node: MemoryNode,
    relationCount: Int,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(node.nodeType.colorValue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = node.nodeType.iconVector,
                    contentDescription = null,
                    tint = node.nodeType.colorValue,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Tier badge
                    if (node.tier == MemoryTier.CORE) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFFD700).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Core",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB8860B),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                if (!node.content.isNullOrBlank()) {
                    Text(
                        text = node.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // Metadata row
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Relationships
                    if (relationCount > 0) {
                        MetadataChip(
                            icon = Icons.Outlined.Hub,
                            text = "$relationCount ${if (relationCount == 1) "relation" else "relations"}"
                        )
                    }
                    
                    // Confidence
                    if (node.confidence < 0.8f) {
                        MetadataChip(
                            icon = Icons.Outlined.TrendingUp,
                            text = "${(node.confidence * 100).toInt()}%"
                        )
                    }
                    
                    // Last accessed
                    MetadataChip(
                        icon = Icons.Outlined.Schedule,
                        text = formatRelativeTime(node.lastAccessedAt)
                    )
                }
            }
            
            // Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("View Details") },
                        onClick = {
                            showMenu = false
                            onClick()
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataChip(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityDetailBottomSheet(
    node: MemoryNode,
    edges: List<MemoryEdge>,
    allNodes: List<MemoryNode>,
    onDismiss: () -> Unit,
    onNodeClick: (MemoryNode) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(node.nodeType.colorValue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = node.nodeType.iconVector,
                            contentDescription = null,
                            tint = node.nodeType.colorValue,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = node.label,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = node.nodeType.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = node.nodeType.colorValue
                        )
                    }
                }
            }
            
            // Content
            if (!node.content.isNullOrBlank()) {
                item {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = node.content,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // Relations
            if (edges.isNotEmpty()) {
                item {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Relationships (${edges.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                items(edges) { edge ->
                    val relatedNode = if (edge.sourceId == node.id) {
                        allNodes.find { it.id == edge.targetId }
                    } else {
                        allNodes.find { it.id == edge.sourceId }
                    }
                    
                    if (relatedNode != null) {
                        RelationshipItem(
                            edge = edge,
                            relatedNode = relatedNode,
                            isOutgoing = edge.sourceId == node.id,
                            onClick = { onNodeClick(relatedNode) }
                        )
                    }
                }
            }
            
            // Metadata
            item {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    DetailRow("Type", node.nodeType.displayName)
                    DetailRow("Tier", node.tier.name.lowercase().replaceFirstChar { it.uppercase() })
                    DetailRow("Confidence", "${(node.confidence * 100).toInt()}%")
                    DetailRow("Created", formatDate(node.createdAt))
                    DetailRow("Last accessed", formatDate(node.lastAccessedAt))
                    DetailRow("Access count", node.accessCount.toString())
                }
            }
        }
    }
}

@Composable
private fun RelationshipItem(
    edge: MemoryEdge,
    relatedNode: MemoryNode,
    isOutgoing: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardSmall,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(relatedNode.nodeType.colorValue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = relatedNode.nodeType.iconVector,
                    contentDescription = null,
                    tint = relatedNode.nodeType.colorValue,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = relatedNode.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${if (isOutgoing) "→" else "←"} ${edge.edgeType.name.lowercase().replace("_", " ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyEntitiesState(hasFilters: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (hasFilters) Icons.Outlined.Search else Icons.Outlined.Category,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (hasFilters) "No matching entities" else "No entities yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (hasFilters) 
                "Try adjusting your search or filters" 
            else 
                "Entities will appear here as you chat",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// Extension properties for NodeType
private val NodeType.displayName: String
    get() = when (this) {
        NodeType.PERSON -> "Person"
        NodeType.PLACE -> "Place"
        NodeType.ORGANIZATION -> "Organization"
        NodeType.OBJECT -> "Object"
        NodeType.CONCEPT -> "Concept"
        NodeType.SKILL -> "Skill"
        NodeType.INTEREST -> "Interest"
        NodeType.FACT -> "Fact"
        NodeType.PREFERENCE -> "Preference"
        NodeType.BELIEF -> "Belief"
        NodeType.GOAL -> "Goal"
        NodeType.EVENT -> "Event"
        NodeType.ROUTINE -> "Routine"
        NodeType.REMINDER -> "Reminder"
        NodeType.EMOTION -> "Emotion"
    }

private val NodeType.iconVector: ImageVector
    get() = when (this) {
        NodeType.PERSON -> Icons.Outlined.Person
        NodeType.PLACE -> Icons.Outlined.Place
        NodeType.ORGANIZATION -> Icons.Outlined.Category
        NodeType.OBJECT -> Icons.Outlined.Category
        NodeType.CONCEPT -> Icons.Outlined.Lightbulb
        NodeType.SKILL -> Icons.Outlined.TrendingUp
        NodeType.INTEREST -> Icons.Outlined.Favorite
        NodeType.FACT -> Icons.Outlined.Category
        NodeType.PREFERENCE -> Icons.Outlined.Star
        NodeType.BELIEF -> Icons.Outlined.Psychology
        NodeType.GOAL -> Icons.Outlined.TrendingUp
        NodeType.EVENT -> Icons.Outlined.Event
        NodeType.ROUTINE -> Icons.Outlined.Repeat
        NodeType.REMINDER -> Icons.Outlined.Schedule
        NodeType.EMOTION -> Icons.Outlined.Favorite
    }

private val NodeType.colorValue: Color
    get() = Color(this.color)

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestamp))
}

