package me.rerere.rikkahub.data.memory.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.memory.entity.EdgeType
import me.rerere.rikkahub.data.memory.entity.MemoryEdge
import me.rerere.rikkahub.data.memory.entity.MemoryNode
import me.rerere.rikkahub.data.memory.entity.MemoryTier
import me.rerere.rikkahub.data.memory.entity.NodeType

/**
 * Data Access Object for the Knowledge Graph.
 * Provides graph-aware queries including traversal, spreading activation support,
 * and tier-based memory management.
 */
@Dao
interface KnowledgeGraphDAO {
    
    // ==================== NODE OPERATIONS ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: MemoryNode)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<MemoryNode>)
    
    @Update
    suspend fun updateNode(node: MemoryNode)
    
    @Delete
    suspend fun deleteNode(node: MemoryNode)
    
    @Query("DELETE FROM memory_nodes WHERE id = :nodeId")
    suspend fun deleteNodeById(nodeId: String)
    
    @Query("SELECT * FROM memory_nodes WHERE id = :nodeId")
    suspend fun getNodeById(nodeId: String): MemoryNode?
    
    @Query("SELECT * FROM memory_nodes WHERE assistant_id = :assistantId AND is_active = 1")
    suspend fun getAllActiveNodes(assistantId: String): List<MemoryNode>
    
    @Query("SELECT * FROM memory_nodes WHERE assistant_id = :assistantId AND is_active = 1")
    fun getAllActiveNodesFlow(assistantId: String): Flow<List<MemoryNode>>
    
    @Query("SELECT * FROM memory_nodes WHERE assistant_id = :assistantId AND node_type = :nodeType AND is_active = 1")
    suspend fun getNodesByType(assistantId: String, nodeType: NodeType): List<MemoryNode>
    
    @Query("SELECT * FROM memory_nodes WHERE assistant_id = :assistantId AND tier = :tier AND is_active = 1")
    suspend fun getNodesByTier(assistantId: String, tier: MemoryTier): List<MemoryNode>
    
    @Query("SELECT * FROM memory_nodes WHERE assistant_id = :assistantId AND tier = 'CORE' AND is_active = 1")
    suspend fun getCoreNodes(assistantId: String): List<MemoryNode>
    
    @Query("SELECT COUNT(*) FROM memory_nodes WHERE assistant_id = :assistantId AND is_active = 1")
    suspend fun getNodeCount(assistantId: String): Int
    
    @Query("SELECT COUNT(*) FROM memory_nodes WHERE assistant_id = :assistantId AND tier = :tier AND is_active = 1")
    suspend fun getNodeCountByTier(assistantId: String, tier: MemoryTier): Int
    
    // === ACTIVATION & ACCESS ===
    
    @Query("UPDATE memory_nodes SET last_accessed_at = :timestamp, access_count = access_count + 1 WHERE id = :nodeId")
    suspend fun recordAccess(nodeId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE memory_nodes SET tier = :tier WHERE id = :nodeId")
    suspend fun updateTier(nodeId: String, tier: MemoryTier)
    
    @Query("SELECT * FROM memory_nodes WHERE assistant_id = :assistantId AND is_active = 1 ORDER BY last_accessed_at DESC LIMIT :limit")
    suspend fun getRecentlyAccessedNodes(assistantId: String, limit: Int): List<MemoryNode>
    
    @Query("SELECT * FROM memory_nodes WHERE assistant_id = :assistantId AND is_active = 1 ORDER BY access_count DESC LIMIT :limit")
    suspend fun getMostAccessedNodes(assistantId: String, limit: Int): List<MemoryNode>
    
    // === PROSPECTIVE MEMORY (REMINDERS) ===
    
    @Query("""
        SELECT * FROM memory_nodes 
        WHERE assistant_id = :assistantId 
          AND node_type = 'REMINDER' 
          AND is_completed = 0 
          AND is_active = 1
        ORDER BY reminder_due_at ASC
    """)
    suspend fun getActiveReminders(assistantId: String): List<MemoryNode>
    
    @Query("""
        SELECT * FROM memory_nodes 
        WHERE assistant_id = :assistantId 
          AND node_type = 'REMINDER' 
          AND is_completed = 0 
          AND is_active = 1
          AND (reminder_due_at IS NULL OR reminder_due_at <= :currentTime)
    """)
    suspend fun getDueReminders(assistantId: String, currentTime: Long = System.currentTimeMillis()): List<MemoryNode>
    
    @Query("UPDATE memory_nodes SET is_completed = 1 WHERE id = :nodeId")
    suspend fun markReminderCompleted(nodeId: String)
    
    // === BELIEF REVISION ===
    
    @Query("UPDATE memory_nodes SET is_active = 0, superseded_by_id = :newNodeId WHERE id = :oldNodeId")
    suspend fun supersedeNode(oldNodeId: String, newNodeId: String)
    
    @Query("SELECT * FROM memory_nodes WHERE superseded_by_id = :nodeId")
    suspend fun getSupersededNodes(nodeId: String): List<MemoryNode>
    
    // === SEARCH ===
    
    @Query("SELECT * FROM memory_nodes WHERE assistant_id = :assistantId AND is_active = 1 AND label LIKE '%' || :query || '%'")
    suspend fun searchByLabel(assistantId: String, query: String): List<MemoryNode>
    
    @Query("SELECT * FROM memory_nodes WHERE assistant_id = :assistantId AND is_active = 1 AND LOWER(label) = LOWER(:label) AND node_type = :nodeType LIMIT 1")
    suspend fun findNodeByLabelExact(assistantId: String, label: String, nodeType: NodeType): MemoryNode?
    
    @Query("SELECT * FROM memory_nodes WHERE assistant_id = :assistantId AND is_active = 1 AND (label LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%')")
    suspend fun searchByLabelOrContent(assistantId: String, query: String): List<MemoryNode>
    
    // ==================== EDGE OPERATIONS ====================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdge(edge: MemoryEdge)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdges(edges: List<MemoryEdge>)
    
    @Update
    suspend fun updateEdge(edge: MemoryEdge)
    
    @Delete
    suspend fun deleteEdge(edge: MemoryEdge)
    
    @Query("DELETE FROM memory_edges WHERE id = :edgeId")
    suspend fun deleteEdgeById(edgeId: String)
    
    @Query("SELECT * FROM memory_edges WHERE id = :edgeId")
    suspend fun getEdgeById(edgeId: String): MemoryEdge?
    
    @Query("SELECT * FROM memory_edges WHERE assistant_id = :assistantId")
    suspend fun getAllEdges(assistantId: String): List<MemoryEdge>
    
    @Query("SELECT * FROM memory_edges WHERE assistant_id = :assistantId")
    fun getAllEdgesFlow(assistantId: String): Flow<List<MemoryEdge>>
    
    @Query("SELECT COUNT(*) FROM memory_edges WHERE assistant_id = :assistantId")
    suspend fun getEdgeCount(assistantId: String): Int
    
    // === GRAPH TRAVERSAL ===
    
    @Query("SELECT * FROM memory_edges WHERE source_id = :nodeId")
    suspend fun getOutgoingEdges(nodeId: String): List<MemoryEdge>
    
    @Query("SELECT * FROM memory_edges WHERE target_id = :nodeId")
    suspend fun getIncomingEdges(nodeId: String): List<MemoryEdge>
    
    @Query("SELECT * FROM memory_edges WHERE source_id = :nodeId OR target_id = :nodeId")
    suspend fun getAllEdgesForNode(nodeId: String): List<MemoryEdge>
    
    @Query("SELECT * FROM memory_edges WHERE source_id = :nodeId AND edge_type = :edgeType")
    suspend fun getOutgoingEdgesByType(nodeId: String, edgeType: EdgeType): List<MemoryEdge>
    
    @Query("SELECT * FROM memory_edges WHERE target_id = :nodeId AND edge_type = :edgeType")
    suspend fun getIncomingEdgesByType(nodeId: String, edgeType: EdgeType): List<MemoryEdge>
    
    @Query("""
        SELECT n.* FROM memory_nodes n
        INNER JOIN memory_edges e ON n.id = e.target_id
        WHERE e.source_id = :nodeId AND n.is_active = 1
    """)
    suspend fun getConnectedNodes(nodeId: String): List<MemoryNode>
    
    @Query("""
        SELECT n.* FROM memory_nodes n
        INNER JOIN memory_edges e ON n.id = e.target_id
        WHERE e.source_id = :nodeId AND e.edge_type = :edgeType AND n.is_active = 1
    """)
    suspend fun getConnectedNodesByEdgeType(nodeId: String, edgeType: EdgeType): List<MemoryNode>
    
    // === EDGE STRENGTH (for spreading activation) ===
    
    @Query("UPDATE memory_edges SET weight = weight + :boost WHERE id = :edgeId")
    suspend fun boostEdgeWeight(edgeId: String, boost: Float)
    
    @Query("UPDATE memory_edges SET last_accessed_at = :timestamp, access_count = access_count + 1 WHERE id = :edgeId")
    suspend fun recordEdgeAccess(edgeId: String, timestamp: Long = System.currentTimeMillis())
    
    // === CHECK EXISTING EDGES ===
    
    @Query("SELECT * FROM memory_edges WHERE source_id = :sourceId AND target_id = :targetId AND edge_type = :edgeType")
    suspend fun findEdge(sourceId: String, targetId: String, edgeType: EdgeType): MemoryEdge?
    
    @Query("SELECT EXISTS(SELECT 1 FROM memory_edges WHERE source_id = :sourceId AND target_id = :targetId)")
    suspend fun hasEdgeBetween(sourceId: String, targetId: String): Boolean
    
    // ==================== BULK OPERATIONS ====================
    
    @Query("DELETE FROM memory_nodes WHERE assistant_id = :assistantId")
    suspend fun deleteAllNodesForAssistant(assistantId: String)
    
    @Query("DELETE FROM memory_edges WHERE assistant_id = :assistantId")
    suspend fun deleteAllEdgesForAssistant(assistantId: String)
    
    @Transaction
    suspend fun deleteAllForAssistant(assistantId: String) {
        deleteAllEdgesForAssistant(assistantId)
        deleteAllNodesForAssistant(assistantId)
    }
    
    // ==================== STATISTICS ====================
    
    @Query("""
        SELECT node_type, COUNT(*) as count 
        FROM memory_nodes 
        WHERE assistant_id = :assistantId AND is_active = 1 
        GROUP BY node_type
    """)
    suspend fun getNodeCountsByType(assistantId: String): List<NodeTypeCount>
    
    @Query("""
        SELECT edge_type, COUNT(*) as count 
        FROM memory_edges 
        WHERE assistant_id = :assistantId 
        GROUP BY edge_type
    """)
    suspend fun getEdgeCountsByType(assistantId: String): List<EdgeTypeCount>
}

/**
 * Helper data class for node type statistics.
 */
data class NodeTypeCount(
    val node_type: NodeType,
    val count: Int
)

/**
 * Helper data class for edge type statistics.
 */
data class EdgeTypeCount(
    val edge_type: EdgeType,
    val count: Int
)
