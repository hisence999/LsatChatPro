package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.rerere.rikkahub.data.db.entity.ToolResultArchiveEntity

@Dao
interface ToolResultArchiveDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ToolResultArchiveEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<ToolResultArchiveEntity>): List<Long>

    @Query(
        """
        SELECT * FROM tool_result_archive
        WHERE conversation_id = :conversationId
        AND user_turn_index < :maxUserTurnIndexExclusive
        """
    )
    suspend fun getByConversationBeforeTurn(
        conversationId: String,
        maxUserTurnIndexExclusive: Int
    ): List<ToolResultArchiveEntity>

    @Query("SELECT * FROM tool_result_archive WHERE conversation_id = :conversationId")
    suspend fun getByConversationId(conversationId: String): List<ToolResultArchiveEntity>

    @Query("SELECT id FROM tool_result_archive WHERE conversation_id = :conversationId")
    suspend fun getIdsByConversationId(conversationId: String): List<Int>

    @Query(
        """
        SELECT * FROM tool_result_archive
        WHERE conversation_id = :conversationId
        AND tool_call_id = :toolCallId
        LIMIT 1
        """
    )
    suspend fun getByToolCallId(conversationId: String, toolCallId: String): ToolResultArchiveEntity?

    @Query(
        """
        UPDATE tool_result_archive
        SET embedding = :embeddingJson,
            embedding_model_id = :modelId
        WHERE id = :id
        """
    )
    suspend fun updateEmbedding(id: Int, embeddingJson: String?, modelId: String?)

    @Query(
        """
        UPDATE tool_result_archive
        SET last_accessed_at = :timestampMs
        WHERE id IN (:ids)
        """
    )
    suspend fun touch(ids: List<Int>, timestampMs: Long)

    @Query("DELETE FROM tool_result_archive WHERE conversation_id = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)

    @Query(
        """
        SELECT COUNT(*) FROM tool_result_archive
        WHERE conversation_id = :conversationId
        AND user_turn_index < :maxUserTurnIndexExclusive
        """
    )
    suspend fun countByConversationBeforeTurn(
        conversationId: String,
        maxUserTurnIndexExclusive: Int
    ): Int
}
