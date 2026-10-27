package dev.bidu.app.data.room

import dev.bidu.app.data.ReminderRepository
import dev.bidu.core.model.Clock
import dev.bidu.core.model.Reminder
import dev.bidu.core.model.ReminderStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class RoomReminderRepository(private val dao: ReminderDao, private val clock: Clock) : ReminderRepository {

    /** SCHEDULED reminders (including past-due ones that have not fired yet), soonest first. */
    override fun observeUpcoming(): Flow<List<Reminder>> = dao.observeScheduled().map { rows -> rows.map { it.toReminder() } }

    override suspend fun get(id: Long): Reminder? = dao.get(id)?.toReminder()

    override suspend fun create(text: String, dueAt: Instant, exact: Boolean): Reminder {
        val cleanText = text.trim()
        require(cleanText.isNotEmpty()) { "Reminder text must not be blank" }
        val entity = ReminderEntity(
            text = cleanText,
            dueAt = dueAt.toEpochMilli(),
            createdAt = clock.now().toEpochMilli(),
            status = ReminderStatus.SCHEDULED.name,
            exact = exact,
        )
        val id = dao.insert(entity)
        return entity.copy(id = id).toReminder()
    }

    override suspend fun upcoming(limit: Int): List<Reminder> =
        dao.scheduled(limit.coerceIn(1, MAX_LIMIT)).map { it.toReminder() }

    /** Matches reminder text; SCHEDULED rows come first, then the rest by due time. */
    override suspend fun search(query: String, limit: Int): List<Reminder> {
        val max = limit.coerceIn(1, MAX_LIMIT)
        val q = query.trim()
        if (q.isEmpty()) return upcoming(max)

        val phrase = dao.searchLike(LikePattern.escape(q), max)
        if (phrase.isNotEmpty()) return phrase.map { it.toReminder() }

        val tokens = TextSearch.tokens(q)
        if (tokens.isEmpty()) return emptyList()
        return dao.recent(SCAN_LIMIT)
            .map { it to TextSearch.score(it.text, tokens) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<ReminderEntity, Int>> { it.second }
                    .thenBy { if (it.first.status == ReminderStatus.SCHEDULED.name) 0 else 1 }
                    .thenBy { it.first.dueAt },
            )
            .take(max)
            .map { it.first.toReminder() }
    }

    override suspend fun scheduled(): List<Reminder> = dao.allScheduled().map { it.toReminder() }

    override suspend fun updateStatus(id: Long, status: ReminderStatus): Boolean = dao.updateStatus(id, status.name) > 0

    override suspend fun updateExact(id: Long, exact: Boolean): Boolean = dao.updateExact(id, exact) > 0

    override suspend fun delete(id: Long): Boolean = dao.delete(id) > 0

    private fun ReminderEntity.toReminder() = Reminder(
        id = id,
        text = text,
        dueAt = Instant.ofEpochMilli(dueAt),
        createdAt = Instant.ofEpochMilli(createdAt),
        status = ReminderStatus.entries.firstOrNull { it.name == status } ?: ReminderStatus.CANCELLED,
        exact = exact,
    )

    private companion object {
        const val MAX_LIMIT = 200
        const val SCAN_LIMIT = 1_000
    }
}
