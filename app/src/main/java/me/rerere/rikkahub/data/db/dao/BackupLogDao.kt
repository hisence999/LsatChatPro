package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.BackupLogEntity

@Dao
interface BackupLogDao {
    @Insert
    fun insert(log: BackupLogEntity): Long

    @Query("SELECT * FROM BackupLogEntity ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BackupLogEntity>>

    @Query("DELETE FROM BackupLogEntity")
    fun clearAll()

    @Query(
        """
        DELETE FROM BackupLogEntity
        WHERE id NOT IN (
            SELECT id FROM BackupLogEntity
            ORDER BY created_at DESC
            LIMIT :keep
        )
        """
    )
    fun pruneKeepLatest(keep: Int)
}

