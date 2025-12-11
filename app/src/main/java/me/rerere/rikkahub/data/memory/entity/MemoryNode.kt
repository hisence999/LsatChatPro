package me.rerere.rikkahub.data.memory.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Node types in the knowledge graph - representing different categories of knowledge.
 * Each type has an icon for visualization and a color for the mind map UI.
 */
enum class NodeType(val icon: String, val color: Long) {
    // === ENTITIES ===
    PERSON("👤", 0xFF4A90D9),           // People the user knows
    PLACE("📍", 0xFF27AE60),            // Locations
    ORGANIZATION("🏢", 0xFFF39C12),     // Companies, groups, institutions
    OBJECT("📦", 0xFF9B59B6),           // Physical things
    
    // === ABSTRACT ===
    CONCEPT("💡", 0xFFE74C3C),          // Ideas, topics, themes
    SKILL("🎯", 0xFF1ABC9C),            // Abilities, competencies
    INTEREST("❤️", 0xFFFF6B6B),         // Hobbies, passions
    
    // === USER-SPECIFIC ===
    FACT("📋", 0xFF3498DB),             // Factual statement about user
    PREFERENCE("⭐", 0xFFFFD93D),       // Likes/dislikes
    BELIEF("🧠", 0xFFAD8B73),           // Opinions, values, worldview
    GOAL("🎯", 0xFF00D4AA),             // Aspirations, objectives
    
    // === TEMPORAL ===
    EVENT("📅", 0xFFFF9F43),            // Past occurrences
    ROUTINE("🔄", 0xFF778BEB),          // Recurring patterns
    REMINDER("⏰", 0xFFFF4757),         // Future intentions (prospective memory)
    
    // === EMOTIONAL ===
    EMOTION("😊", 0xFFFF6B81),          // Emotional states/associations
}

/**
 * Memory storage tiers - inspired by MemGPT architecture.
 * Determines how accessible and persistent a memory is.
 */
enum class MemoryTier {
    CORE,       // Always in context, never forgotten (~10 items max)
    RECALL,     // Retrievable via semantic search, subject to decay
    ARCHIVAL    // Long-term storage, needs explicit retrieval, compressed
}

/**
 * Source of the memory - tracks provenance for confidence scoring.
 */
enum class MemorySource {
    EXPLICIT,       // User explicitly stated this
    INFERRED,       // AI inferred from conversation
    CONSOLIDATED,   // Created during sleep consolidation
    MIGRATED        // Migrated from legacy memory system
}

/**
 * Temporal type for memories - enables time-aware retrieval and belief revision.
 * Inspired by Graphiti/Zep temporal knowledge graphs.
 */
enum class TemporalType {
    POINT,      // Happened at specific moment
    INTERVAL,   // Valid during a time range (valid_from to valid_until)
    RECURRING,  // Repeats on a pattern (e.g., "every Monday")
    ETERNAL     // Always true, no temporal bounds (e.g., "User's name is Julia")
}

/**
 * A node in the knowledge graph representing a discrete piece of knowledge.
 * 
 * Implements ACT-R-inspired activation dynamics:
 * - Base-level activation from recency and frequency
 * - Emotional valence for mood-based retrieval
 * - Confidence scoring for belief revision
 * - Tier-based storage for memory management
 */
@Entity(
    tableName = "memory_nodes",
    indices = [
        Index("assistant_id"),
        Index("node_type"),
        Index("tier"),
        Index("is_active")
    ]
)
data class MemoryNode(
    @PrimaryKey 
    val id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "assistant_id")
    val assistantId: String,
    
    // === CORE IDENTITY ===
    @ColumnInfo(name = "node_type")
    val nodeType: NodeType,
    
    @ColumnInfo(name = "label")
    val label: String,                      // Short display name (e.g., "Alice")
    
    @ColumnInfo(name = "content")
    val content: String,                    // Full description
    
    @ColumnInfo(name = "aliases", defaultValue = "[]")
    val aliases: String = "[]",             // JSON array of alternate names
    
    // === EMBEDDINGS ===
    @ColumnInfo(name = "embedding")
    val embedding: String? = null,          // JSON-encoded float array
    
    @ColumnInfo(name = "embedding_model_id")
    val embeddingModelId: String? = null,
    
    // === ACT-R BASE-LEVEL ACTIVATION ===
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_accessed_at")
    val lastAccessedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "access_count", defaultValue = "1")
    val accessCount: Int = 1,               // n in activation formula
    
    @ColumnInfo(name = "decay_rate", defaultValue = "0.5")
    val decayRate: Float = 0.5f,            // d in activation formula (typical ACT-R value)
    
    // === MEMORY TIERS ===
    @ColumnInfo(name = "tier", defaultValue = "RECALL")
    val tier: MemoryTier = MemoryTier.RECALL,
    
    // === EMOTIONAL VALENCE ===
    @ColumnInfo(name = "emotional_valence", defaultValue = "0")
    val emotionalValence: Float = 0f,       // -1 (negative) to +1 (positive)
    
    @ColumnInfo(name = "emotional_arousal", defaultValue = "0.5")
    val emotionalArousal: Float = 0.5f,     // 0 (calm) to 1 (intense)
    
    @ColumnInfo(name = "dominant_emotion")
    val dominantEmotion: String? = null,    // "happy", "anxious", "excited", etc.
    
    // === CONFIDENCE & SOURCE ===
    @ColumnInfo(name = "confidence", defaultValue = "1.0")
    val confidence: Float = 1.0f,           // 0-1, how certain are we?
    
    @ColumnInfo(name = "source", defaultValue = "INFERRED")
    val source: MemorySource = MemorySource.INFERRED,
    
    @ColumnInfo(name = "source_conversation_id")
    val sourceConversationId: String? = null,
    
    @ColumnInfo(name = "source_message_id")
    val sourceMessageId: String? = null,
    
    // === TEMPORAL (for EVENT nodes) ===
    @ColumnInfo(name = "event_timestamp")
    val eventTimestamp: Long? = null,       // When the event occurred
    
    @ColumnInfo(name = "event_duration_ms")
    val eventDurationMs: Long? = null,      // Duration in milliseconds
    
    // === TEMPORAL VALIDITY (belief revision) ===
    @ColumnInfo(name = "temporal_type", defaultValue = "ETERNAL")
    val temporalType: TemporalType = TemporalType.ETERNAL,
    
    @ColumnInfo(name = "valid_from")
    val validFrom: Long? = null,            // When this belief became true
    
    @ColumnInfo(name = "valid_until")
    val validUntil: Long? = null,           // When this belief stopped being true
    
    @ColumnInfo(name = "recurrence_pattern")
    val recurrencePattern: String? = null,  // "DAILY", "WEEKLY:MON,WED", "MONTHLY:15"
    
    // === PROSPECTIVE MEMORY (for REMINDER nodes) ===
    @ColumnInfo(name = "trigger_condition")
    val triggerCondition: String? = null,   // JSON trigger specification
    
    @ColumnInfo(name = "is_completed", defaultValue = "0")
    val isCompleted: Boolean = false,       // For reminders
    
    @ColumnInfo(name = "reminder_due_at")
    val reminderDueAt: Long? = null,        // When to surface the reminder
    
    // === VERSIONING (belief revision) ===
    @ColumnInfo(name = "version", defaultValue = "1")
    val version: Int = 1,
    
    @ColumnInfo(name = "superseded_by_id")
    val supersededById: String? = null,     // If this node was updated/replaced
    
    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,           // false = soft deleted or superseded
)
