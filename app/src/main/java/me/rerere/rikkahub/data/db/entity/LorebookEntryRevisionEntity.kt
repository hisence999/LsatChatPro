package me.rerere.rikkahub.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lorebook_entry_revision",
    indices = [
        Index(value = ["lorebook_id", "created_at"]),
        Index(value = ["lorebook_id", "undone_at"]),
        Index(value = ["entry_id"]),
        Index(value = ["assistant_id"]),
    ],
)
data class LorebookEntryRevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "lorebook_id") val lorebookId: String,
    @ColumnInfo(name = "assistant_id") val assistantId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String? = null,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "entry_title") val entryTitle: String,
    @ColumnInfo(name = "entry_index") val entryIndex: Int? = null,
    @ColumnInfo(name = "before_json") val beforeJson: String? = null,
    @ColumnInfo(name = "after_json") val afterJson: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "undone_at") val undoneAt: Long? = null,
)
