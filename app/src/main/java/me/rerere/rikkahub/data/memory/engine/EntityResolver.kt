package me.rerere.rikkahub.data.memory.engine

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.memory.dao.KnowledgeGraphDAO
import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.NodeType
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.VectorEngine

/**
 * Entity resolver for deduplication and merging of knowledge graph nodes.
 * 
 * Uses multiple signals to determine if two entities refer to the same thing:
 * - Exact label match (case-insensitive)
 * - Alias match
 * - Embedding similarity (semantic matching)
 * - Type compatibility
 */
class EntityResolver(
    private val dao: KnowledgeGraphDAO,
    private val embeddingService: EmbeddingService
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        const val DEFAULT_SIMILARITY_THRESHOLD = 0.85f
        const val LABEL_SIMILARITY_THRESHOLD = 0.9f
    }
    
    /**
     * Result of entity resolution.
     */
    sealed class ResolutionResult {
        data class Matched(val existingNode: MemoryNode, val similarity: Float) : ResolutionResult()
        data class PossibleMatch(val candidates: List<Pair<MemoryNode, Float>>) : ResolutionResult()
        object NoMatch : ResolutionResult()
    }
    
    /**
     * Resolve an extracted entity against existing nodes in the graph.
     * 
     * @param label The entity label to resolve
     * @param type The entity type
     * @param content The entity description/content
     * @param assistantId The assistant ID
     * @param threshold Minimum similarity for a match
     */
    suspend fun resolve(
        label: String,
        type: NodeType,
        content: String,
        assistantId: String,
        threshold: Float = DEFAULT_SIMILARITY_THRESHOLD
    ): ResolutionResult {
        val existingNodes = dao.getAllActiveNodes(assistantId)
        
        // 1. Exact label match (case-insensitive)
        existingNodes.find { 
            it.label.equals(label, ignoreCase = true) && it.nodeType == type
        }?.let { 
            return ResolutionResult.Matched(it, 1.0f) 
        }
        
        // 2. Alias match
        existingNodes.find { node ->
            try {
                val aliases = json.decodeFromString<List<String>>(node.aliases)
                aliases.any { it.equals(label, ignoreCase = true) }
            } catch (e: Exception) {
                false
            }
        }?.let {
            return ResolutionResult.Matched(it, 0.95f)
        }
        
        // 3. Fuzzy label match (allows for typos, abbreviations)
        existingNodes.filter { it.nodeType == type }
            .mapNotNull { node ->
                val similarity = calculateLabelSimilarity(label, node.label)
                if (similarity >= LABEL_SIMILARITY_THRESHOLD) {
                    node to similarity
                } else null
            }
            .maxByOrNull { it.second }
            ?.let { (node, similarity) ->
                return ResolutionResult.Matched(node, similarity)
            }
        
        // 4. Embedding similarity (semantic matching)
        val sameTypeNodes = existingNodes.filter { 
            it.nodeType == type && it.embedding != null 
        }
        
        if (sameTypeNodes.isEmpty()) {
            return ResolutionResult.NoMatch
        }
        
        try {
            val queryEmbedding = embeddingService.embed("$label: $content", assistantId)
            
            val candidates = sameTypeNodes.mapNotNull { node ->
                try {
                    val nodeEmbedding = json.decodeFromString<List<Float>>(node.embedding!!)
                    val similarity = VectorEngine.cosineSimilarity(queryEmbedding, nodeEmbedding)
                    if (similarity >= threshold * 0.9f) node to similarity else null
                } catch (e: Exception) {
                    null
                }
            }.sortedByDescending { it.second }
            
            return when {
                candidates.isEmpty() -> ResolutionResult.NoMatch
                candidates.first().second >= threshold -> {
                    ResolutionResult.Matched(candidates.first().first, candidates.first().second)
                }
                candidates.size == 1 && candidates.first().second >= threshold * 0.9f -> {
                    // Close enough, likely a match
                    ResolutionResult.Matched(candidates.first().first, candidates.first().second)
                }
                else -> ResolutionResult.PossibleMatch(candidates.take(3))
            }
        } catch (e: Exception) {
            return ResolutionResult.NoMatch
        }
    }
    
    /**
     * Calculate string similarity between two labels.
     * Uses Levenshtein distance normalized by length.
     */
    private fun calculateLabelSimilarity(label1: String, label2: String): Float {
        val s1 = label1.lowercase().trim()
        val s2 = label2.lowercase().trim()
        
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0f
        
        // Check if one contains the other
        if (s1.contains(s2) || s2.contains(s1)) {
            val ratio = minOf(s1.length, s2.length).toFloat() / maxOf(s1.length, s2.length)
            if (ratio >= 0.7f) return 0.9f * ratio
        }
        
        // Levenshtein distance
        val distance = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        return 1.0f - (distance.toFloat() / maxLen)
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // deletion
                    dp[i][j - 1] + 1,       // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }
    
    /**
     * Merge two nodes, keeping the more complete/confident one and updating aliases.
     */
    suspend fun mergeNodes(
        keepNode: MemoryNode,
        mergeNode: MemoryNode
    ): MemoryNode {
        // Combine aliases
        val keepAliases = try {
            json.decodeFromString<List<String>>(keepNode.aliases).toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
        
        val mergeAliases = try {
            json.decodeFromString<List<String>>(mergeNode.aliases)
        } catch (e: Exception) {
            emptyList()
        }
        
        // Add merge node's label and aliases
        keepAliases.add(mergeNode.label)
        keepAliases.addAll(mergeAliases)
        val uniqueAliases = keepAliases.distinct().filter { !it.equals(keepNode.label, ignoreCase = true) }
        
        // Combine content if merge has more info
        val combinedContent = if (mergeNode.content.length > keepNode.content.length) {
            mergeNode.content
        } else {
            keepNode.content
        }
        
        // Take higher confidence
        val combinedConfidence = maxOf(keepNode.confidence, mergeNode.confidence)
        
        // Update access count
        val combinedAccessCount = keepNode.accessCount + mergeNode.accessCount
        
        val updatedNode = keepNode.copy(
            aliases = json.encodeToString(ListSerializer(String.serializer()), uniqueAliases),
            content = combinedContent,
            confidence = combinedConfidence,
            accessCount = combinedAccessCount,
            lastAccessedAt = maxOf(keepNode.lastAccessedAt, mergeNode.lastAccessedAt)
        )
        
        dao.updateNode(updatedNode)
        
        // Re-point edges from merge node to keep node
        val mergeEdges = dao.getAllEdgesForNode(mergeNode.id)
        for (edge in mergeEdges) {
            val newEdge = when {
                edge.sourceId == mergeNode.id -> edge.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    sourceId = keepNode.id
                )
                edge.targetId == mergeNode.id -> edge.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    targetId = keepNode.id
                )
                else -> null
            }
            
            newEdge?.let { dao.insertEdge(it) }
        }
        
        // Delete merge node (cascade will delete its edges)
        dao.deleteNodeById(mergeNode.id)
        
        return updatedNode
    }
    
    /**
     * Find potential duplicate nodes in the graph.
     */
    suspend fun findPotentialDuplicates(
        assistantId: String,
        threshold: Float = 0.8f
    ): List<Pair<MemoryNode, MemoryNode>> {
        val nodes = dao.getAllActiveNodes(assistantId).filter { it.embedding != null }
        val duplicates = mutableListOf<Pair<MemoryNode, MemoryNode>>()
        
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val node1 = nodes[i]
                val node2 = nodes[j]
                
                // Only compare same type
                if (node1.nodeType != node2.nodeType) continue
                
                // Compare embeddings
                try {
                    val emb1 = json.decodeFromString<List<Float>>(node1.embedding!!)
                    val emb2 = json.decodeFromString<List<Float>>(node2.embedding!!)
                    val similarity = VectorEngine.cosineSimilarity(emb1, emb2)
                    
                    if (similarity >= threshold) {
                        duplicates.add(node1 to node2)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        }
        
        return duplicates
    }
}
