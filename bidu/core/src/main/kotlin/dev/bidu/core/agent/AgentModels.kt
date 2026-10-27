package dev.bidu.core.agent

import dev.bidu.core.model.Capability
import dev.bidu.core.model.Language
import dev.bidu.core.model.UserQuestion
import dev.bidu.core.nlu.NluResult
import dev.bidu.core.plan.Plan
import dev.bidu.core.policy.ConfirmationRequest
import dev.bidu.core.tool.ToolCall
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolExecution
import dev.bidu.core.tool.ToolOutcome
import java.time.Instant

/**
 * The single assistant state machine. The engine moves through UNDERSTANDING..REPORTING and
 * ERROR; the voice pipeline owns IDLE, LISTENING_WAKE and CAPTURING_COMMAND. The app-side
 * controller merges both into one value for the UI and the notification.
 */
enum class AgentState {
    IDLE,
    LISTENING_WAKE,
    CAPTURING_COMMAND,
    UNDERSTANDING,
    PLANNING,
    CHECKING_PERMISSIONS,
    EXECUTING,
    VERIFYING,
    REPORTING,
    ERROR,
}

enum class RequestOrigin { VOICE_WAKE, PUSH_TO_TALK, TEXT, NOTIFICATION }

data class AgentRequest(
    val id: String,
    val text: String,
    val origin: RequestOrigin,
    val receivedAt: Instant,
    val languageHint: Language? = null,
)

sealed interface AgentEvent {
    val requestId: String

    data class StateChanged(override val requestId: String, val state: AgentState) : AgentEvent
    data class Understood(override val requestId: String, val nlu: NluResult) : AgentEvent
    data class Planned(override val requestId: String, val plan: Plan) : AgentEvent
    data class PermissionMissing(override val requestId: String, val capabilities: Set<Capability>) : AgentEvent
    data class ConfirmationRequested(override val requestId: String, val request: ConfirmationRequest) : AgentEvent
    data class ToolStarted(override val requestId: String, val call: ToolCall) : AgentEvent
    data class ToolFinished(override val requestId: String, val execution: ToolExecution) : AgentEvent
    data class Progress(override val requestId: String, val message: String) : AgentEvent
    data class Completed(override val requestId: String, val report: AgentReport) : AgentEvent
}

/**
 * Final answer for one request. [outcome] aggregates the executions: any FAILED -> FAILED,
 * else any NEEDS_USER -> NEEDS_USER, else any HANDED_OFF -> HANDED_OFF, else VERIFIED;
 * a cancelled request is CANCELLED. Reply/Clarify/Decline plans map to VERIFIED/NEEDS_USER/FAILED.
 */
data class AgentReport(
    val requestId: String,
    val outcome: ToolOutcome,
    /** What TTS says. Short, in the user's language. */
    val spoken: String,
    /** What the notification and the activity log show; may carry more detail than [spoken]. */
    val display: String,
    val plan: Plan? = null,
    val executions: List<ToolExecution> = emptyList(),
    val question: UserQuestion? = null,
    val missingCapabilities: Set<Capability> = emptySet(),
    val finishedAt: Instant,
)

/** One request/answer pair as kept in history and shown on the main screen. */
data class ConversationTurn(
    val id: Long,
    val requestId: String,
    val requestText: String,
    val origin: RequestOrigin,
    val outcome: ToolOutcome,
    val spoken: String,
    val display: String,
    val at: Instant,
)

/** One row of the action log: what tool ran, with what (sanitised) args, and what came back. */
data class ActionLogEntry(
    val requestId: String,
    val callId: String,
    val toolName: String,
    val argsSummary: String,
    val outcome: ToolOutcome,
    val evidenceSummary: String,
    val errorCode: ToolErrorCode?,
    val attempts: Int,
    val durationMillis: Long,
    val at: Instant,
)

interface ActionLogSink {
    suspend fun append(entry: ActionLogEntry)
}
