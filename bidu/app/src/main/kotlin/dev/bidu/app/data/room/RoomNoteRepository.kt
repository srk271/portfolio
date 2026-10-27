package dev.bidu.app.data.room

import dev.bidu.app.data.NoteRepository
import dev.bidu.core.model.Clock
import dev.bidu.core.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class RoomNoteRepository(private val dao: NoteDao, private val clock: Clock) : NoteRepository {

    override fun observeAll(): Flow<List<Note>> = dao.observeAll().map { rows -> rows.map { it.toNote() } }

    override suspend fun get(id: Long): Note? = dao.get(id)?.toNote()

    override suspend fun create(body: String, title: String?): Note {
        val cleanBody = body.trim()
        require(cleanBody.isNotEmpty()) { "Note body must not be blank" }
        val now = clock.now().toEpochMilli()
        val entity = NoteEntity(title = title?.trim()?.takeIf { it.isNotEmpty() }, body = cleanBody, createdAt = now, updatedAt = now)
        val id = dao.insert(entity)
        return entity.copy(id = id).toNote()
    }

    override suspend fun update(note: Note): Note {
        val existing = dao.get(note.id) ?: throw NoSuchElementException("Note ${note.id} does not exist")
        val cleanBody = note.body.trim()
        require(cleanBody.isNotEmpty()) { "Note body must not be blank" }
        val updated = existing.copy(
            title = note.title?.trim()?.takeIf { it.isNotEmpty() },
            body = cleanBody,
            updatedAt = clock.now().toEpochMilli(),
        )
        dao.update(updated)
        return updated.toNote()
    }

    override suspend fun search(query: String?, limit: Int): List<Note> {
        val max = limit.coerceIn(1, MAX_LIMIT)
        val q = query?.trim().orEmpty()
        if (q.isEmpty()) return dao.recent(max).map { it.toNote() }

        val phrase = dao.searchLike(LikePattern.escape(q), max)
        if (phrase.isNotEmpty()) return phrase.map { it.toNote() }

        val tokens = TextSearch.tokens(q)
        if (tokens.isEmpty()) return emptyList()
        return dao.recent(SCAN_LIMIT)
            .map { it to TextSearch.score("${it.title.orEmpty()} ${it.body}", tokens) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<NoteEntity, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            .take(max)
            .map { it.first.toNote() }
    }

    override suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    private fun NoteEntity.toNote() = Note(
        id = id,
        title = title,
        body = body,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

    private companion object {
        const val MAX_LIMIT = 200
        const val SCAN_LIMIT = 1_000
    }
}
