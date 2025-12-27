package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.ScheduledTaskEntity

@Dao
interface ScheduledTaskDao {
    @Query(
        """
        SELECT * FROM scheduled_tasks
        WHERE assistant_id = :assistantId
        ORDER BY enabled DESC, (next_run_at IS NULL) ASC, next_run_at ASC
        """
    )
    fun observeTasksOfAssistant(assistantId: String): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScheduledTaskEntity?

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1")
    suspend fun getAllEnabled(): List<ScheduledTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE scheduled_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean)

    @Query(
        """
        UPDATE scheduled_tasks
        SET last_run_at = :lastRunAt,
            last_scheduled_for = :lastScheduledFor,
            next_run_at = :nextRunAt,
            last_error_code = :lastErrorCode,
            last_error_at = :lastErrorAt
        WHERE id = :id
        """
    )
    suspend fun updateRunFields(
        id: String,
        lastRunAt: Long?,
        lastScheduledFor: Long?,
        nextRunAt: Long?,
        lastErrorCode: String?,
        lastErrorAt: Long?,
    )
}
