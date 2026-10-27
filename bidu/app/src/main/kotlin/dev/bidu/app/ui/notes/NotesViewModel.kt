package dev.bidu.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidu.app.R
import dev.bidu.app.data.ActionLogRepository
import dev.bidu.app.data.NoteRepository
import dev.bidu.app.data.ReminderRepository
import dev.bidu.app.ui.UiMessage
import dev.bidu.app.ui.attempt
import dev.bidu.app.ui.normalizeMemoryKey
import dev.bidu.core.agent.ActionLogEntry
import dev.bidu.core.memory.MemoryStore
import dev.bidu.core.model.MemoryFact
import dev.bidu.core.model.Note
import dev.bidu.core.model.Reminder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Notes, remembered facts, upcoming reminders (read-only) and the action log (read-only). */
class NotesViewModel(
    private val noteRepository: NoteRepository,
    private val memory: MemoryStore,
    reminderRepository: ReminderRepository,
    actionLogRepository: ActionLogRepository,
) : ViewModel() {
    val notes: StateFlow<List<Note>> = noteRepository.observeAll()
        .map { list -> list.sortedByDescending { it.updatedAt } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<List<Reminder>> = reminderRepository.observeUpcoming()
        .map { list -> list.sortedBy { it.dueAt } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val actionLog: StateFlow<List<ActionLogEntry>> = actionLogRepository.observeRecent(ACTION_LOG_LIMIT)
        .map { list -> list.sortedByDescending { it.at } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // MemoryStore has no change stream, so the list is re-read on resume and after each edit.
    private val _facts = MutableStateFlow<List<MemoryFact>>(emptyList())
    val facts: StateFlow<List<MemoryFact>> = _facts.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun reloadFacts() {
        viewModelScope.launch { loadFacts() }
    }

    /** [existing] null creates a note. Returns false (with a message) when the body is empty. */
    fun saveNote(existing: Note?, title: String, body: String): Boolean {
        val cleanBody = body.trim()
        if (cleanBody.isEmpty()) {
            send(UiMessage(R.string.ui_msg_note_empty))
            return false
        }
        val cleanTitle = title.trim().ifBlank { null }
        viewModelScope.launch {
            attempt {
                if (existing == null) {
                    noteRepository.create(cleanBody, cleanTitle)
                } else {
                    noteRepository.update(existing.copy(title = cleanTitle, body = cleanBody))
                }
            }.onFailure { send(UiMessage(R.string.ui_msg_save_failed)) }
        }
        return true
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            attempt { noteRepository.delete(note.id) }
                .onSuccess { deleted -> if (!deleted) send(UiMessage(R.string.ui_msg_delete_failed)) }
                .onFailure { send(UiMessage(R.string.ui_msg_delete_failed)) }
        }
    }

    /** [originalKey] null adds a fact; a changed key renames it. Refuses to overwrite a different fact. */
    fun saveFact(originalKey: String?, key: String, value: String): Boolean {
        val cleanKey = normalizeMemoryKey(key)
        val cleanValue = value.trim()
        if (cleanKey.isEmpty() || cleanValue.isEmpty()) {
            send(UiMessage(R.string.ui_msg_fact_invalid))
            return false
        }
        if (cleanKey != originalKey && _facts.value.any { it.key == cleanKey }) {
            send(UiMessage(R.string.ui_msg_fact_exists, cleanKey))
            return false
        }
        viewModelScope.launch {
            attempt<Unit> {
                memory.put(cleanKey, cleanValue)
                if (originalKey != null && originalKey != cleanKey) memory.remove(originalKey)
            }.onFailure { send(UiMessage(R.string.ui_msg_save_failed)) }
            loadFacts()
        }
        return true
    }

    fun deleteFact(key: String) {
        viewModelScope.launch {
            attempt { memory.remove(key) }.onFailure { send(UiMessage(R.string.ui_msg_delete_failed)) }
            loadFacts()
        }
    }

    private suspend fun loadFacts() {
        attempt { memory.all() }
            .onSuccess { list -> _facts.value = list.sortedBy { it.key } }
            .onFailure { send(UiMessage(R.string.ui_msg_load_failed)) }
    }

    private fun send(message: UiMessage) {
        _messages.trySend(message)
    }

    private companion object {
        const val ACTION_LOG_LIMIT = 200
    }
}
