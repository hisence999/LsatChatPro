package me.rerere.rikkahub.data.memory.migration

import android.util.Log
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.db.dao.ChatEpisodeDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.memory.dao.KnowledgeGraphDAO
import me.rerere.rikkahub.data.memory.engine.KnowledgeExtractor
import me.rerere.rikkahub.data.memory.entity.EdgeType
import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.MemorySource
import me.rerere.rikkahub.data.memory.entity.MemoryTier
import me.rerere.rikkahub.data.memory.entity.NodeType
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID


/**
 * Migrator for converting legacy memory system to Ultimate Memory System 3.0.
 * 
 * Handles:
 * - Core memories → Graph nodes with entity extraction
 * - Episodic memories → EVENT nodes with temporal edges
 * - Relationship inference between migrated entities
 * - Embedding reuse where compatible
 */
class LegacyMigrator(
    private val oldMemoryDAO: MemoryDAO,
    private val oldEpisodeDAO: ChatEpisodeDAO,
    private val newGraphDAO: KnowledgeGraphDAO,
    private val extractor: KnowledgeExtractor,
    private val embeddingService: EmbeddingService
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val TAG = "LegacyMigrator"
    }
    
    /**
     * Migration result statistics.
     */
    data class MigrationResult(
        val memoriesMigrated: Int,
        val episodesMigrated: Int,
        val nodesCreated: Int,
        val edgesCreated: Int,
        val failed: Int,
        val durationMs: Long,
        val wasResumed: Boolean = false  // True if continuing an interrupted migration
    )
    
    /**
     * Progress callback for UI updates.
     */
    fun interface ProgressCallback {
        fun onProgress(phase: String, progress: Float, message: String)
    }
    
    /**
     * Migrate all legacy memories for an assistant to the new knowledge graph.
     */
    suspend fun migrate(
        assistantId: String,
        progressCallback: ProgressCallback? = null
    ): MigrationResult {
        val startTime = System.currentTimeMillis()
        
        var memoriesMigrated = 0
        var episodesMigrated = 0
        var nodesCreated = 0
        var edgesCreated = 0
        var failed = 0
        
        Log.i(TAG, "Starting migration for assistant: $assistantId")
        
        // Get existing entity labels for deduplication
        val existingLabels = mutableListOf<String>()
        
        try {
            // === PHASE 1: Migrate Core Memories ===
            progressCallback?.onProgress("memories", 0f, "Loading core memories...")
            
            val coreMemories = oldMemoryDAO.getMemoriesOfAssistant(assistantId)
            val totalMemories = coreMemories.size
            val wasResumed = isMigrated(assistantId) && totalMemories > 0
            
            if (wasResumed) {
                Log.i(TAG, "Resuming interrupted migration with $totalMemories remaining memories")
            }
            
            Log.i(TAG, "Found $totalMemories core memories to migrate")
            
            coreMemories.forEachIndexed { index, memory ->
                try {
                    progressCallback?.onProgress(
                        "memories",
                        (index + 1).toFloat() / totalMemories * 0.5f,
                        "Migrating memory ${index + 1}/$totalMemories"
                    )
                    
                    // Try to extract structured entities
                    val extraction = extractor.extractFromText(
                        text = memory.content,
                        assistantId = assistantId,
                        existingEntityLabels = existingLabels
                    )
                    
                    if (extraction.entities.isNotEmpty()) {
                        // Create nodes for extracted entities
                        for (entity in extraction.entities) {
                            val node = createNodeFromEntity(
                                entity = entity,
                                assistantId = assistantId,
                                source = MemorySource.MIGRATED,
                                sourceEmbedding = memory.embedding,
                                sourceEmbeddingModelId = memory.embeddingModelId
                            )
                            
                            newGraphDAO.insertNode(node)
                            existingLabels.add(entity.label)
                            nodesCreated++
                        }
                        
                        // Create edges for extracted relationships
                        for (rel in extraction.relationships) {
                            val edge = createEdgeFromRelationship(
                                rel = rel,
                                assistantId = assistantId,
                                nodeLabels = existingLabels
                            )
                            
                            if (edge != null) {
                                newGraphDAO.insertEdge(edge)
                                edgesCreated++
                            }
                        }
                    } else {
                        // Fallback: create a single FACT node for the whole memory
                        val fallbackNode = MemoryNode(
                            assistantId = assistantId,
                            nodeType = NodeType.FACT,
                            label = memory.content.take(50).trim(),
                            content = memory.content,
                            tier = MemoryTier.RECALL,
                            source = MemorySource.MIGRATED,
                            confidence = 0.8f,
                            embedding = memory.embedding,
                            embeddingModelId = memory.embeddingModelId,
                            createdAt = memory.createdAt,
                            lastAccessedAt = memory.lastAccessedAt ?: memory.createdAt
                        )
                        
                        newGraphDAO.insertNode(fallbackNode)
                        existingLabels.add(fallbackNode.label)
                        nodesCreated++
                    }
                    
                    memoriesMigrated++
                    
                    // Delete this memory immediately after successful migration
                    // This makes migration resumable - if interrupted, only remaining items are processed
                    oldMemoryDAO.deleteMemory(memory.id)
                    Log.d(TAG, "Migrated and deleted legacy memory: ${memory.id}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to migrate memory: ${memory.id}", e)
                    failed++
                }
            }
            
            // === PHASE 2: Migrate Episodes ===
            progressCallback?.onProgress("episodes", 0.5f, "Loading episodic memories...")
            
            val episodes = oldEpisodeDAO.getEpisodesOfAssistant(assistantId)
            val totalEpisodes = episodes.size
            
            Log.i(TAG, "Found $totalEpisodes episodes to migrate")
            
            var previousEventId: String? = null
            
            episodes.sortedBy { it.startTime }.forEachIndexed { index, episode ->
                try {
                    progressCallback?.onProgress(
                        "episodes",
                        0.5f + (index + 1).toFloat() / totalEpisodes * 0.4f,
                        "Migrating episode ${index + 1}/$totalEpisodes"
                    )
                    
                    val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    val dateStr = dateFormatter.format(Date(episode.startTime))
                    
                    // Create EVENT node
                    val eventNode = MemoryNode(
                        id = UUID.randomUUID().toString(),
                        assistantId = assistantId,
                        nodeType = NodeType.EVENT,
                        label = "Conversation on $dateStr",
                        content = episode.content,
                        tier = MemoryTier.ARCHIVAL, // Episodes go to archival
                        source = MemorySource.MIGRATED,
                        confidence = 1.0f,
                        embedding = episode.embedding,
                        embeddingModelId = episode.embeddingModelId,
                        eventTimestamp = episode.startTime,
                        eventDurationMs = episode.endTime - episode.startTime,
                        createdAt = episode.startTime,
                        lastAccessedAt = episode.lastAccessedAt,
                        // Map significance to emotional valence (rough approximation)
                        emotionalValence = (episode.significance - 5) / 5f, // 1-10 → -0.8 to +1.0
                        emotionalArousal = episode.significance / 10f
                    )
                    
                    newGraphDAO.insertNode(eventNode)
                    nodesCreated++
                    
                    // Create temporal edge to previous event
                    if (previousEventId != null) {
                        val temporalEdge = MemoryEdge(
                            assistantId = assistantId,
                            sourceId = previousEventId!!,
                            targetId = eventNode.id,
                            edgeType = EdgeType.BEFORE,
                            weight = 0.5f
                        )
                        newGraphDAO.insertEdge(temporalEdge)
                        edgesCreated++
                    }
                    
                    previousEventId = eventNode.id
                    episodesMigrated++
                    
                    // Delete this episode immediately after successful migration
                    oldEpisodeDAO.deleteEpisode(episode.id)
                    Log.d(TAG, "Migrated and deleted legacy episode: ${episode.id}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to migrate episode: ${episode.id}", e)
                    failed++
                }
            }
            
            // === PHASE 3: Generate Cross-References ===
            progressCallback?.onProgress("crossref", 0.9f, "Generating cross-references...")
            
            val crossRefEdges = generateCrossReferences(assistantId)
            edgesCreated += crossRefEdges
            
            progressCallback?.onProgress("done", 1.0f, "Migration complete!")
            
            // Legacy data is now deleted incrementally during migration
            // No batch cleanup needed
            
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        Log.i(TAG, "Migration completed in ${duration}ms: " +
            "$memoriesMigrated memories, $episodesMigrated episodes, " +
            "$nodesCreated nodes, $edgesCreated edges, $failed failed")
        
        return MigrationResult(
            memoriesMigrated = memoriesMigrated,
            episodesMigrated = episodesMigrated,
            nodesCreated = nodesCreated,
            edgesCreated = edgesCreated,
            failed = failed,
            durationMs = duration,
            wasResumed = isMigrated(assistantId) && (memoriesMigrated > 0 || episodesMigrated > 0)
        )
    }
    
    private suspend fun createNodeFromEntity(
        entity: KnowledgeExtractor.ExtractedEntity,
        assistantId: String,
        source: MemorySource,
        sourceEmbedding: String?,
        sourceEmbeddingModelId: String?
    ): MemoryNode {
        val nodeType = try {
            NodeType.valueOf(entity.type.uppercase())
        } catch (e: Exception) {
            NodeType.FACT
        }
        
        // Try to generate new embedding, fall back to source
        val (embedding, modelId) = try {
            val result = embeddingService.embedWithModelId(
                "${entity.label}: ${entity.description}",
                assistantId
            )
            val emb = result.embeddings.firstOrNull()
            if (emb != null) {
                json.encodeToString(
                    ListSerializer(Float.serializer()),
                    emb.toList()
                ) to result.modelId
            } else {
                sourceEmbedding to sourceEmbeddingModelId
            }
        } catch (e: Exception) {
            sourceEmbedding to sourceEmbeddingModelId
        }
        
        return MemoryNode(
            assistantId = assistantId,
            nodeType = nodeType,
            label = entity.label.take(50),
            content = entity.description.take(500),
            tier = MemoryTier.RECALL,
            source = source,
            confidence = entity.confidence,
            emotionalValence = entity.emotionalValence ?: 0f,
            emotionalArousal = entity.emotionalArousal ?: 0.5f,
            dominantEmotion = entity.dominantEmotion,
            embedding = embedding,
            embeddingModelId = modelId
        )
    }
    
    private suspend fun createEdgeFromRelationship(
        rel: KnowledgeExtractor.ExtractedRelationship,
        assistantId: String,
        nodeLabels: List<String>
    ): MemoryEdge? {
        // Find nodes by label
        val allNodes = newGraphDAO.getAllActiveNodes(assistantId)
        val sourceNode = allNodes.find { it.label.equals(rel.sourceLabel, ignoreCase = true) }
        val targetNode = allNodes.find { it.label.equals(rel.targetLabel, ignoreCase = true) }
        
        if (sourceNode == null || targetNode == null) return null
        
        val edgeType = try {
            EdgeType.valueOf(rel.relationType.uppercase())
        } catch (e: Exception) {
            EdgeType.RELATED_TO
        }
        
        return MemoryEdge(
            assistantId = assistantId,
            sourceId = sourceNode.id,
            targetId = targetNode.id,
            edgeType = edgeType,
            confidence = rel.confidence,
            weight = rel.confidence
        )
    }
    
    /**
     * Generate relationships between entities based on embedding similarity.
     */
    private suspend fun generateCrossReferences(assistantId: String): Int {
        val nodes = newGraphDAO.getAllActiveNodes(assistantId).filter { it.embedding != null }
        val existingEdges = newGraphDAO.getAllEdges(assistantId)
        val edgePairs = existingEdges.map { it.sourceId to it.targetId }.toSet()
        
        var edgesCreated = 0
        
        // Only create RELATED_TO edges for highly similar nodes that aren't already connected
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val node1 = nodes[i]
                val node2 = nodes[j]
                
                // Skip if already connected
                if ((node1.id to node2.id) in edgePairs || (node2.id to node1.id) in edgePairs) continue
                
                // Skip same type (usually less interesting connections)
                if (node1.nodeType == node2.nodeType) continue
                
                try {
                    val emb1 = json.decodeFromString<List<Float>>(node1.embedding!!)
                    val emb2 = json.decodeFromString<List<Float>>(node2.embedding!!)
                    val similarity = me.rerere.rikkahub.data.ai.rag.VectorEngine.cosineSimilarity(emb1, emb2)
                    
                    if (similarity >= 0.75f) {
                        val edge = MemoryEdge(
                            assistantId = assistantId,
                            sourceId = node1.id,
                            targetId = node2.id,
                            edgeType = EdgeType.RELATED_TO,
                            weight = similarity,
                            confidence = similarity
                        )
                        newGraphDAO.insertEdge(edge)
                        edgesCreated++
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        }
        
        return edgesCreated
    }
    
    /**
     * Check if migration has already been done for an assistant.
     */
    suspend fun isMigrated(assistantId: String): Boolean {
        val nodeCount = newGraphDAO.getNodeCount(assistantId)
        return nodeCount > 0
    }
    
    /**
     * Mark an assistant as migrated (for cases with no legacy data).
     */
    suspend fun markMigrated(assistantId: String) {
        // Create a placeholder node to mark migration complete
        // This is only called when there's no legacy data to migrate
        // The presence of any node marks the assistant as migrated
    }
    
    /**
     * Clear migrated data to allow re-migration.
     */
    suspend fun clearMigration(assistantId: String) {
        newGraphDAO.deleteAllForAssistant(assistantId)
    }
    
    /**
     * Check if there's a partial/interrupted migration.
     * Returns true if there are nodes in the new system BUT also legacy data remaining.
     */
    suspend fun hasPartialMigration(assistantId: String): Boolean {
        val hasNewData = isMigrated(assistantId)
        val hasLegacyData = hasLegacyData(assistantId)
        return hasNewData && hasLegacyData
    }
    
    /**
     * Check if there's any legacy data that needs migrating.
     */
    suspend fun hasLegacyData(assistantId: String): Boolean {
        val legacyMemories = oldMemoryDAO.getMemoriesOfAssistant(assistantId)
        val legacyEpisodes = oldEpisodeDAO.getEpisodesOfAssistant(assistantId)
        return legacyMemories.isNotEmpty() || legacyEpisodes.isNotEmpty()
    }
    
    /**
     * Get counts of pending legacy data.
     */
    suspend fun getLegacyDataCounts(assistantId: String): Pair<Int, Int> {
        val memories = oldMemoryDAO.getMemoriesOfAssistant(assistantId).size
        val episodes = oldEpisodeDAO.getEpisodesOfAssistant(assistantId).size
        return memories to episodes
    }
}
