package me.rerere.rikkahub.data.memory.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Edge categories based on Semantic Spacetime's four fundamental relations.
 * These categories organize the relationship types into logical groups.
 */
enum class EdgeCategory {
    SIMILARITY,     // Semantic proximity (similar_to, same_as)
    CAUSAL,         // Cause-effect relationships
    CONTAINMENT,    // Part-whole, membership
    PROPERTY,       // Attributes and types
    SOCIAL,         // Interpersonal relationships
    PREFERENCE,     // User attitudes and opinions
    TEMPORAL,       // Time-based relationships
    ACTION          // User-entity interactions
}

/**
 * Relationship types for knowledge graph edges.
 * Based on Semantic Spacetime with extensions for personal AI use.
 */
enum class EdgeType(val category: EdgeCategory, val displayName: String, val isDirected: Boolean = true) {
    // === SIMILARITY (semantic proximity) ===
    SIMILAR_TO(EdgeCategory.SIMILARITY, "similar to", false),
    SAME_AS(EdgeCategory.SIMILARITY, "same as", false),      // Entity resolution
    OPPOSITE_OF(EdgeCategory.SIMILARITY, "opposite of", false),
    RELATED_TO(EdgeCategory.SIMILARITY, "related to", false),
    
    // === CAUSAL (cause-effect chains) ===
    CAUSES(EdgeCategory.CAUSAL, "causes"),
    CAUSED_BY(EdgeCategory.CAUSAL, "caused by"),
    ENABLES(EdgeCategory.CAUSAL, "enables"),
    PREVENTS(EdgeCategory.CAUSAL, "prevents"),
    TRIGGERS(EdgeCategory.CAUSAL, "triggers"),
    RESULTS_IN(EdgeCategory.CAUSAL, "results in"),
    
    // === CONTAINMENT (part-whole) ===
    PART_OF(EdgeCategory.CONTAINMENT, "part of"),
    CONTAINS(EdgeCategory.CONTAINMENT, "contains"),
    MEMBER_OF(EdgeCategory.CONTAINMENT, "member of"),
    LOCATED_IN(EdgeCategory.CONTAINMENT, "located in"),
    BELONGS_TO(EdgeCategory.CONTAINMENT, "belongs to"),
    
    // === PROPERTY (attributes) ===
    HAS_PROPERTY(EdgeCategory.PROPERTY, "has property"),
    IS_A(EdgeCategory.PROPERTY, "is a"),                    // Type/category
    DESCRIBES(EdgeCategory.PROPERTY, "describes"),
    
    // === SOCIAL (interpersonal) ===
    KNOWS(EdgeCategory.SOCIAL, "knows"),
    FRIEND_OF(EdgeCategory.SOCIAL, "friend of"),
    FAMILY_OF(EdgeCategory.SOCIAL, "family of"),
    WORKS_WITH(EdgeCategory.SOCIAL, "works with"),
    REPORTS_TO(EdgeCategory.SOCIAL, "reports to"),
    MARRIED_TO(EdgeCategory.SOCIAL, "married to"),
    CHILD_OF(EdgeCategory.SOCIAL, "child of"),
    PARENT_OF(EdgeCategory.SOCIAL, "parent of"),
    
    // === PREFERENCE (user attitudes) ===
    LIKES(EdgeCategory.PREFERENCE, "likes"),
    DISLIKES(EdgeCategory.PREFERENCE, "dislikes"),
    PREFERS_OVER(EdgeCategory.PREFERENCE, "prefers over"),
    INTERESTED_IN(EdgeCategory.PREFERENCE, "interested in"),
    AVOIDS(EdgeCategory.PREFERENCE, "avoids"),
    
    // === TEMPORAL (time-based) ===
    BEFORE(EdgeCategory.TEMPORAL, "before"),
    AFTER(EdgeCategory.TEMPORAL, "after"),
    DURING(EdgeCategory.TEMPORAL, "during"),
    SUPERSEDES(EdgeCategory.TEMPORAL, "supersedes"),        // Belief revision
    FOLLOWS(EdgeCategory.TEMPORAL, "follows"),
    PRECEDES(EdgeCategory.TEMPORAL, "precedes"),
    
    // === ACTION (user-entity interaction) ===
    DOES(EdgeCategory.ACTION, "does"),
    USES(EdgeCategory.ACTION, "uses"),
    CREATED(EdgeCategory.ACTION, "created"),
    EXPERIENCED(EdgeCategory.ACTION, "experienced"),
    ATTENDED(EdgeCategory.ACTION, "attended"),
    LEARNED(EdgeCategory.ACTION, "learned"),
    MENTIONED(EdgeCategory.ACTION, "mentioned"),
}

/**
 * An edge in the knowledge graph representing a relationship between two nodes.
 * 
 * Edges carry:
 * - Semantic meaning (type)
 * - Strength (weight for spreading activation)
 * - Confidence (certainty of the relationship)
 * - Access history (for activation calculation)
 */
@Entity(
    tableName = "memory_edges",
    foreignKeys = [
        ForeignKey(
            entity = MemoryNode::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryNode::class,
            parentColumns = ["id"],
            childColumns = ["target_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("source_id"),
        Index("target_id"),
        Index("assistant_id"),
        Index("edge_type")
    ]
)
data class MemoryEdge(
    @PrimaryKey 
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "assistant_id")
    val assistantId: String,
    
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    
    @ColumnInfo(name = "target_id")
    val targetId: String,
    
    @ColumnInfo(name = "edge_type")
    val edgeType: EdgeType,
    
    // === EDGE STRENGTH (for spreading activation) ===
    @ColumnInfo(name = "weight", defaultValue = "1.0")
    val weight: Float = 1.0f,               // Higher = stronger connection
    
    @ColumnInfo(name = "confidence", defaultValue = "1.0")
    val confidence: Float = 1.0f,           // How certain is this relationship?
    
    // === ACTIVATION HISTORY ===
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "access_count", defaultValue = "1")
    val accessCount: Int = 1,
    
    // === SOURCE TRACKING ===
    @ColumnInfo(name = "source_conversation_id")
    val sourceConversationId: String? = null,
    
    // === METADATA ===
    @ColumnInfo(name = "metadata")
    val metadata: String? = null,           // Additional JSON data if needed
)
