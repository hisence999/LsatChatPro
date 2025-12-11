package me.rerere.rikkahub.data.memory.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.memory.dao.KnowledgeGraphDAO
import me.rerere.rikkahub.data.memory.engine.ActivationEngine
import me.rerere.rikkahub.data.memory.engine.SpreadingActivation
import me.rerere.rikkahub.data.memory.entity.EdgeType
import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.MemorySource
import me.rerere.rikkahub.data.memory.entity.MemoryTier
import me.rerere.rikkahub.data.memory.entity.NodeType
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.VectorEngine

/**
 * Repository for the Knowledge Graph.
 * Provides high-level operations combining DAO access with activation logic,
 * embedding generation, and graph-based retrieval.
 */
class KnowledgeGraphRepository(
    private val dao: KnowledgeGraphDAO,
    private val embeddingService: EmbeddingService
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    // ==================== NODE OPERATIONS ====================
    
    suspend fun insertNode(node: MemoryNode): MemoryNode {
        // Check for existing node with same label and type (prevent duplicates)
        val existingNode = dao.findNodeByLabelExact(node.assistantId, node.label, node.nodeType)
        if (existingNode != null) {
            // Update existing node content instead of creating duplicate
            val updatedNode = existingNode.copy(
                content = "${existingNode.content}\n\n${node.content}".trimEnd(),
                lastAccessedAt = System.currentTimeMillis(),
                accessCount = existingNode.accessCount + 1
            )
            dao.updateNode(updatedNode)
            return updatedNode
        }
        
        // Generate embedding if content is provided
        val nodeWithEmbedding = if (node.embedding == null && node.content.isNotBlank()) {
            try {
                val embeddingResult = embeddingService.embedWithModelId(
                    "${node.label}: ${node.content}", 
                    node.assistantId
                )
                val embedding = embeddingResult.embeddings.firstOrNull()
                node.copy(
                    embedding = embedding?.let { json.encodeToString(it.toList()) },
                    embeddingModelId = embeddingResult.modelId
                )
            } catch (e: Exception) {
                node // Keep without embedding if generation fails
            }
        } else {
            node
        }
        
        dao.insertNode(nodeWithEmbedding)
        return nodeWithEmbedding
    }
    
    suspend fun insertNodes(nodes: List<MemoryNode>) {
        dao.insertNodes(nodes)
    }
    
    suspend fun updateNode(node: MemoryNode) {
        dao.updateNode(node)
    }
    
    suspend fun getNodeById(nodeId: String): MemoryNode? = dao.getNodeById(nodeId)
    
    suspend fun getAllActiveNodes(assistantId: String): List<MemoryNode> = 
        dao.getAllActiveNodes(assistantId)
    
    fun getAllActiveNodesFlow(assistantId: String): Flow<List<MemoryNode>> = 
        dao.getAllActiveNodesFlow(assistantId)
    
    suspend fun getNodesByType(assistantId: String, nodeType: NodeType): List<MemoryNode> =
        dao.getNodesByType(assistantId, nodeType)
    
    suspend fun getCoreNodes(assistantId: String): List<MemoryNode> = 
        dao.getCoreNodes(assistantId)
    
    suspend fun getNodesByTier(assistantId: String, tier: MemoryTier): List<MemoryNode> =
        dao.getNodesByTier(assistantId, tier)
    
    suspend fun deleteNode(nodeId: String) {
        dao.deleteNodeById(nodeId)
    }
    
    // ==================== EDGE OPERATIONS ====================
    
    suspend fun insertEdge(edge: MemoryEdge) {
        // Check for duplicate edge
        val existing = dao.findEdge(edge.sourceId, edge.targetId, edge.edgeType)
        if (existing != null) {
            // Boost weight of existing edge
            dao.boostEdgeWeight(existing.id, 0.1f)
            dao.recordEdgeAccess(existing.id)
        } else {
            dao.insertEdge(edge)
        }
    }
    
    suspend fun insertEdges(edges: List<MemoryEdge>) {
        dao.insertEdges(edges)
    }
    
    suspend fun getAllEdges(assistantId: String): List<MemoryEdge> = 
        dao.getAllEdges(assistantId)
    
    fun getAllEdgesFlow(assistantId: String): Flow<List<MemoryEdge>> = 
        dao.getAllEdgesFlow(assistantId)
    
    suspend fun getConnectedNodes(nodeId: String): List<MemoryNode> =
        dao.getConnectedNodes(nodeId)
    
    suspend fun getEdgesForNode(nodeId: String): List<MemoryEdge> =
        dao.getAllEdgesForNode(nodeId)
    
    // ==================== ACTIVATION & RETRIEVAL ====================
    
    /**
     * Record that a node was accessed, updating activation.
     */
    suspend fun recordAccess(nodeId: String) {
        dao.recordAccess(nodeId)
        
        // Check if tier should change
        val node = dao.getNodeById(nodeId) ?: return
        val suggestedTier = ActivationEngine.suggestTierChange(node)
        if (suggestedTier != null && suggestedTier != node.tier) {
            dao.updateTier(nodeId, suggestedTier)
        }
    }
    
    /**
     * Semantic search: find nodes similar to a query.
     * Uses embedding similarity + base-level activation.
     */
    suspend fun semanticSearch(
        query: String,
        assistantId: String,
        limit: Int = 10,
        minSimilarity: Float = 0.5f,
        includeTiers: Set<MemoryTier> = setOf(MemoryTier.CORE, MemoryTier.RECALL)
    ): List<Pair<MemoryNode, Float>> {
        val queryEmbedding = try {
            embeddingService.embed(query, assistantId)
        } catch (e: Exception) {
            return emptyList()
        }
        
        val nodes = dao.getAllActiveNodes(assistantId)
            .filter { it.tier in includeTiers && it.embedding != null }
        
        return nodes.mapNotNull { node ->
            val embedding = try {
                json.decodeFromString<List<Float>>(node.embedding!!)
            } catch (e: Exception) {
                return@mapNotNull null
            }
            
            val similarity = VectorEngine.cosineSimilarity(queryEmbedding, embedding)
            if (similarity < minSimilarity) return@mapNotNull null
            
            // Combine similarity with activation
            val activation = ActivationEngine.calculateActivation(node)
            val combinedScore = similarity * 0.7f + (activation.coerceIn(-1f, 5f) / 5f) * 0.3f
            
            node to combinedScore
        }
        .sortedByDescending { it.second }
        .take(limit)
    }
    
    /**
     * Graph-based retrieval using spreading activation.
     * Starts from semantically similar nodes and spreads to related nodes.
     */
    suspend fun graphRetrieval(
        query: String,
        assistantId: String,
        limit: Int = 10,
        maxDepth: Int = 2,
        minSimilarity: Float = 0.5f
    ): List<Pair<MemoryNode, Float>> {
        // Step 1: Find seed nodes via semantic search
        val seedResults = semanticSearch(query, assistantId, limit = 5, minSimilarity = minSimilarity)
        if (seedResults.isEmpty()) return emptyList()
        
        val seedActivations = seedResults.associate { it.first.id to it.second }
        
        // Step 2: Load graph
        val edges = dao.getAllEdges(assistantId)
        val nodes = dao.getAllActiveNodes(assistantId).associateBy { it.id }
        
        // Step 3: Spread activation
        val spreadResult = SpreadingActivation.spread(
            seedActivations = seedActivations,
            edges = edges,
            nodes = nodes,
            maxDepth = maxDepth
        )
        
        // Step 4: Return ranked results
        return spreadResult.activations
            .mapNotNull { (nodeId, activation) ->
                nodes[nodeId]?.let { it to activation }
            }
            .sortedByDescending { it.second }
            .take(limit)
    }
    
    /**
     * Retrieve memories with emotional context matching.
     */
    suspend fun retrieveWithEmotion(
        query: String,
        assistantId: String,
        emotionalValence: Float,
        emotionalArousal: Float? = null,
        limit: Int = 10
    ): List<Pair<MemoryNode, Float>> {
        val seedResults = semanticSearch(query, assistantId, limit = 5)
        if (seedResults.isEmpty()) return emptyList()
        
        val seedActivations = seedResults.associate { it.first.id to it.second }
        val edges = dao.getAllEdges(assistantId)
        val nodes = dao.getAllActiveNodes(assistantId).associateBy { it.id }
        
        val spreadResult = SpreadingActivation.spreadWithEmotion(
            seedActivations = seedActivations,
            edges = edges,
            nodes = nodes,
            currentEmotionalValence = emotionalValence,
            currentEmotionalArousal = emotionalArousal
        )
        
        return spreadResult.activations
            .mapNotNull { (nodeId, activation) -> nodes[nodeId]?.let { it to activation } }
            .sortedByDescending { it.second }
            .take(limit)
    }
    
    // ==================== TIER MANAGEMENT ====================
    
    suspend fun promoteToCore(nodeId: String) {
        dao.updateTier(nodeId, MemoryTier.CORE)
    }
    
    suspend fun demoteFromCore(nodeId: String) {
        dao.updateTier(nodeId, MemoryTier.RECALL)
    }
    
    suspend fun archiveNode(nodeId: String) {
        dao.updateTier(nodeId, MemoryTier.ARCHIVAL)
    }
    
    /**
     * Apply tier changes based on activation levels.
     * Called during consolidation.
     */
    suspend fun applyTierChanges(assistantId: String): Int {
        var changesApplied = 0
        val nodes = dao.getAllActiveNodes(assistantId).filter { it.tier != MemoryTier.CORE }
        
        for (node in nodes) {
            val suggestedTier = ActivationEngine.suggestTierChange(node)
            if (suggestedTier != null && suggestedTier != node.tier) {
                dao.updateTier(node.id, suggestedTier)
                changesApplied++
            }
        }
        
        return changesApplied
    }
    
    // ==================== PROSPECTIVE MEMORY (REMINDERS) ====================
    
    suspend fun getActiveReminders(assistantId: String): List<MemoryNode> =
        dao.getActiveReminders(assistantId)
    
    suspend fun getDueReminders(assistantId: String): List<MemoryNode> =
        dao.getDueReminders(assistantId)
    
    suspend fun completeReminder(nodeId: String) {
        dao.markReminderCompleted(nodeId)
    }
    
    suspend fun createReminder(
        assistantId: String,
        label: String,
        content: String,
        dueAt: Long? = null,
        triggerCondition: String? = null
    ): MemoryNode {
        val reminder = MemoryNode(
            assistantId = assistantId,
            nodeType = NodeType.REMINDER,
            label = label,
            content = content,
            tier = MemoryTier.CORE, // Reminders are always CORE
            source = MemorySource.INFERRED,
            reminderDueAt = dueAt,
            triggerCondition = triggerCondition
        )
        return insertNode(reminder)
    }
    
    // ==================== BELIEF REVISION ====================
    
    /**
     * Update a belief/fact, keeping history via superseding.
     */
    suspend fun reviseNode(
        oldNodeId: String,
        newContent: String,
        newConfidence: Float = 1.0f,
        sourceConversationId: String? = null
    ): MemoryNode? {
        val oldNode = dao.getNodeById(oldNodeId) ?: return null
        
        // Create new version
        val newNode = oldNode.copy(
            id = java.util.UUID.randomUUID().toString(),
            content = newContent,
            confidence = newConfidence,
            version = oldNode.version + 1,
            source = MemorySource.INFERRED,
            sourceConversationId = sourceConversationId,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            accessCount = 1,
            embedding = null // Will be regenerated
        )
        
        val insertedNode = insertNode(newNode)
        
        // Mark old as superseded
        dao.supersedeNode(oldNodeId, newNode.id)
        
        // Create supersedes edge
        insertEdge(MemoryEdge(
            assistantId = oldNode.assistantId,
            sourceId = newNode.id,
            targetId = oldNodeId,
            edgeType = EdgeType.SUPERSEDES
        ))
        
        return insertedNode
    }
    
    // ==================== STATISTICS ====================
    
    suspend fun getNodeCount(assistantId: String): Int = dao.getNodeCount(assistantId)
    suspend fun getEdgeCount(assistantId: String): Int = dao.getEdgeCount(assistantId)
    suspend fun getNodeCountByTier(assistantId: String, tier: MemoryTier): Int = 
        dao.getNodeCountByTier(assistantId, tier)
    
    data class GraphStats(
        val totalNodes: Int,
        val totalEdges: Int,
        val coreCount: Int,
        val recallCount: Int,
        val archivalCount: Int,
        val nodesByType: Map<NodeType, Int>,
        val edgesByType: Map<EdgeType, Int>
    )
    
    suspend fun getGraphStats(assistantId: String): GraphStats {
        return GraphStats(
            totalNodes = dao.getNodeCount(assistantId),
            totalEdges = dao.getEdgeCount(assistantId),
            coreCount = dao.getNodeCountByTier(assistantId, MemoryTier.CORE),
            recallCount = dao.getNodeCountByTier(assistantId, MemoryTier.RECALL),
            archivalCount = dao.getNodeCountByTier(assistantId, MemoryTier.ARCHIVAL),
            nodesByType = dao.getNodeCountsByType(assistantId).associate { it.node_type to it.count },
            edgesByType = dao.getEdgeCountsByType(assistantId).associate { it.edge_type to it.count }
        )
    }
    
    // ==================== BULK OPERATIONS ====================
    
    suspend fun deleteAllForAssistant(assistantId: String) {
        dao.deleteAllForAssistant(assistantId)
    }
    
    /**
     * Alias for deleteAllForAssistant (used by ViewModel).
     */
    suspend fun clearAllForAssistant(assistantId: String) {
        deleteAllForAssistant(assistantId)
    }
}
