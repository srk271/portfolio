package dev.bidu.app.data.room

import dev.bidu.app.data.ActionLogRepository
import dev.bidu.core.agent.ActionLogEntry
import dev.bidu.core.agent.LogSanitizer
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Action log sink and reader. The registry already sanitises args; this re-applies [LogSanitizer]
 * to both free-text columns so nothing secret-looking reaches disk even from a careless caller.
 */
class RoomActionLogRepository(private val dao: ActionLogDao) : ActionLogRepository {

    override suspend fun append(entry: ActionLogEntry) {
        dao.insert(
            ActionLogEntity(
                requestId = entry.requestId.take(MAX_ID),
                callId = entry.callId.take(MAX_ID),
                toolName = entry.toolName.take(MAX_ID),
                argsSummary = LogSanitizer.sanitize(entry.argsSummary).take(MAX_TEXT),
                outcome = entry.outcome.name,
                evidenceSummary = LogSanitizer.sanitize(entry.evidenceSummary).take(MAX_TEXT),
                errorCode = entry.errorCode?.name,
                attempts = entry.attempts,
                durationMillis = entry.durationMillis,
                at = entry.at.toEpochMilli(),
            ),
        )
        dao.trimTo(MAX_ROWS)
    }

    override fun observeRecent(limit: Int): Flow<List<ActionLogEntry>> =
        dao.observeRecent(limit.coerceIn(1, MAX_ROWS)).map { rows -> rows.map { it.toEntry() } }

    override suspend fun recent(limit: Int): List<ActionLogEntry> = dao.recent(limit.coerceIn(1, MAX_ROWS)).map { it.toEntry() }

    override suspend fun clear() = dao.clear()

    private fun ActionLogEntity.toEntry() = ActionLogEntry(
        requestId = requestId,
        callId = callId,
        toolName = toolName,
        argsSummary = argsSummary,
        outcome = ToolOutcome.entries.firstOrNull { it.name == outcome } ?: ToolOutcome.FAILED,
        evidenceSummary = evidenceSummary,
        errorCode = errorCode?.let { code -> ToolErrorCode.entries.firstOrNull { it.name == code } ?: ToolErrorCode.INTERNAL },
        attempts = attempts,
        durationMillis = durationMillis,
        at = Instant.ofEpochMilli(at),
    )

    private companion object {
        const val MAX_ROWS = 2_000
        const val MAX_ID = 128
        const val MAX_TEXT = 1_000
    }
}
