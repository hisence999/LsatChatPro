package me.rerere.rikkahub.ui.pages.assistant.memory

import androidx.compose.ui.geometry.Offset
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Force-directed graph layout algorithm for knowledge map visualization.
 * 
 * Uses a physics simulation with:
 * - Repulsion between all nodes (prevent overlap)
 * - Attraction along edges (connected nodes cluster)
 * - Gravity toward center (prevent drift)
 * - Damping to stabilize
 * 
 * Based on Fruchterman-Reingold algorithm with modifications for
 * real-time animation.
 */
class ForceDirectedLayout(
    private val nodes: List<GraphUINode>,
    private val edges: List<GraphUIEdge>,
    private val width: Float = 800f,
    private val height: Float = 600f
) {
    private val positions = mutableMapOf<String, Offset>()
    private val velocities = mutableMapOf<String, Offset>()
    
    companion object {
        const val REPULSION_STRENGTH = 15000f  // Increased for better spacing
        const val ATTRACTION_STRENGTH = 0.02f  // Slightly reduced to prevent clustering
        const val GRAVITY_STRENGTH = 0.015f    // Reduced gravity
        const val DAMPING = 0.85f
        const val MIN_DISTANCE = 80f           // Increased min distance for label visibility
        const val MAX_VELOCITY = 50f
        const val CONVERGENCE_THRESHOLD = 0.1f
    }
    
    /**
     * Initialize node positions randomly within bounds.
     */
    fun initialize() {
        val cx = width / 2
        val cy = height / 2
        val radius = minOf(width, height) * 0.35f  // Slightly smaller for tighter initial layout
        
        nodes.forEachIndexed { index, node ->
            // Distribute nodes in a circle initially for better starting positions
            val angle = (index.toFloat() / nodes.size) * 2 * Math.PI
            positions[node.id] = Offset(
                cx + (radius * kotlin.math.cos(angle) * Random.nextFloat()).toFloat(),
                cy + (radius * kotlin.math.sin(angle) * Random.nextFloat()).toFloat()
            )
            velocities[node.id] = Offset.Zero
        }
    }
    
    /**
     * Run one simulation step and return updated positions.
     * Returns true if the layout has converged (minimal movement).
     */
    fun step(): Pair<Map<String, Offset>, Boolean> {
        if (nodes.isEmpty()) return emptyMap<String, Offset>() to true
        
        val forces = mutableMapOf<String, Offset>()
        nodes.forEach { forces[it.id] = Offset.Zero }
        
        // === REPULSION (all pairs) ===
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val nodeA = nodes[i]
                val nodeB = nodes[j]
                val posA = positions[nodeA.id] ?: continue
                val posB = positions[nodeB.id] ?: continue
                
                val delta = posA - posB
                val distance = delta.getDistance().coerceAtLeast(MIN_DISTANCE)
                
                // Coulomb's law: F = k / d^2
                val forceMagnitude = REPULSION_STRENGTH / (distance * distance)
                val force = delta.normalize() * forceMagnitude
                
                forces[nodeA.id] = forces[nodeA.id]!! + force
                forces[nodeB.id] = forces[nodeB.id]!! - force
            }
        }
        
        // === ATTRACTION (along edges) ===
        for (edge in edges) {
            val posSource = positions[edge.sourceId] ?: continue
            val posTarget = positions[edge.targetId] ?: continue
            
            val delta = posTarget - posSource
            val distance = delta.getDistance()
            
            if (distance < MIN_DISTANCE) continue
            
            // Hooke's law: F = k * d (stronger connection = stronger attraction)
            val forceMagnitude = ATTRACTION_STRENGTH * distance * edge.weight
            val force = delta.normalize() * forceMagnitude
            
            forces[edge.sourceId] = forces[edge.sourceId]!! + force
            forces[edge.targetId] = forces[edge.targetId]!! - force
        }
        
        // === GRAVITY (toward center) ===
        val center = Offset(width / 2, height / 2)
        for (node in nodes) {
            val pos = positions[node.id] ?: continue
            val toCenter = center - pos
            val force = toCenter * GRAVITY_STRENGTH
            forces[node.id] = forces[node.id]!! + force
        }
        
        // === APPLY FORCES ===
        var totalMovement = 0f
        
        for (node in nodes) {
            var velocity = velocities[node.id]!! + forces[node.id]!!
            
            // Clamp velocity
            val speed = velocity.getDistance()
            if (speed > MAX_VELOCITY) {
                velocity = velocity.normalize() * MAX_VELOCITY
            }
            
            // Apply damping
            velocity *= DAMPING
            
            // Update position
            val newPos = positions[node.id]!! + velocity
            
            // Keep within bounds with padding
            val padding = 50f
            val clampedPos = Offset(
                newPos.x.coerceIn(padding, width - padding),
                newPos.y.coerceIn(padding, height - padding)
            )
            
            totalMovement += (clampedPos - positions[node.id]!!).getDistance()
            
            positions[node.id] = clampedPos
            velocities[node.id] = velocity
        }
        
        val avgMovement = totalMovement / nodes.size
        val converged = avgMovement < CONVERGENCE_THRESHOLD
        
        return positions.toMap() to converged
    }
    
    /**
     * Get current positions.
     */
    fun getPositions(): Map<String, Offset> = positions.toMap()
    
    /**
     * Run simulation until convergence or max iterations.
     */
    fun runUntilConverged(maxIterations: Int = 200): Map<String, Offset> {
        initialize()
        repeat(maxIterations) {
            val (_, converged) = step()
            if (converged) return positions.toMap()
        }
        return positions.toMap()
    }
    
    /**
     * Update layout with new nodes/edges without full re-initialization.
     * New nodes are placed near related nodes.
     */
    fun updateIncremental(
        newNodes: List<GraphUINode>,
        newEdges: List<GraphUIEdge>
    ) {
        // Position new nodes near their connected nodes
        for (node in newNodes) {
            if (node.id in positions) continue
            
            // Find connected existing nodes
            val connectedIds = newEdges
                .filter { it.sourceId == node.id || it.targetId == node.id }
                .map { if (it.sourceId == node.id) it.targetId else it.sourceId }
            
            val connectedPositions = connectedIds.mapNotNull { positions[it] }
            
            val basePos = if (connectedPositions.isNotEmpty()) {
                // Average position of connected nodes
                val avgX = connectedPositions.map { it.x }.average().toFloat()
                val avgY = connectedPositions.map { it.y }.average().toFloat()
                Offset(avgX, avgY)
            } else {
                // Random position near center
                Offset(width / 2, height / 2)
            }
            
            // Add small random offset
            positions[node.id] = basePos + Offset(
                Random.nextFloat() * 50 - 25,
                Random.nextFloat() * 50 - 25
            )
            velocities[node.id] = Offset.Zero
        }
    }
}

/**
 * Represents a node in the graph UI.
 */
data class GraphUINode(
    val id: String,
    val label: String,
    val nodeType: String,
    val color: Long,
    val icon: String,
    val size: Float = 1f,  // 0-2 scale factor based on activation/importance
    val activation: Float = 0f
)

/**
 * Represents an edge in the graph UI.
 */
data class GraphUIEdge(
    val id: String,
    val sourceId: String,
    val targetId: String,
    val label: String,
    val weight: Float = 1f
)

// Extension functions
private fun Offset.normalize(): Offset {
    val len = getDistance()
    return if (len > 0) Offset(x / len, y / len) else Offset.Zero
}

private operator fun Offset.times(factor: Float): Offset = Offset(x * factor, y * factor)
