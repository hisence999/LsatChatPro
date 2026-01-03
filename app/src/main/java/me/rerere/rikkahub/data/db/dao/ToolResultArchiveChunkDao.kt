package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.ToolResultArchiveChunkEntity

@Dao
interface ToolResultArchiveChunkDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<ToolResultArchiveChunkEntity>): List<Long>

    @Query(
        """
        SELECT * FROM tool_result_archive_chunk
        WHERE conversation_id = :conversationId
        AND user_turn_index < :maxUserTurnIndexExclusive
        """
    )
    suspend fun getByConversationBeforeTurn(
        conversationId: String,
        maxUserTurnIndexExclusive: Int,
    ): List<ToolResultArchiveChunkEntity>

    @Query(
        """
        SELECT * FROM tool_result_archive_chunk
        WHERE conversation_id = :conversationId
        AND tool_call_id = :toolCallId
        ORDER BY chunk_index ASC
        """
    )
    suspend fun getByToolCallId(
        conversationId: String,
        toolCallId: String,
    ): List<ToolResultArchiveChunkEntity>

    @Query("SELECT DISTINCT tool_call_id FROM tool_result_archive_chunk WHERE conversation_id = :conversationId")
    suspend fun getToolCallIdsByConversationId(conversationId: String): List<String>

    @Query(
        """
        SELECT COUNT(*) FROM tool_result_archive_chunk
        WHERE conversation_id = :conversationId
        AND tool_call_id = :toolCallId
        """
    )
    suspend fun countByToolCallId(conversationId: String, toolCallId: String): Int

    @Query("SELECT id FROM tool_result_archive_chunk WHERE conversation_id = :conversationId")
    suspend fun getIdsByConversationId(conversationId: String): List<Int>

    @Query("DELETE FROM tool_result_archive_chunk WHERE conversation_id = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)

    @Query(
        """
        UPDATE tool_result_archive_chunk
        SET embedding = :embeddingJson,
            embedding_model_id = :modelId
        WHERE id = :id
        """
    )
    suspend fun updateEmbedding(id: Int, embeddingJson: String?, modelId: String?)

    @Query(
        """
        UPDATE tool_result_archive_chunk
        SET last_accessed_at = :timestampMs
        WHERE id IN (:ids)
        """
    )
    suspend fun touch(ids: List<Int>, timestampMs: Long)
}
