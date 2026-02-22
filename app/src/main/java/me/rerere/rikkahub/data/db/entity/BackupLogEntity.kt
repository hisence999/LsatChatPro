package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class BackupLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    val action: String,
    val trigger: String,
    val backend: String,
    val status: String,
    @ColumnInfo(name = "file_name")
    val fileName: String?,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    val message: String,
    val error: String?,
)

