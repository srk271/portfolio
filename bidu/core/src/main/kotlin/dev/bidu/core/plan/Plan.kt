package dev.bidu.core.plan

import dev.bidu.core.agent.AgentRequest
import dev.bidu.core.agent.ConversationTurn
import dev.bidu.core.memory.KnownApp
import dev.bidu.core.model.Language
import dev.bidu.core.model.MemoryFact
import dev.bidu.core.model.UserQuestion
import dev.bidu.core.nlu.NluResult
import dev.bidu.core.tool.ToolCall
import dev.bidu.core.tool.ToolSpec
import java.time.ZonedDateTime

enum class PlanSource { RULES, CLOUD }

enum class DeclineReason {
    /** Credentials, OTP, UPI PIN, bank details: never. */
    FORBIDDEN,
    /** Understood but Bidu has no tool for it; the explanation says what the user can do instead. */
    UNSUPPORTED,
    /** Not understood, and cloud reasoning is off or also failed. */
    NOT_UNDERSTOOD,
}

sealed interface Plan {
    val source: PlanSource

    /** Run these calls in order; a FAILED or NEEDS_USER call stops the rest. */
    data class Execute(
        val goal: String,
        val calls: List<ToolCall>,
        override val source: PlanSource,
        val confidence: Double,
    ) : Plan {
        init {
            require(calls.isNotEmpty()) { "Execute plan needs at least one call" }
            require(calls.map { it.id }.toSet().size == calls.size) { "ToolCall ids must be unique" }
        }
    }

    /** No tool needed (help, greetings, recalled fact already in context). */
    data class Reply(val text: String, override val source: PlanSource) : Plan

    /** Ambiguous; ask and wait for the next utterance. */
    data class Clarify(val question: UserQuestion, override val source: PlanSource) : Plan

    data class Decline(val reason: DeclineReason, val explanation: String, override val source: PlanSource) : Plan
}

data class PlanContext(
    val now: ZonedDateTime,
    val language: Language,
    val memory: List<MemoryFact> = emptyList(),
    val recentTurns: List<ConversationTurn> = emptyList(),
    val installedApps: List<KnownApp> = emptyList(),
)

data class PlanRequest(
    val request: AgentRequest,
    val nlu: NluResult,
    val tools: List<ToolSpec>,
    val context: PlanContext,
)

/**
 * Turns an understood request into a [Plan]. The rule planner covers every offline intent; the
 * cloud planner (optional, consented) is consulted only for [dev.bidu.core.nlu.Intent.Unknown]
 * and must restrict itself to the [PlanRequest.tools] it was given.
 */
interface Planner {
    suspend fun plan(request: PlanRequest): Plan
}
