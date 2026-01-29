package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.AIRequestLogEntity

@Dao
interface AIRequestLogDao {
    @Insert
    fun insert(log: AIRequestLogEntity): Long

    @Query("SELECT COUNT(*) FROM AIRequestLogEntity")
    fun countAll(): Int

    @Query("SELECT * FROM AIRequestLogEntity ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AIRequestLogEntity>>

    @Query("SELECT * FROM AIRequestLogEntity ORDER BY created_at DESC LIMIT :limit")
    fun getRecent(limit: Int): List<AIRequestLogEntity>

    @Query("SELECT * FROM AIRequestLogEntity ORDER BY created_at DESC")
    fun getAll(): List<AIRequestLogEntity>

    @Query("SELECT * FROM AIRequestLogEntity WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<AIRequestLogEntity?>

    @Query("UPDATE AIRequestLogEntity SET source = :source WHERE id = :id")
    fun updateSource(id: Long, source: String)

    @Query("DELETE FROM AIRequestLogEntity")
    fun clearAll()

    @Query(
        """
        DELETE FROM AIRequestLogEntity 
        WHERE id NOT IN (
            SELECT id FROM AIRequestLogEntity
            ORDER BY created_at DESC
            LIMIT :keep
        )
        """
    )
    fun pruneKeepLatest(keep: Int)
}
