package me.rerere.rikkahub.data.memory.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.memory.engine.KnowledgeExtractor
import me.rerere.rikkahub.data.memory.entity.EdgeType
import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.MemoryTier
import me.rerere.rikkahub.data.memory.entity.NodeType
import me.rerere.rikkahub.data.memory.entity.TemporalType
import me.rerere.rikkahub.data.memory.repository.KnowledgeGraphRepository

/**
 * Service for real-time memory extraction from conversations.
 * 
 * This is the main entry point for the memory system, bypassing the old
 * MemoryConsolidationWorker entirely. Memories are extracted and stored
 * immediately after each chat interaction.
 * 
 * Inspired by:
 * - Mem0: Atomic fact extraction and hybrid storage
 * - GraphRAG: Entity-relationship graphs for multi-hop reasoning
 * - Graphiti/Zep: Temporal awareness and belief revision
 */
class MemoryExtractionService(
    private val knowledgeExtractor: KnowledgeExtractor,
    private val knowledgeGraphRepository: KnowledgeGraphRepository
) {
    companion object {
        private const val TAG = "MemoryExtractionService"
        private const val MIN_MESSAGES_FOR_EXTRACTION = 2
    }
    
    /**
     * Extract and store memories from a conversation.
     * Called directly from ChatService after each message generation.
     * 
     * @param messages Recent conversation messages (last 6-10 recommended)
     * @param assistantId The assistant ID for scoping memories
     */
    suspend fun extractAndStore(
        messages: List<UIMessage>,
        assistantId: String
    ): ExtractionStats = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        if (messages.size < MIN_MESSAGES_FOR_EXTRACTION) {
            Log.d(TAG, "Not enough messages for extraction: ${messages.size}")
            return@withContext ExtractionStats.empty()
        }
        
        try {
            // Get existing entity labels for deduplication
            val existingNodes = knowledgeGraphRepository.getAllActiveNodes(assistantId)
            val existingLabels = existingNodes.map { it.label }
            
            Log.d(TAG, "Starting extraction for $assistantId with ${messages.size} messages")
            
            // Extract knowledge using the KnowledgeExtractor
            val result = knowledgeExtractor.extractFromConversation(
                messages = messages,
                assistantId = assistantId,
                existingEntityLabels = existingLabels
            )
            
            if (result.entities.isEmpty()) {
                Log.d(TAG, "No entities extracted from conversation")
                return@withContext ExtractionStats(
                    entitiesExtracted = 0,
                    entitiesStored = 0,
                    relationshipsExtracted = 0,
                    relationshipsStored = 0,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
            
            Log.d(TAG, "Extracted ${result.entities.size} entities, ${result.relationships.size} relationships")
            
            // Convert and store entities
            var entitiesStored = 0
            val entityIdMap = mutableMapOf<String, String>() // label -> nodeId
            
            for (entity in result.entities) {
                try {
                    val nodeType = parseNodeType(entity.type)
                    val temporalType = parseTemporalType(entity)
                    
                    val node = MemoryNode(
                        assistantId = assistantId,
                        nodeType = nodeType,
                        label = entity.label.take(100),
                        content = entity.description.take(500),
                        tier = MemoryTier.RECALL, // New memories start in RECALL
                        confidence = entity.confidence,
                        emotionalValence = entity.emotionalValence ?: 0f,
                        emotionalArousal = entity.emotionalArousal ?: 0.5f,
                        dominantEmotion = entity.dominantEmotion,
                        temporalType = temporalType,
                        createdAt = System.currentTimeMillis(),
                        lastAccessedAt = System.currentTimeMillis()
                    )
                    
                    val storedNode = knowledgeGraphRepository.insertNode(node)
                    entityIdMap[entity.label] = storedNode.id
                    entitiesStored++
                    
                    Log.d(TAG, "Stored entity: ${entity.label} (${nodeType.name})")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to store entity ${entity.label}: ${e.message}")
                }
            }
            
            // Store relationships
            var relationshipsStored = 0
            for (relationship in result.relationships) {
                try {
                    val sourceId = entityIdMap[relationship.sourceLabel] 
                        ?: findExistingNodeId(existingNodes, relationship.sourceLabel)
                    val targetId = entityIdMap[relationship.targetLabel]
                        ?: findExistingNodeId(existingNodes, relationship.targetLabel)
                    
                    if (sourceId != null && targetId != null) {
                        val edgeType = parseEdgeType(relationship.relationType)
                        
                        val edge = MemoryEdge(
                            assistantId = assistantId,
                            sourceId = sourceId,
                            targetId = targetId,
                            edgeType = edgeType,
                            weight = relationship.confidence,
                            createdAt = System.currentTimeMillis(),
                            lastAccessedAt = System.currentTimeMillis()
                        )
                        
                        knowledgeGraphRepository.insertEdge(edge)
                        relationshipsStored++
                        
                        Log.d(TAG, "Stored relationship: ${relationship.sourceLabel} -> ${relationship.targetLabel}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to store relationship: ${e.message}")
                }
            }
            
            val durationMs = System.currentTimeMillis() - startTime
            Log.d(TAG, "Extraction complete in ${durationMs}ms: $entitiesStored entities, $relationshipsStored relationships")
            
            ExtractionStats(
                entitiesExtracted = result.entities.size,
                entitiesStored = entitiesStored,
                relationshipsExtracted = result.relationships.size,
                relationshipsStored = relationshipsStored,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed: ${e.message}", e)
            ExtractionStats.empty()
        }
    }
    
    /**
     * Apply automatic tier management based on access patterns.
     * Promotes frequently accessed RECALL to CORE, demotes stale CORE to ARCHIVE.
     */
    suspend fun applyTierManagement(assistantId: String) = withContext(Dispatchers.IO) {
        try {
            knowledgeGraphRepository.applyTierChanges(assistantId)
            Log.d(TAG, "Applied tier management for $assistantId")
        } catch (e: Exception) {
            Log.e(TAG, "Tier management failed: ${e.message}")
        }
    }
    
    private fun parseNodeType(typeString: String): NodeType {
        return try {
            NodeType.valueOf(typeString.uppercase())
        } catch (e: IllegalArgumentException) {
            // Map common variations
            when (typeString.uppercase()) {
                "TOPIC", "SUBJECT" -> NodeType.CONCEPT
                "HABIT", "PATTERN" -> NodeType.ROUTINE
                "INTEREST", "LIKE", "DISLIKE" -> NodeType.PREFERENCE
                "OPINION", "THOUGHT" -> NodeType.BELIEF
                "TASK", "TODO", "OBJECTIVE" -> NodeType.GOAL
                "PLACE", "CITY", "COUNTRY", "LOCATION" -> NodeType.PLACE
                "FACT", "INFO" -> NodeType.CONCEPT
                else -> NodeType.CONCEPT
            }
        }
    }
    
    private fun parseTemporalType(entity: KnowledgeExtractor.ExtractedEntity): TemporalType {
        return when (entity.temporal?.lowercase()) {
            "past" -> TemporalType.INTERVAL  // Past events have a validity interval
            "present" -> TemporalType.ETERNAL  // Current facts are eternally valid (until revised)
            "future" -> TemporalType.POINT  // Future events are point-in-time
            "recurring" -> TemporalType.RECURRING
            else -> TemporalType.ETERNAL  // Default: assume currently valid
        }
    }
    
    private fun parseEdgeType(relationString: String): EdgeType {
        return try {
            EdgeType.valueOf(relationString.uppercase().replace(" ", "_"))
        } catch (e: IllegalArgumentException) {
            // Map common relationship patterns to actual EdgeType values
            val normalized = relationString.lowercase()
            when {
                normalized.contains("know") || normalized.contains("friend") -> EdgeType.KNOWS
                normalized.contains("like") || normalized.contains("love") -> EdgeType.LIKES
                normalized.contains("work") -> EdgeType.WORKS_WITH
                normalized.contains("live") || normalized.contains("reside") -> EdgeType.LOCATED_IN
                normalized.contains("own") || normalized.contains("have") -> EdgeType.BELONGS_TO
                normalized.contains("part") || normalized.contains("member") -> EdgeType.PART_OF
                normalized.contains("relat") || normalized.contains("connect") -> EdgeType.RELATED_TO
                normalized.contains("cause") || normalized.contains("result") -> EdgeType.CAUSES
                normalized.contains("before") -> EdgeType.BEFORE
                normalized.contains("after") -> EdgeType.AFTER
                normalized.contains("similar") -> EdgeType.SIMILAR_TO
                normalized.contains("oppos") || normalized.contains("conflict") -> EdgeType.OPPOSITE_OF
                else -> EdgeType.RELATED_TO
            }
        }
    }
    
    private fun findExistingNodeId(nodes: List<MemoryNode>, label: String): String? {
        return nodes.find { it.label.equals(label, ignoreCase = true) }?.id
    }
    
    data class ExtractionStats(
        val entitiesExtracted: Int,
        val entitiesStored: Int,
        val relationshipsExtracted: Int,
        val relationshipsStored: Int,
        val durationMs: Long
    ) {
        companion object {
            fun empty() = ExtractionStats(0, 0, 0, 0, 0)
        }
    }
}
