package me.rerere.rikkahub.data.memory.engine

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.memory.dao.KnowledgeGraphDAO
import me.rerere.rikkahub.data.memory.entity.EdgeType
import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.MemorySource
import me.rerere.rikkahub.data.memory.entity.MemoryTier
import me.rerere.rikkahub.data.memory.entity.NodeType
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.VectorEngine



/**
 * Sleep-inspired memory consolidation engine.
 * 
 * Performs periodic maintenance on the knowledge graph:
 * - REPLAY: Re-activate important memories to strengthen them
 * - STRENGTHEN: Boost edges between frequently co-accessed nodes  
 * - PRUNE: Move low-activation nodes to archival tier
 * - GENERALIZE: Find patterns across events and create higher-level concepts
 * - RESOLVE: Handle conflicting beliefs via recency/confidence
 * 
 * Based on research on memory consolidation during sleep in biological and
 * artificial neural networks.
 */
class MemoryConsolidationEngine(
    private val dao: KnowledgeGraphDAO,
    private val embeddingService: EmbeddingService,
    private val providerManager: ProviderManager,
    private val settingsStore: SettingsStore
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val TAG = "ConsolidationEngine"
        
        // Thresholds
        const val PRUNE_THRESHOLD = -2.0f           // Archive nodes below this activation
        const val GENERALIZE_MIN_EVENTS = 3         // Need 3+ similar events to generalize
        const val REPLAY_BATCH_SIZE = 20            // How many recent nodes to replay
        const val CO_ACCESS_THRESHOLD = 3           // Min co-accesses to boost edge
        const val SIMILARITY_CLUSTER_THRESHOLD = 0.75f
        const val CONFLICT_DETECTION_THRESHOLD = 0.85f
    }
    
    /**
     * Result of a consolidation cycle.
     */
    data class ConsolidationResult(
        val nodesReplayed: Int,
        val edgesStrengthened: Int,
        val nodesArchived: Int,
        val nodesPromoted: Int,
        val patternsDiscovered: Int,
        val conflictsResolved: Int,
        val duplicatesMerged: Int
    )
    
    @Serializable
    data class GeneralizationResult(
        val label: String,
        val description: String,
        val confidence: Float
    )
    
    /**
     * Run a full consolidation cycle.
     * This should be run periodically (e.g., nightly or when app is idle).
     */
    suspend fun runConsolidationCycle(assistantId: String): ConsolidationResult {
        Log.i(TAG, "Starting consolidation cycle for assistant: $assistantId")
        
        var nodesReplayed = 0
        var edgesStrengthened = 0
        var nodesArchived = 0
        var nodesPromoted = 0
        var patternsDiscovered = 0
        var conflictsResolved = 0
        var duplicatesMerged = 0
        
        try {
            // === PHASE 1: REPLAY ===
            nodesReplayed = replayRecentMemories(assistantId)
            Log.i(TAG, "Phase 1 (Replay): $nodesReplayed nodes replayed")
            
            // === PHASE 2: STRENGTHEN ===
            edgesStrengthened = strengthenCoAccessedEdges(assistantId)
            Log.i(TAG, "Phase 2 (Strengthen): $edgesStrengthened edges strengthened")
            
            // === PHASE 3: PRUNE ===
            val pruneResult = pruneAndPromoteNodes(assistantId)
            nodesArchived = pruneResult.first
            nodesPromoted = pruneResult.second
            Log.i(TAG, "Phase 3 (Prune): $nodesArchived archived, $nodesPromoted promoted")
            
            // === PHASE 4: GENERALIZE ===
            patternsDiscovered = discoverPatterns(assistantId)
            Log.i(TAG, "Phase 4 (Generalize): $patternsDiscovered patterns discovered")
            
            // === PHASE 5: RESOLVE CONFLICTS ===
            conflictsResolved = resolveConflicts(assistantId)
            Log.i(TAG, "Phase 5 (Resolve): $conflictsResolved conflicts resolved")
            
            // === PHASE 6: DEDUPLICATE ===
            duplicatesMerged = deduplicateNodes(assistantId)
            Log.i(TAG, "Phase 6 (Dedupe): $duplicatesMerged duplicates merged")
            
        } catch (e: Exception) {
            Log.e(TAG, "Consolidation cycle failed", e)
        }
        
        return ConsolidationResult(
            nodesReplayed = nodesReplayed,
            edgesStrengthened = edgesStrengthened,
            nodesArchived = nodesArchived,
            nodesPromoted = nodesPromoted,
            patternsDiscovered = patternsDiscovered,
            conflictsResolved = conflictsResolved,
            duplicatesMerged = duplicatesMerged
        )
    }
    
    // ==================== PHASE 1: REPLAY ====================
    
    /**
     * Re-activate recent and important memories to strengthen them.
     * Mimics hippocampal replay during sleep.
     */
    private suspend fun replayRecentMemories(assistantId: String): Int {
        val recentNodes = dao.getRecentlyAccessedNodes(assistantId, REPLAY_BATCH_SIZE)
        
        for (node in recentNodes) {
            // "Replay" = increment access count (without changing lastAccessedAt to now)
            // This strengthens the memory without updating recency
            dao.recordAccess(node.id)
        }
        
        return recentNodes.size
    }
    
    // ==================== PHASE 2: STRENGTHEN ====================
    
    /**
     * Boost edge weights between nodes that are frequently activated together.
     */
    private suspend fun strengthenCoAccessedEdges(assistantId: String): Int {
        val edges = dao.getAllEdges(assistantId)
        val nodes = dao.getAllActiveNodes(assistantId).associateBy { it.id }
        
        var strengthened = 0
        
        for (edge in edges) {
            val sourceNode = nodes[edge.sourceId] ?: continue
            val targetNode = nodes[edge.targetId] ?: continue
            
            // If both nodes have been accessed multiple times recently, boost the edge
            val recentSource = (System.currentTimeMillis() - sourceNode.lastAccessedAt) < 7 * 24 * 60 * 60 * 1000
            val recentTarget = (System.currentTimeMillis() - targetNode.lastAccessedAt) < 7 * 24 * 60 * 60 * 1000
            
            if (recentSource && recentTarget) {
                val minAccess = minOf(sourceNode.accessCount, targetNode.accessCount)
                if (minAccess >= CO_ACCESS_THRESHOLD) {
                    // Boost edge weight proportionally
                    val boost = 0.05f * minOf(minAccess - CO_ACCESS_THRESHOLD + 1, 5)
                    dao.boostEdgeWeight(edge.id, boost)
                    strengthened++
                }
            }
        }
        
        return strengthened
    }
    
    // ==================== PHASE 3: PRUNE ====================
    
    /**
     * Move low-activation nodes to archival, promote high-activation to higher tiers.
     */
    private suspend fun pruneAndPromoteNodes(assistantId: String): Pair<Int, Int> {
        val nodes = dao.getAllActiveNodes(assistantId).filter { it.tier != MemoryTier.CORE }
        
        var archived = 0
        var promoted = 0
        
        for (node in nodes) {
            val suggestedTier = ActivationEngine.suggestTierChange(node)
            
            if (suggestedTier != null && suggestedTier != node.tier) {
                dao.updateTier(node.id, suggestedTier)
                
                when (suggestedTier) {
                    MemoryTier.ARCHIVAL -> archived++
                    MemoryTier.RECALL, MemoryTier.CORE -> promoted++
                }
            }
        }
        
        return archived to promoted
    }
    
    // ==================== PHASE 4: GENERALIZE ====================
    
    /**
     * Find patterns across similar events and create higher-level abstractions.
     */
    private suspend fun discoverPatterns(assistantId: String): Int {
        val events = dao.getNodesByType(assistantId, NodeType.EVENT)
        if (events.size < GENERALIZE_MIN_EVENTS) return 0
        
        // Cluster similar events
        val clusters = clusterSimilarNodes(events)
        var patternsCreated = 0
        
        for (cluster in clusters) {
            if (cluster.size < GENERALIZE_MIN_EVENTS) continue
            
            // Check if a pattern for this cluster already exists
            val existingRoutines = dao.getNodesByType(assistantId, NodeType.ROUTINE)
            val clusterEmbedding = computeClusterEmbedding(cluster)
            
            val alreadyExists = existingRoutines.any { routine ->
                routine.embedding?.let { embJson ->
                    try {
                        val emb = json.decodeFromString<List<Float>>(embJson)
                        VectorEngine.cosineSimilarity(clusterEmbedding, emb) > 0.85f
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
            }
            
            if (alreadyExists) continue
            
            // Generate a generalization using LLM
            val generalization = generateGeneralization(cluster, assistantId) ?: continue
            
            if (generalization.confidence < 0.6f) continue
            
            // Create ROUTINE node
            val patternNode = MemoryNode(
                assistantId = assistantId,
                nodeType = NodeType.ROUTINE,
                label = generalization.label,
                content = generalization.description,
                confidence = generalization.confidence,
                source = MemorySource.CONSOLIDATED,
                tier = MemoryTier.RECALL
            )
            
            // Generate embedding
            val embeddingResult = try {
                embeddingService.embedWithModelId(
                    "${patternNode.label}: ${patternNode.content}",
                    assistantId
                )
            } catch (e: Exception) {
                null
            }
            
            val patternWithEmbedding = embeddingResult?.let {
                patternNode.copy(
                    embedding = json.encodeToString(ListSerializer(Float.serializer()), it.embeddings.first().toList()),
                    embeddingModelId = it.modelId
                )
            } ?: patternNode
            
            dao.insertNode(patternWithEmbedding)
            
            // Link cluster events to pattern
            for (event in cluster) {
                dao.insertEdge(MemoryEdge(
                    assistantId = assistantId,
                    sourceId = event.id,
                    targetId = patternWithEmbedding.id,
                    edgeType = EdgeType.PART_OF
                ))
            }
            
            patternsCreated++
        }
        
        return patternsCreated
    }
    
    private fun clusterSimilarNodes(nodes: List<MemoryNode>): List<List<MemoryNode>> {
        if (nodes.isEmpty()) return emptyList()
        
        val clusters = mutableListOf<MutableList<MemoryNode>>()
        val assigned = mutableSetOf<String>()
        
        for (node in nodes) {
            if (node.id in assigned || node.embedding == null) continue
            
            val cluster = mutableListOf(node)
            assigned.add(node.id)
            
            val nodeEmb = try {
                json.decodeFromString<List<Float>>(node.embedding)
            } catch (e: Exception) {
                continue
            }
            
            for (other in nodes) {
                if (other.id in assigned || other.embedding == null) continue
                
                val otherEmb = try {
                    json.decodeFromString<List<Float>>(other.embedding)
                } catch (e: Exception) {
                    continue
                }
                
                val similarity = VectorEngine.cosineSimilarity(nodeEmb, otherEmb)
                if (similarity >= SIMILARITY_CLUSTER_THRESHOLD) {
                    cluster.add(other)
                    assigned.add(other.id)
                }
            }
            
            clusters.add(cluster)
        }
        
        return clusters
    }
    
    private fun computeClusterEmbedding(cluster: List<MemoryNode>): List<Float> {
        val embeddings = cluster.mapNotNull { node ->
            node.embedding?.let {
                try {
                    json.decodeFromString<List<Float>>(it)
                } catch (e: Exception) {
                    null
                }
            }
        }
        
        if (embeddings.isEmpty()) return emptyList()
        
        val dim = embeddings.first().size
        val avgEmbedding = MutableList(dim) { 0f }
        
        for (emb in embeddings) {
            for (i in 0 until dim) {
                avgEmbedding[i] += emb[i] / embeddings.size
            }
        }
        
        return avgEmbedding
    }
    
    private suspend fun generateGeneralization(
        cluster: List<MemoryNode>,
        assistantId: String
    ): GeneralizationResult? {
        val eventsText = cluster.take(5).joinToString("\n") { "- ${it.label}: ${it.content}" }
        
        val prompt = """
Analyze these similar events and identify a recurring pattern or routine:

$eventsText

If there is a clear pattern, return JSON with:
- label: Short name for the pattern (e.g., "Morning coffee ritual")
- description: Brief description of the routine
- confidence: 0.0-1.0 how confident you are this is a real pattern

Return ONLY valid JSON. If no clear pattern, return: {"label":"","description":"","confidence":0.0}
""".trimIndent()
        
        return try {
            val settings = settingsStore.settingsFlow.value
            val assistant = settings.assistants.find { it.id.toString() == assistantId } ?: return null
            // Use background model or fallback to global chat model
            val modelId = assistant.backgroundModelId ?: assistant.chatModelId ?: settings.chatModelId
            val model = settings.findModelById(modelId) ?: return null
            val providerSetting = model.findProvider(settings.providers) ?: return null
            val provider = providerManager.getProviderByType(providerSetting)
            
            val response = provider.generateText(
                providerSetting = providerSetting,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(model = model, temperature = 0.3f)
            )
            
            val text = response.choices.firstOrNull()?.message?.toContentText() ?: return null
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) return null
            
            json.decodeFromString<GeneralizationResult>(text.substring(jsonStart, jsonEnd + 1))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate generalization", e)
            null
        }
    }
    
    // ==================== PHASE 5: RESOLVE CONFLICTS ====================
    
    /**
     * Find and resolve conflicting beliefs.
     */
    private suspend fun resolveConflicts(assistantId: String): Int {
        val facts = dao.getNodesByType(assistantId, NodeType.FACT) +
                   dao.getNodesByType(assistantId, NodeType.PREFERENCE) +
                   dao.getNodesByType(assistantId, NodeType.BELIEF)
        
        if (facts.size < 2) return 0
        
        var resolved = 0
        val processed = mutableSetOf<String>()
        
        for (i in facts.indices) {
            if (facts[i].id in processed) continue
            
            for (j in i + 1 until facts.size) {
                if (facts[j].id in processed) continue
                
                // Check if nodes are similar (potentially conflicting)
                val node1 = facts[i]
                val node2 = facts[j]
                
                if (node1.embedding == null || node2.embedding == null) continue
                if (node1.nodeType != node2.nodeType) continue
                
                try {
                    val emb1 = json.decodeFromString<List<Float>>(node1.embedding)
                    val emb2 = json.decodeFromString<List<Float>>(node2.embedding)
                    val similarity = VectorEngine.cosineSimilarity(emb1, emb2)
                    
                    if (similarity >= CONFLICT_DETECTION_THRESHOLD) {
                        // Potential conflict - keep the more recent/confident one
                        val (older, newer) = if (node1.createdAt < node2.createdAt) {
                            node1 to node2
                        } else {
                            node2 to node1
                        }
                        
                        // If newer has reasonable confidence, supersede older
                        if (newer.confidence >= older.confidence * 0.8f) {
                            dao.supersedeNode(older.id, newer.id)
                            dao.insertEdge(MemoryEdge(
                                assistantId = assistantId,
                                sourceId = newer.id,
                                targetId = older.id,
                                edgeType = EdgeType.SUPERSEDES
                            ))
                            processed.add(older.id)
                            resolved++
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        }
        
        return resolved
    }
    
    // ==================== PHASE 6: DEDUPLICATE ====================
    
    /**
     * Find and merge duplicate nodes.
     */
    private suspend fun deduplicateNodes(assistantId: String): Int {
        val entityResolver = EntityResolver(dao, embeddingService)
        val duplicates = entityResolver.findPotentialDuplicates(assistantId, threshold = 0.90f)
        
        var merged = 0
        val alreadyMerged = mutableSetOf<String>()
        
        for ((node1, node2) in duplicates) {
            if (node1.id in alreadyMerged || node2.id in alreadyMerged) continue
            
            // Keep the one with more accesses or higher confidence
            val keepScore1 = node1.accessCount * 0.5f + node1.confidence
            val keepScore2 = node2.accessCount * 0.5f + node2.confidence
            
            val (keep, merge) = if (keepScore1 >= keepScore2) node1 to node2 else node2 to node1
            
            entityResolver.mergeNodes(keep, merge)
            alreadyMerged.add(merge.id)
            merged++
        }
        
        return merged
    }
}
