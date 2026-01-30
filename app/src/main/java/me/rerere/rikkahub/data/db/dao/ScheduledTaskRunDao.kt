package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import me.rerere.rikkahub.data.db.entity.ScheduledTaskRunEntity

@Dao
interface ScheduledTaskRunDao {
    @Query(
        """
        SELECT * FROM scheduled_task_runs
        WHERE task_id = :taskId AND scheduled_for = :scheduledFor
        LIMIT 1
        """
    )
    suspend fun getByTaskAndScheduledFor(taskId: String, scheduledFor: Long): ScheduledTaskRunEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(run: ScheduledTaskRunEntity): Long

    @Update
    suspend fun update(run: ScheduledTaskRunEntity)

    @Query("DELETE FROM scheduled_task_runs WHERE assistant_id = :assistantId")
    suspend fun deleteByAssistantId(assistantId: String)
}

