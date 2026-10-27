package dev.bidu.app.data

import dev.bidu.core.agent.ActionLogEntry
import dev.bidu.core.agent.ActionLogSink
import dev.bidu.core.agent.ConversationTurn
import dev.bidu.core.model.Note
import dev.bidu.core.model.Reminder
import dev.bidu.core.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface NoteRepository {
    fun observeAll(): Flow<List<Note>>
    suspend fun get(id: Long): Note?
    suspend fun create(body: String, title: String? = null): Note
    suspend fun update(note: Note): Note
    /** Null or blank [query] lists the most recent notes. */
    suspend fun search(query: String?, limit: Int = 20): List<Note>
    suspend fun delete(id: Long): Boolean
}

interface ReminderRepository {
    fun observeUpcoming(): Flow<List<Reminder>>
    suspend fun get(id: Long): Reminder?
    suspend fun create(text: String, dueAt: Instant, exact: Boolean): Reminder
    suspend fun upcoming(limit: Int = 20): List<Reminder>
    suspend fun search(query: String, limit: Int = 20): List<Reminder>
    /** Every reminder still SCHEDULED, for rescheduling after boot. */
    suspend fun scheduled(): List<Reminder>
    suspend fun updateStatus(id: Long, status: ReminderStatus): Boolean
    suspend fun updateExact(id: Long, exact: Boolean): Boolean
    suspend fun delete(id: Long): Boolean
}

interface HistoryRepository {
    fun observeRecent(limit: Int): Flow<List<ConversationTurn>>
    /** Stores the turn and returns it with its assigned id. */
    suspend fun append(turn: ConversationTurn): ConversationTurn
    suspend fun recent(limit: Int): List<ConversationTurn>
    suspend fun clear()
}

/** The engine writes through [ActionLogSink]; the UI reads the same rows. */
interface ActionLogRepository : ActionLogSink {
    fun observeRecent(limit: Int): Flow<List<ActionLogEntry>>
    suspend fun recent(limit: Int): List<ActionLogEntry>
    suspend fun clear()
}
