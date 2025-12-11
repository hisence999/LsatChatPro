package me.rerere.rikkahub.ui.pages.assistant.memory

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.rerere.rikkahub.data.memory.engine.ActivationEngine
import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.MemoryNode

/**
 * Interactive knowledge map visualization using force-directed layout.
 * 
 * Features:
 * - Pan and zoom gestures
 * - Tap to select nodes
 * - Animated layout simulation
 * - Node sizing based on activation
 * - Edge thickness based on weight
 * - Color coding by node type
 */
@Composable
fun KnowledgeMapView(
    nodes: List<MemoryNode>,
    edges: List<MemoryEdge>,
    onNodeTap: (MemoryNode) -> Unit,
    selectedNodeId: String? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    
    // Transform state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    // Layout state
    var positions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    var isSimulating by remember { mutableStateOf(true) }
    
    // Drag state for node interaction
    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Convert to UI models
    val uiNodes = remember(nodes) {
        nodes.map { node ->
            val activation = ActivationEngine.calculateActivation(node)
            GraphUINode(
                id = node.id,
                label = node.label,
                nodeType = node.nodeType.name,
                color = node.nodeType.color,
                icon = node.nodeType.icon,
                size = (0.8f + activation.coerceIn(-1f, 3f) / 4f).coerceIn(0.5f, 1.5f),
                activation = activation
            )
        }
    }
    
    val uiEdges = remember(edges) {
        edges.map { edge ->
            GraphUIEdge(
                id = edge.id,
                sourceId = edge.sourceId,
                targetId = edge.targetId,
                label = edge.edgeType.displayName,
                weight = edge.weight
            )
        }
    }
    
    val nodeMap = remember(uiNodes) { uiNodes.associateBy { it.id } }
    
    // Layout simulation
    LaunchedEffect(uiNodes, uiEdges) {
        if (uiNodes.isEmpty()) return@LaunchedEffect
        
        val layout = ForceDirectedLayout(
            nodes = uiNodes,
            edges = uiEdges,
            width = 800f,
            height = 600f
        )
        layout.initialize()
        isSimulating = true
        
        // Animate layout
        repeat(150) { iteration ->
            val (newPositions, converged) = layout.step()
            positions = newPositions
            
            if (converged && iteration > 30) {
                isSimulating = false
                return@LaunchedEffect
            }
            
            // Slow down animation as it converges
            val delayMs = if (iteration < 30) 16L else 32L
            delay(delayMs)
        }
        
        isSimulating = false
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (!isDragging) {
                        scale = (scale * zoom).coerceIn(0.3f, 3f)
                        offset += pan
                    }
                }
            }
            .pointerInput(nodes, positions) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        // Find which node was tapped
                        val graphTap = (startOffset - offset) / scale
                        for (node in uiNodes) {
                            val pos = positions[node.id] ?: continue
                            val distance = (graphTap - pos).getDistance()
                            val nodeRadius = 20f * node.size
                            if (distance <= nodeRadius + 15f) {
                                draggedNodeId = node.id
                                isDragging = true
                                break
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (isDragging && draggedNodeId != null) {
                            change.consume()
                            val scaledDrag = dragAmount / scale
                            val currentPos = positions[draggedNodeId] ?: return@detectDragGestures
                            val newPos = currentPos + Offset(scaledDrag.x, scaledDrag.y)
                            
                            // Update dragged node position
                            val mutablePositions = positions.toMutableMap()
                            mutablePositions[draggedNodeId!!] = newPos
                            
                            // Pull connected nodes slightly (physics effect)
                            val pullStrength = 0.15f
                            for (edge in uiEdges) {
                                val connectedId = when {
                                    edge.sourceId == draggedNodeId -> edge.targetId
                                    edge.targetId == draggedNodeId -> edge.sourceId
                                    else -> null
                                }
                                if (connectedId != null && mutablePositions.containsKey(connectedId)) {
                                    val connectedPos = mutablePositions[connectedId]!!
                                    val pullDelta = Offset(scaledDrag.x * pullStrength, scaledDrag.y * pullStrength)
                                    mutablePositions[connectedId] = connectedPos + pullDelta
                                }
                            }
                            
                            positions = mutablePositions
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        draggedNodeId = null
                    },
                    onDragCancel = {
                        isDragging = false
                        draggedNodeId = null
                    }
                )
            }
            .pointerInput(nodes) {
                detectTapGestures { tapOffset ->
                    // Convert tap to graph coordinates
                    val graphTap = (tapOffset - offset) / scale
                    
                    // Find tapped node
                    for (node in uiNodes) {
                        val pos = positions[node.id] ?: continue
                        val distance = (graphTap - pos).getDistance()
                        val nodeRadius = 20f * node.size
                        
                        if (distance <= nodeRadius + 10f) {
                            nodes.find { it.id == node.id }?.let { onNodeTap(it) }
                            return@detectTapGestures
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale)
            }) {
                // === DRAW EDGES ===
                for (edge in uiEdges) {
                    val startPos = positions[edge.sourceId] ?: continue
                    val endPos = positions[edge.targetId] ?: continue
                    
                    val isConnectedToSelected = selectedNodeId != null &&
                            (edge.sourceId == selectedNodeId || edge.targetId == selectedNodeId)
                    
                    val edgeColor = if (isConnectedToSelected) {
                        Color(0xFF4A90D9).copy(alpha = 0.8f)
                    } else {
                        Color.Gray.copy(alpha = 0.3f)
                    }
                    
                    val strokeWidth = (1f + edge.weight * 2f) * 
                            (if (isConnectedToSelected) 1.5f else 1f)
                    
                    drawLine(
                        color = edgeColor,
                        start = startPos,
                        end = endPos,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                
                // === DRAW NODES ===
                for (node in uiNodes) {
                    val pos = positions[node.id] ?: continue
                    val isSelected = node.id == selectedNodeId
                    val baseRadius = 20f * node.size
                    
                    // Selection highlight
                    if (isSelected) {
                        drawCircle(
                            color = Color(node.color).copy(alpha = 0.3f),
                            radius = baseRadius * 1.5f,
                            center = pos
                        )
                    }
                    
                    // Node circle
                    drawCircle(
                        color = Color(node.color),
                        radius = baseRadius,
                        center = pos
                    )
                    
                    // Inner gradient effect
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        radius = baseRadius * 0.7f,
                        center = pos - Offset(baseRadius * 0.15f, baseRadius * 0.15f)
                    )
                    
                    // Label
                    val labelText = if (node.label.length > 12) {
                        node.label.take(10) + "…"
                    } else {
                        node.label
                    }
                    
                    val textLayout = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = (10 + node.size * 2).sp
                        )
                    )
                    
                    drawText(
                        textMeasurer = textMeasurer,
                        text = labelText,
                        style = TextStyle(
                            color = if (isSelected) Color(node.color) else Color.Gray,
                            fontSize = (10 + node.size * 2).sp
                        ),
                        topLeft = Offset(
                            pos.x - textLayout.size.width / 2,
                            pos.y + baseRadius + 5f
                        )
                    )
                }
            }
        }
    }
}

/**
 * Empty state for knowledge map.
 */
@Composable
fun KnowledgeMapEmptyState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.02f)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.material3.Text(
                text = "🧠",
                fontSize = 48.sp
            )
            androidx.compose.material3.Text(
                text = "No memories yet",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.Text(
                text = "Chat with your assistant to build memory",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
