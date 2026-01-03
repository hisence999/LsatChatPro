package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent archive for tool call results.
 *
 * - Stored locally for later RAG retrieval.
 * - Decoupled from prompt context: old tool results can be dropped from requests while remaining accessible.
 */
@Entity(
    tableName = "tool_result_archive",
    indices = [
        Index(value = ["conversation_id", "tool_call_id"], unique = true),
        Index(value = ["conversation_id", "user_turn_index"]),
        Index(value = ["assistant_id"]),
    ]
)
data class ToolResultArchiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "assistant_id") val assistantId: String,
    @ColumnInfo(name = "tool_call_id") val toolCallId: String,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "arguments_json") val argumentsJson: String,
    @ColumnInfo(name = "content_json") val contentJson: String,
    @ColumnInfo(name = "metadata_json") val metadataJson: String? = null,
    @ColumnInfo(name = "extract_text") val extractText: String,
    @ColumnInfo(name = "user_turn_index") val userTurnIndex: Int,
    @ColumnInfo(name = "embedding") val embedding: String? = null, // JSON list of floats
    @ColumnInfo(name = "embedding_model_id", defaultValue = "") val embeddingModelId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAt: Long = System.currentTimeMillis(),
)

