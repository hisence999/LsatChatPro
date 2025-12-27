package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_task_runs",
    indices = [
        Index(value = ["task_id"]),
        Index(value = ["task_id", "scheduled_for"], unique = true),
    ]
)
data class ScheduledTaskRunEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "task_id")
    val taskId: String,
    @ColumnInfo(name = "assistant_id")
    val assistantId: String,
    @ColumnInfo(name = "scheduled_for")
    val scheduledFor: Long,
    @ColumnInfo(name = "attempt", defaultValue = "0")
    val attempt: Int = 0,
    @ColumnInfo(name = "status", defaultValue = "0")
    val status: Int = 0,
    @ColumnInfo(name = "started_at")
    val startedAt: Long? = null,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long? = null,
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "model_id_used")
    val modelIdUsed: String? = null,
    @ColumnInfo(name = "search_provider_used")
    val searchProviderUsed: String? = null,
    @ColumnInfo(name = "mcp_server_used")
    val mcpServerUsed: String? = null,
)

