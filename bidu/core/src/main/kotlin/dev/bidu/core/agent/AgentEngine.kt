package dev.bidu.core.agent

import dev.bidu.core.memory.AppCatalog
import dev.bidu.core.memory.MemoryStore
import dev.bidu.core.model.Capability
import dev.bidu.core.model.Clock
import dev.bidu.core.model.ResponseStyle
import dev.bidu.core.model.UserQuestion
import dev.bidu.core.nlu.Nlu
import dev.bidu.core.nlu.NluResult
import dev.bidu.core.plan.Plan
import dev.bidu.core.plan.Planner
import dev.bidu.core.policy.AutonomyPolicy
import dev.bidu.core.policy.ConfirmationGateway
import dev.bidu.core.policy.PermissionGateway
import dev.bidu.core.policy.PolicySettings
import dev.bidu.core.tool.ToolExecution
import dev.bidu.core.tool.ToolOutcome
import dev.bidu.core.tool.ToolRegistry
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZonedDateTime

/**
 * The agent loop: UNDERSTAND -> PLAN -> CHECK PERMISSIONS -> EXECUTE -> VERIFY -> REPORT.
 *
 * Contract for implementations:
 * - [handle] processes one request at a time (callers serialise); it never throws for cancellation
 *   triggered through [cancel] but returns a report with outcome CANCELLED.
 * - Every state transition is published on [state] and [events]; [AgentEvent.Completed] is always the
 *   last event of a request.
 * - Policy screening runs before planning; a forbidden utterance yields a Decline plan and FAILED.
 * - Permission checks cover the union of required capabilities of all calls in the plan before any
 *   call runs; missing ones produce NEEDS_USER with [AgentReport.missingCapabilities].
 * - Confirmation is requested per call as the policy decides; REJECTED/TIMEOUT stop the plan.
 * - VERIFYING re-checks that each result's outcome is consistent with its evidence (the type system
 *   already enforces it) and downgrades anything suspicious to FAILED rather than overclaiming.
 */
interface AgentEngine {
    val state: StateFlow<AgentState>
    val events: SharedFlow<AgentEvent>
    suspend fun handle(request: AgentRequest): AgentReport
    fun cancel()
}

/** Settings snapshot the engine reads at the start of each request. */
data class EngineSettings(
    val style: ResponseStyle = ResponseStyle(),
    val policy: PolicySettings = PolicySettings(),
    val wakePhrases: List<String> = listOf("hey bidu", "bhai"),
)

/** Everything the default engine needs; built once by the app container. */
data class EngineDependencies(
    val nlu: Nlu,
    val planner: Planner,
    val registry: ToolRegistry,
    val permissions: PermissionGateway,
    val confirmations: ConfirmationGateway,
    val policy: AutonomyPolicy,
    val composer: ResponseComposer,
    val actionLog: ActionLogSink,
    val memory: MemoryStore,
    val appCatalog: AppCatalog,
    val clock: Clock,
    val settings: () -> EngineSettings,
)

/** Input to the composer: everything known about the request once execution finished. */
data class ComposerInput(
    val request: AgentRequest,
    val nlu: NluResult?,
    val plan: Plan?,
    val executions: List<ToolExecution>,
    val outcome: ToolOutcome,
    val question: UserQuestion?,
    val missingCapabilities: Set<Capability>,
    val now: ZonedDateTime,
)

data class ComposedResponse(val spoken: String, val display: String)

/**
 * Produces the spoken and displayed reply in the configured language. Must word HANDED_OFF results
 * as hand-offs ("Uber khol diya, destination set hai; ride wahin confirm karna") and must never
 * invent facts (fares, ETAs, whether another app succeeded).
 */
interface ResponseComposer {
    fun compose(input: ComposerInput, style: ResponseStyle): ComposedResponse
}
