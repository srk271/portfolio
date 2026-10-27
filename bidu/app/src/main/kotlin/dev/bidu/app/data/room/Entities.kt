package dev.bidu.app.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Instants are stored as epoch millis and enums by name, so the schema needs no type converters.

@Entity(tableName = "notes", indices = [Index("updated_at")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "reminders", indices = [Index("status", "due_at")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "reminder_text") val text: String,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "exact") val exact: Boolean,
)

@Entity(tableName = "history", indices = [Index("at_millis")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "request_text") val requestText: String,
    @ColumnInfo(name = "origin") val origin: String,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "spoken") val spoken: String,
    @ColumnInfo(name = "display") val display: String,
    @ColumnInfo(name = "at_millis") val at: Long,
)

@Entity(tableName = "memory")
data class MemoryEntity(
    @PrimaryKey @ColumnInfo(name = "fact_key") val key: String,
    @ColumnInfo(name = "fact_value") val value: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "action_log", indices = [Index("at_millis")])
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "call_id") val callId: String,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "args_summary") val argsSummary: String,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "evidence_summary") val evidenceSummary: String,
    @ColumnInfo(name = "error_code") val errorCode: String?,
    @ColumnInfo(name = "attempts") val attempts: Int,
    @ColumnInfo(name = "duration_millis") val durationMillis: Long,
    @ColumnInfo(name = "at_millis") val at: Long,
)
