package dev.bidu.app.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// LIKE patterns are passed already escaped (see LikePattern); a backslash is the escape character.

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity): Int

    @Query("SELECT * FROM notes ORDER BY updated_at DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<NoteEntity>

    @Query(
        "SELECT * FROM notes WHERE body LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "OR title LIKE '%' || :pattern || '%' ESCAPE '\\' ORDER BY updated_at DESC, id DESC LIMIT :limit",
    )
    suspend fun searchLike(pattern: String, limit: Int): List<NoteEntity>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE status = 'SCHEDULED' ORDER BY due_at ASC, id ASC")
    fun observeScheduled(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders WHERE status = 'SCHEDULED' ORDER BY due_at ASC, id ASC LIMIT :limit")
    suspend fun scheduled(limit: Int): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status = 'SCHEDULED' ORDER BY due_at ASC, id ASC")
    suspend fun allScheduled(): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY due_at DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ReminderEntity>

    @Query(
        "SELECT * FROM reminders WHERE reminder_text LIKE '%' || :pattern || '%' ESCAPE '\\' " +
            "ORDER BY CASE WHEN status = 'SCHEDULED' THEN 0 ELSE 1 END, due_at ASC LIMIT :limit",
    )
    suspend fun searchLike(pattern: String, limit: Int): List<ReminderEntity>

    @Query("UPDATE reminders SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String): Int

    @Query("UPDATE reminders SET exact = :exact WHERE id = :id")
    suspend fun updateExact(id: Long, exact: Boolean): Int

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY at_millis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY at_millis DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<HistoryEntity>

    @Insert
    suspend fun insert(turn: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY at_millis DESC, id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int): Int

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory ORDER BY fact_key ASC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory ORDER BY fact_key ASC")
    suspend fun all(): List<MemoryEntity>

    @Query("SELECT * FROM memory WHERE fact_key = :key")
    suspend fun get(key: String): MemoryEntity?

    @Upsert
    suspend fun upsert(fact: MemoryEntity)

    @Query("DELETE FROM memory WHERE fact_key = :key")
    suspend fun delete(key: String): Int
}

@Dao
interface ActionLogDao {
    @Query("SELECT * FROM action_log ORDER BY at_millis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActionLogEntity>>

    @Query("SELECT * FROM action_log ORDER BY at_millis DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ActionLogEntity>

    @Insert
    suspend fun insert(entry: ActionLogEntity): Long

    @Query("DELETE FROM action_log WHERE id NOT IN (SELECT id FROM action_log ORDER BY at_millis DESC, id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int): Int

    @Query("DELETE FROM action_log")
    suspend fun clear()
}
