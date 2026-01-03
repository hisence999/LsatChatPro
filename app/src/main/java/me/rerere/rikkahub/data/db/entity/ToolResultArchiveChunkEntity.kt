package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Chunk-level archive for tool call results.
 *
 * Stored locally for chunk-based RAG retrieval so we can recall the most relevant segment
 * instead of only the leading prefix.
 */
@Entity(
    tableName = "tool_result_archive_chunk",
    indices = [
        Index(value = ["conversation_id", "tool_call_id", "chunk_index"], unique = true),
        Index(value = ["conversation_id", "user_turn_index"]),
        Index(value = ["assistant_id"]),
    ]
)
data class ToolResultArchiveChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "assistant_id") val assistantId: String,
    @ColumnInfo(name = "tool_call_id") val toolCallId: String,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "chunk_index") val chunkIndex: Int,
    @ColumnInfo(name = "chunk_text") val chunkText: String,
    @ColumnInfo(name = "user_turn_index") val userTurnIndex: Int,
    @ColumnInfo(name = "embedding") val embedding: String? = null, // JSON list of floats
    @ColumnInfo(name = "embedding_model_id", defaultValue = "") val embeddingModelId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAt: Long = System.currentTimeMillis(),
)

