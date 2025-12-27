package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scheduled_tasks",
    indices = [
        Index(value = ["assistant_id"]),
        Index(value = ["assistant_id", "enabled", "next_run_at"]),
    ]
)
data class ScheduledTaskEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "assistant_id")
    val assistantId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "enabled", defaultValue = "0")
    val enabled: Boolean = false,
    @ColumnInfo(name = "prompt_template")
    val promptTemplate: String,
    @ColumnInfo(name = "time_of_day_minutes", defaultValue = "0")
    val timeOfDayMinutes: Int = 0,
    @ColumnInfo(name = "repeat_type", defaultValue = "0")
    val repeatType: Int = 0,
    @ColumnInfo(name = "weekly_mask", defaultValue = "0")
    val weeklyMask: Int = 0,
    @ColumnInfo(name = "monthly_day", defaultValue = "0")
    val monthlyDay: Int = 0,
    @ColumnInfo(name = "interval_value", defaultValue = "0")
    val intervalValue: Int = 0,
    @ColumnInfo(name = "interval_unit", defaultValue = "0")
    val intervalUnit: Int = 0,
    @ColumnInfo(name = "override_model_id")
    val overrideModelId: String? = null,
    @ColumnInfo(name = "search_override_type", defaultValue = "0")
    val searchOverrideType: Int = 0,
    @ColumnInfo(name = "search_provider_index", defaultValue = "-1")
    val searchProviderIndex: Int = -1,
    @ColumnInfo(name = "mcp_override_type", defaultValue = "0")
    val mcpOverrideType: Int = 0,
    @ColumnInfo(name = "mcp_server_id")
    val mcpServerId: String? = null,
    @ColumnInfo(name = "accuracy_mode", defaultValue = "0")
    val accuracyMode: Int = 0,
    @ColumnInfo(name = "notify_on_done", defaultValue = "0")
    val notifyOnDone: Boolean = false,
    @ColumnInfo(name = "last_run_at")
    val lastRunAt: Long? = null,
    @ColumnInfo(name = "last_scheduled_for")
    val lastScheduledFor: Long? = null,
    @ColumnInfo(name = "next_run_at")
    val nextRunAt: Long? = null,
    @ColumnInfo(name = "last_error_code")
    val lastErrorCode: String? = null,
    @ColumnInfo(name = "last_error_at")
    val lastErrorAt: Long? = null,
)

