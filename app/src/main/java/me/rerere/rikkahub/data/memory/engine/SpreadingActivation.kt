package me.rerere.rikkahub.data.memory.engine

import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.MemoryNode

/**
 * Spreading activation engine for associative memory retrieval.
 * 
 * When retrieving memories, activation spreads from query-matched nodes
 * to connected nodes in the knowledge graph. This mimics how human memory
 * works - thinking of "coffee" activates related concepts like "morning",
 * "café", "energy", etc.
 * 
 * Based on cognitive psychology's spreading activation theory:
 * - Concepts are nodes in a semantic network
 * - When a concept is activated, activation spreads to related concepts
 * - Strength of spreading depends on connection weight
 * - Activation decays with distance (number of hops)
 */
object SpreadingActivation {
    
    // Default parameters
    const val DEFAULT_DECAY_FACTOR = 0.5f   // Activation halves per hop
    const val DEFAULT_MAX_DEPTH = 2         // Maximum hops to spread
    const val DEFAULT_MIN_ACTIVATION = 0.1f // Stop spreading below this
    
    /**
     * Result of spreading activation, containing final activation levels
     * for all reached nodes.
     */
    data class ActivationResult(
        val activations: Map<String, Float>,
        val depth: Map<String, Int>,        // How many hops from seed
        val paths: Map<String, List<String>> // Path taken to reach node
    )
    
    /**
     * Spread activation through the knowledge graph starting from seed nodes.
     * 
     * @param seedActivations Initial activation levels for seed nodes (nodeId -> activation)
     * @param edges All edges in the graph for this assistant
     * @param nodes All nodes for quick lookup (nodeId -> node)
     * @param maxDepth Maximum number of hops to spread
     * @param decayFactor How much activation decays per hop (0-1)
     * @param minActivation Stop spreading when activation falls below this
     * @return ActivationResult with final activation levels
     */
    fun spread(
        seedActivations: Map<String, Float>,
        edges: List<MemoryEdge>,
        nodes: Map<String, MemoryNode>,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        decayFactor: Float = DEFAULT_DECAY_FACTOR,
        minActivation: Float = DEFAULT_MIN_ACTIVATION
    ): ActivationResult {
        // Build adjacency map for efficient traversal
        val adjacency = buildAdjacencyMap(edges)
        
        // Initialize activation, depth, and path tracking
        val activations = seedActivations.toMutableMap()
        val depths = seedActivations.keys.associateWith { 0 }.toMutableMap()
        val paths = seedActivations.keys.associateWith { listOf(it) }.toMutableMap()
        
        // BFS frontier
        var frontier = seedActivations.keys.toSet()
        
        repeat(maxDepth) { depth ->
            val nextFrontier = mutableSetOf<String>()
            val currentDecay = decayFactor.pow(depth + 1)
            
            for (nodeId in frontier) {
                val sourceActivation = activations[nodeId] ?: continue
                val neighbors = adjacency[nodeId] ?: continue
                
                for ((neighborId, edge) in neighbors) {
                    // Skip inactive nodes
                    val neighborNode = nodes[neighborId]
                    if (neighborNode == null || !neighborNode.isActive) continue
                    
                    // Calculate spread amount
                    val spreadAmount = sourceActivation * currentDecay * edge.weight * edge.confidence
                    
                    if (spreadAmount < minActivation) continue
                    
                    // Accumulate activation (can receive from multiple paths)
                    val currentNeighborActivation = activations[neighborId] ?: 0f
                    activations[neighborId] = currentNeighborActivation + spreadAmount
                    
                    // Track depth (keep shortest path)
                    if (neighborId !in depths) {
                        depths[neighborId] = depth + 1
                        paths[neighborId] = (paths[nodeId] ?: emptyList()) + neighborId
                    }
                    
                    nextFrontier.add(neighborId)
                }
            }
            frontier = nextFrontier
        }
        
        // Add base-level activation contribution from each node
        val finalActivations = activations.mapValues { (nodeId, spreadActivation) ->
            val node = nodes[nodeId] ?: return@mapValues spreadActivation
            spreadActivation + ActivationEngine.calculateActivation(node)
        }
        
        return ActivationResult(
            activations = finalActivations,
            depth = depths,
            paths = paths
        )
    }
    
    /**
     * Build an adjacency map from edges for efficient graph traversal.
     * Maps nodeId -> list of (neighborId, edge) pairs.
     */
    private fun buildAdjacencyMap(edges: List<MemoryEdge>): Map<String, List<Pair<String, MemoryEdge>>> {
        val adjacency = mutableMapOf<String, MutableList<Pair<String, MemoryEdge>>>()
        
        for (edge in edges) {
            // Add forward direction
            adjacency.getOrPut(edge.sourceId) { mutableListOf() }.add(edge.targetId to edge)
            
            // Add reverse direction for undirected edges
            if (!edge.edgeType.isDirected) {
                adjacency.getOrPut(edge.targetId) { mutableListOf() }.add(edge.sourceId to edge)
            }
        }
        
        return adjacency
    }
    
    /**
     * Spread activation with emotional context.
     * Nodes with similar emotional valence get a boost.
     */
    fun spreadWithEmotion(
        seedActivations: Map<String, Float>,
        edges: List<MemoryEdge>,
        nodes: Map<String, MemoryNode>,
        currentEmotionalValence: Float,
        currentEmotionalArousal: Float? = null,
        emotionWeight: Float = 0.3f,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        decayFactor: Float = DEFAULT_DECAY_FACTOR,
        minActivation: Float = DEFAULT_MIN_ACTIVATION
    ): ActivationResult {
        val baseResult = spread(seedActivations, edges, nodes, maxDepth, decayFactor, minActivation)
        
        // Add emotional boost
        val emotionalActivations = baseResult.activations.mapValues { (nodeId, activation) ->
            val node = nodes[nodeId] ?: return@mapValues activation
            
            val valenceDiff = kotlin.math.abs(node.emotionalValence - currentEmotionalValence)
            val arousalDiff = if (currentEmotionalArousal != null) {
                kotlin.math.abs(node.emotionalArousal - currentEmotionalArousal)
            } else 0f
            
            val emotionalSimilarity = 1f - (valenceDiff + arousalDiff) / 2f
            val emotionalBoost = emotionalSimilarity * emotionWeight
            
            activation + emotionalBoost
        }
        
        return baseResult.copy(activations = emotionalActivations)
    }
    
    /**
     * Find the most relevant nodes given seed activations.
     * Returns nodes sorted by final activation level.
     */
    fun findMostRelevant(
        seedActivations: Map<String, Float>,
        edges: List<MemoryEdge>,
        nodes: Map<String, MemoryNode>,
        limit: Int = 10,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        decayFactor: Float = DEFAULT_DECAY_FACTOR
    ): List<Pair<MemoryNode, Float>> {
        val result = spread(seedActivations, edges, nodes, maxDepth, decayFactor)
        
        return result.activations
            .mapNotNull { (nodeId, activation) ->
                nodes[nodeId]?.let { it to activation }
            }
            .sortedByDescending { it.second }
            .take(limit)
    }
    
    /**
     * Find nodes that are frequently co-activated.
     * Useful for consolidation (strengthening connections).
     */
    fun findCoActivatedNodes(
        activationHistory: List<Set<String>>,
        minCoOccurrence: Int = 3
    ): List<Pair<String, String>> {
        val coOccurrences = mutableMapOf<Pair<String, String>, Int>()
        
        for (activeSet in activationHistory) {
            val nodeList = activeSet.toList()
            for (i in nodeList.indices) {
                for (j in i + 1 until nodeList.size) {
                    val pair = if (nodeList[i] < nodeList[j]) {
                        nodeList[i] to nodeList[j]
                    } else {
                        nodeList[j] to nodeList[i]
                    }
                    coOccurrences[pair] = (coOccurrences[pair] ?: 0) + 1
                }
            }
        }
        
        return coOccurrences
            .filter { it.value >= minCoOccurrence }
            .map { it.key }
    }
    
    // Extension function for Float power
    private fun Float.pow(exponent: Int): Float {
        var result = 1f
        repeat(exponent) { result *= this }
        return result
    }
}
