package dev.bidu.app.data.room

import dev.bidu.app.data.HistoryRepository
import dev.bidu.core.agent.ConversationTurn
import dev.bidu.core.agent.LogSanitizer
import dev.bidu.core.agent.RequestOrigin
import dev.bidu.core.tool.ToolOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Conversation turns, newest first. Text that looks like it carries a secret is scrubbed before it is stored. */
class RoomHistoryRepository(private val dao: HistoryDao) : HistoryRepository {

    override fun observeRecent(limit: Int): Flow<List<ConversationTurn>> =
        dao.observeRecent(limit.coerceIn(1, MAX_ROWS)).map { rows -> rows.map { it.toTurn() } }

    override suspend fun append(turn: ConversationTurn): ConversationTurn {
        val stored = turn.copy(
            requestText = scrub(turn.requestText),
            spoken = scrub(turn.spoken),
            display = scrub(turn.display),
        )
        val id = dao.insert(stored.toEntity())
        dao.trimTo(MAX_ROWS)
        return stored.copy(id = id)
    }

    override suspend fun recent(limit: Int): List<ConversationTurn> =
        dao.recent(limit.coerceIn(1, MAX_ROWS)).map { it.toTurn() }

    override suspend fun clear() = dao.clear()

    private fun ConversationTurn.toEntity() = HistoryEntity(
        requestId = requestId,
        requestText = requestText,
        origin = origin.name,
        outcome = outcome.name,
        spoken = spoken,
        display = display,
        at = at.toEpochMilli(),
    )

    private fun HistoryEntity.toTurn() = ConversationTurn(
        id = id,
        requestId = requestId,
        requestText = requestText,
        origin = RequestOrigin.entries.firstOrNull { it.name == origin } ?: RequestOrigin.TEXT,
        outcome = ToolOutcome.entries.firstOrNull { it.name == outcome } ?: ToolOutcome.FAILED,
        spoken = spoken,
        display = display,
        at = Instant.ofEpochMilli(at),
    )

    private companion object {
        const val MAX_ROWS = 2_000
        val SECRET_HINT = Regex("""(?i)\b(otp|pin|upi|password|passcode|cvv|mpin)\b|ओटीपी|पिन|पासवर्ड""")

        fun scrub(text: String): String = if (SECRET_HINT.containsMatchIn(text)) LogSanitizer.sanitize(text) else text
    }
}
