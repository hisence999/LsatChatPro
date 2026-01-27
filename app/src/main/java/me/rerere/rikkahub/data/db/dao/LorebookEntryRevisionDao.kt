package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.LorebookEntryRevisionEntity

@Dao
interface LorebookEntryRevisionDao {
    @Insert
    fun insert(entity: LorebookEntryRevisionEntity): Long

    @Query(
        """
        SELECT * FROM lorebook_entry_revision
        WHERE lorebook_id = :lorebookId
        ORDER BY created_at DESC
        LIMIT :limit
        """
    )
    fun observeRecentByLorebook(lorebookId: String, limit: Int): Flow<List<LorebookEntryRevisionEntity>>

    @Query(
        """
        SELECT * FROM lorebook_entry_revision
        WHERE lorebook_id = :lorebookId
        ORDER BY created_at DESC
        LIMIT :limit
        """
    )
    fun getRecentByLorebook(lorebookId: String, limit: Int): List<LorebookEntryRevisionEntity>

    @Query(
        """
        SELECT * FROM lorebook_entry_revision
        WHERE lorebook_id = :lorebookId
          AND undone_at IS NULL
        ORDER BY created_at DESC
        LIMIT 1
        """
    )
    fun getLatestNotUndone(lorebookId: String): LorebookEntryRevisionEntity?

    @Query("SELECT * FROM lorebook_entry_revision WHERE id = :id LIMIT 1")
    fun getById(id: Int): LorebookEntryRevisionEntity?

    @Query("UPDATE lorebook_entry_revision SET undone_at = :undoneAt WHERE id = :id")
    fun markUndone(id: Int, undoneAt: Long)

    @Query(
        """
        DELETE FROM lorebook_entry_revision
        WHERE lorebook_id = :lorebookId
          AND id NOT IN (
            SELECT id FROM lorebook_entry_revision
            WHERE lorebook_id = :lorebookId
            ORDER BY created_at DESC
            LIMIT :keep
          )
        """
    )
    fun pruneKeepLatest(lorebookId: String, keep: Int)
}

