package dev.bidu.core.agent

import dev.bidu.core.memory.KnownApp
import dev.bidu.core.model.Capability
import dev.bidu.core.model.QuestionKind
import dev.bidu.core.model.ResponseStyle
import dev.bidu.core.model.RiskLevel
import dev.bidu.core.model.UserQuestion
import dev.bidu.core.model.say
import dev.bidu.core.nlu.ChoiceMatcher
import dev.bidu.core.nlu.Intent
import dev.bidu.core.nlu.NluContext
import dev.bidu.core.nlu.NluResult
import dev.bidu.core.plan.DeclineReason
import dev.bidu.core.plan.Plan
import dev.bidu.core.plan.PlanContext
import dev.bidu.core.plan.PlanRequest
import dev.bidu.core.plan.PlanSource
import dev.bidu.core.policy.ConfirmationDecision
import dev.bidu.core.policy.ConfirmationRequest
import dev.bidu.core.policy.PolicyContext
import dev.bidu.core.policy.PolicyDecision
import dev.bidu.core.policy.RequestScreening
import dev.bidu.core.tool.ToolCall
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolExecution
import dev.bidu.core.tool.ToolOutcome
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.EvidenceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * UNDERSTAND -> PLAN -> CHECK_PERMISSIONS -> EXECUTE -> VERIFY -> REPORT, one request at a time.
 *
 * Each request runs as a child job of [scope] so [cancel] can stop it from anywhere (notification, "ruk ja").
 * The engine also keeps the one open follow-up of the last request: an unanswered clarification is merged
 * with the next utterance, and a CHOICE question from a tool ("kaunsa Rahul?") resumes the stopped call
 * with the chosen option.
 */
class DefaultAgentEngine(
    private val deps: EngineDependencies,
    private val scope: CoroutineScope,
) : AgentEngine {

    private val _state = MutableStateFlow(AgentState.IDLE)
    override val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val mutex = Mutex()

    @Volatile
    private var current: Job? = null
    private var followUp: FollowUp? = null
    private val recent = ArrayDeque<ConversationTurn>()

    private sealed interface FollowUp {
        val question: UserQuestion
        val at: Instant

        data class Clarifying(val text: String, override val question: UserQuestion, override val at: Instant) : FollowUp
        data class Choosing(
            val call: ToolCall,
            val rest: List<ToolCall>,
            val data: Map<String, String>,
            override val question: UserQuestion,
            override val at: Instant,
        ) : FollowUp
    }

    private class Run(val request: AgentRequest) {
        @Volatile var style: ResponseStyle = ResponseStyle()
        @Volatile var nlu: NluResult? = null
        @Volatile var plan: Plan? = null
        @Volatile var executions: List<ToolExecution> = emptyList()
    }

    override suspend fun handle(request: AgentRequest): AgentReport = mutex.withLock {
        val run = Run(request)
        val job = scope.async { process(run) }
        current = job
        try {
            job.await()
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) {
                job.cancel()
                throw e
            }
            withContext(NonCancellable) { cancelled(run) }
        } finally {
            current = null
        }
    }

    override fun cancel() {
        current?.cancel()
    }

    private suspend fun process(run: Run): AgentReport {
        val request = run.request
        return try {
            val settings = deps.settings()
            run.style = request.languageHint?.let { settings.style.copy(language = it) } ?: settings.style
            val lang = run.style.language
            val now = deps.clock.nowZoned()

            transition(request, AgentState.UNDERSTANDING)
            val screening = deps.policy.screen(request.text)
            if (screening is RequestScreening.Forbidden) {
                followUp = null
                run.plan = Plan.Decline(
                    DeclineReason.FORBIDDEN,
                    lang.say(
                        "OTP, UPI PIN, password ya bank details mein main kabhi madad nahi karta. Ye kisi ko mat batana.",
                        "I never handle OTPs, UPI PINs, passwords or bank details. Don't share them with anyone.",
                        "ओटीपी, यूपीआई पिन, पासवर्ड या बैंक की जानकारी में मैं कभी मदद नहीं करता। यह किसी को मत बताइए।",
                    ),
                    PlanSource.RULES,
                )
                return report(run, ToolOutcome.FAILED)
            }

            val apps = quietly { deps.appCatalog.apps() }.orEmpty()
            val pending = followUp?.takeIf { Duration.between(it.at, deps.clock.now()) <= FOLLOW_UP_TTL }
            followUp = null
            val nluContext = NluContext(
                now = now,
                pendingQuestion = pending?.question,
                knownAppNames = appNames(apps),
                wakePhrases = settings.wakePhrases,
            )
            var nlu = deps.nlu.parse(request.text, nluContext)
            if (pending is FollowUp.Clarifying && (nlu.intent is Intent.Unknown || nlu.confidence < MIN_CONFIDENCE)) {
                val merged = deps.nlu.parse("${pending.text} ${request.text}", nluContext.copy(pendingQuestion = null))
                if (merged.intent !is Intent.Unknown && merged.confidence >= MIN_CONFIDENCE) nlu = merged
            }
            run.nlu = nlu
            emit(AgentEvent.Understood(request.id, nlu))

            transition(request, AgentState.PLANNING)
            val plan = resume(pending, nlu, request) ?: run {
                val memory = quietly { deps.memory.all() }.orEmpty()
                deps.planner.plan(
                    PlanRequest(
                        request = request,
                        nlu = nlu,
                        tools = deps.registry.specs,
                        context = PlanContext(now, lang, memory, recent.toList(), apps),
                    ),
                )
            }
            run.plan = plan
            emit(AgentEvent.Planned(request.id, plan))

            when (plan) {
                is Plan.Reply -> report(run, ToolOutcome.VERIFIED)
                is Plan.Decline -> report(run, ToolOutcome.FAILED)
                is Plan.Clarify -> {
                    followUp = FollowUp.Clarifying(nlu.rawText, plan.question, deps.clock.now())
                    report(run, ToolOutcome.NEEDS_USER, question = plan.question)
                }
                is Plan.Execute -> execute(run, plan, settings)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(run, e)
        }
    }

    private suspend fun execute(run: Run, plan: Plan.Execute, settings: EngineSettings): AgentReport {
        val request = run.request
        val lang = run.style.language

        transition(request, AgentState.CHECKING_PERMISSIONS)
        val specs = plan.calls.mapNotNull { deps.registry.find(it.tool)?.spec }
        val required = specs.flatMapTo(mutableSetOf()) { it.requiredCapabilities }
        val missing = deps.permissions.missing(required)
        if (missing.isNotEmpty()) {
            emit(AgentEvent.PermissionMissing(request.id, missing))
            val question = UserQuestion(
                text = lang.say(
                    "Pehle ye permission chahiye: ${labels(missing)}. Bidu app mein Setup kholo.",
                    "I need this permission first: ${labels(missing)}. Open Setup in the Bidu app.",
                    "पहले यह अनुमति चाहिए: ${labels(missing)}। बिडू ऐप में सेटअप खोलिए।",
                ),
                kind = QuestionKind.PERMISSION,
                capabilities = missing,
            )
            return report(run, ToolOutcome.NEEDS_USER, question = question, missing = missing)
        }
        if (specs.count { it.risk == RiskLevel.CRITICAL } > 1) {
            run.plan = Plan.Decline(
                DeclineReason.UNSUPPORTED,
                lang.say("Ek baar mein ek hi payment wala kaam. Ek-ek karke bolo.", "Only one payment per request. Ask for them one at a time.", "एक बार में एक ही पेमेंट वाला काम। एक-एक करके बोलिए।"),
                plan.source,
            )
            return report(run, ToolOutcome.FAILED)
        }

        transition(request, AgentState.EXECUTING)
        val executions = mutableListOf<ToolExecution>()
        for ((index, call) in plan.calls.withIndex()) {
            val spec = deps.registry.find(call.tool)?.spec
            if (spec != null) {
                val stop = when (val decision = deps.policy.decide(call, spec, PolicyContext(lang, request.origin, settings.policy))) {
                    PolicyDecision.Proceed -> null
                    is PolicyDecision.Deny -> ToolResult.failed(ToolErrorCode.PERMISSION_DENIED, "Blocked by policy: ${decision.reason}")
                    is PolicyDecision.Confirm -> {
                        val confirmation = ConfirmationRequest(
                            id = "${request.id}/${call.id}",
                            requestId = request.id,
                            toolName = call.tool,
                            risk = spec.risk,
                            prompt = decision.prompt,
                            language = lang,
                            timeoutMillis = settings.policy.confirmationTimeoutMillis,
                        )
                        emit(AgentEvent.ConfirmationRequested(request.id, confirmation))
                        when (deps.confirmations.confirm(confirmation)) {
                            ConfirmationDecision.APPROVED -> null
                            ConfirmationDecision.REJECTED -> ToolResult(ToolOutcome.CANCELLED, "You said no", mapOf(REASON to DECLINED))
                            ConfirmationDecision.TIMEOUT -> ToolResult(ToolOutcome.CANCELLED, "No answer to the confirmation", mapOf(REASON to UNANSWERED))
                            ConfirmationDecision.UNAVAILABLE -> ToolResult.failed(ToolErrorCode.UNAVAILABLE, "Could not ask for confirmation")
                        }
                    }
                }
                if (stop != null) {
                    executions += logged(request, call, stop)
                    run.executions = executions.toList()
                    break
                }
            }
            emit(AgentEvent.ToolStarted(request.id, call))
            val execution = deps.registry.execute(
                call,
                ToolContext(request.id, lang, deps.clock) { message -> emit(AgentEvent.Progress(request.id, message)) },
            )
            executions += execution
            run.executions = executions.toList()
            emit(AgentEvent.ToolFinished(request.id, execution))
            val result = execution.result
            if (result.outcome == ToolOutcome.NEEDS_USER) {
                result.question?.takeIf { it.kind == QuestionKind.CHOICE && it.options.isNotEmpty() }?.let { q ->
                    followUp = FollowUp.Choosing(call, plan.calls.drop(index + 1), result.data, q, deps.clock.now())
                }
            }
            if (result.outcome == ToolOutcome.FAILED || result.outcome == ToolOutcome.NEEDS_USER || result.outcome == ToolOutcome.CANCELLED) break
        }

        transition(request, AgentState.VERIFYING)
        val verified = executions.map(::verify)
        run.executions = verified
        val question = verified.lastOrNull { it.result.outcome == ToolOutcome.NEEDS_USER }?.result?.question
        return report(run, aggregate(verified), question = question, missing = question?.capabilities.orEmpty())
    }

    /** Re-checks each result against its evidence; anything that claims more than it observed becomes FAILED. */
    private fun verify(execution: ToolExecution): ToolExecution {
        val r = execution.result
        val consistent = when (r.outcome) {
            ToolOutcome.VERIFIED -> r.evidence.any { it.kind.observable }
            ToolOutcome.HANDED_OFF -> r.evidence.any { it.kind == EvidenceKind.INTENT_DELIVERED }
            ToolOutcome.NEEDS_USER -> r.question != null
            ToolOutcome.FAILED -> r.error != null
            ToolOutcome.CANCELLED -> true
        }
        if (consistent) return execution
        return execution.copy(result = ToolResult.failed(ToolErrorCode.INTERNAL, "Result was not backed by evidence: ${r.summary}"))
    }

    private fun aggregate(executions: List<ToolExecution>): ToolOutcome {
        val outcomes = executions.map { it.result.outcome }
        return when {
            outcomes.isEmpty() -> ToolOutcome.FAILED
            ToolOutcome.FAILED in outcomes -> ToolOutcome.FAILED
            ToolOutcome.NEEDS_USER in outcomes -> ToolOutcome.NEEDS_USER
            ToolOutcome.CANCELLED in outcomes -> ToolOutcome.CANCELLED
            ToolOutcome.HANDED_OFF in outcomes -> ToolOutcome.HANDED_OFF
            else -> ToolOutcome.VERIFIED
        }
    }

    /** Continues a call that stopped on a CHOICE question when the new utterance picks one of its options. */
    private fun resume(pending: FollowUp?, nlu: NluResult, request: AgentRequest): Plan? {
        if (pending !is FollowUp.Choosing) return null
        val intent = nlu.intent
        if (intent is Intent.Cancel || intent is Intent.Confirm && !intent.approved) {
            return Plan.Reply(
                run { val l = deps.settings().style.language; l.say("Theek hai, rehne dete hain.", "Okay, leaving it.", "ठीक है, रहने देते हैं।") },
                PlanSource.RULES,
            )
        }
        val options = pending.question.options
        val choice = ChoiceMatcher.match(request.text, options) ?: return null
        val call = pending.call
        val argName = CHOICE_ARGS.firstOrNull { call.args.has(it) }
            ?: deps.registry.find(call.tool)?.spec?.params?.firstOrNull { it.required }?.name
            ?: "query"
        var args = call.args.with(argName, choice).with("id", null)
        val packages = pending.data["packages"]?.split(',')?.map { it.trim() }
        if (packages != null && packages.size == options.size) args = args.with("package", packages[options.indexOf(choice)])
        return Plan.Execute("Continue: ${call.purpose.ifBlank { call.tool }}", listOf(call.copy(args = args)) + pending.rest, PlanSource.RULES, 1.0)
    }

    private suspend fun logged(request: AgentRequest, call: ToolCall, result: ToolResult): ToolExecution {
        val at = deps.clock.now()
        quietly {
            deps.actionLog.append(
                ActionLogEntry(
                    requestId = request.id,
                    callId = call.id,
                    toolName = call.tool,
                    argsSummary = LogSanitizer.sanitize(call.args.asMap().mapValues { (_, v) -> v.toString().take(80) }),
                    outcome = result.outcome,
                    evidenceSummary = LogSanitizer.sanitize(result.summary),
                    errorCode = result.error?.code,
                    attempts = 0,
                    durationMillis = 0,
                    at = at,
                ),
            )
        }
        return ToolExecution(call, result, attempts = 0, startedAt = at, durationMillis = 0)
    }

    private fun report(
        run: Run,
        outcome: ToolOutcome,
        question: UserQuestion? = null,
        missing: Set<Capability> = emptySet(),
    ): AgentReport {
        val request = run.request
        transition(request, AgentState.REPORTING)
        val composed = compose(run, outcome, question, missing)
        val report = AgentReport(
            requestId = request.id,
            outcome = outcome,
            spoken = composed.spoken,
            display = composed.display,
            plan = run.plan,
            executions = run.executions,
            question = question,
            missingCapabilities = missing,
            finishedAt = deps.clock.now(),
        )
        remember(request, report)
        transition(request, AgentState.IDLE)
        emit(AgentEvent.Completed(request.id, report))
        return report
    }

    private fun cancelled(run: Run): AgentReport {
        followUp = null
        return report(run, ToolOutcome.CANCELLED)
    }

    private fun failure(run: Run, e: Exception): AgentReport {
        val request = run.request
        followUp = null
        transition(request, AgentState.ERROR)
        val lang = run.style.language
        val spoken = lang.say(
            "Kuch gadbad ho gayi, kaam pura nahi hua. Ek baar phir bolo.",
            "Something went wrong and it didn't get done. Please try again.",
            "कुछ गड़बड़ हो गई, काम पूरा नहीं हुआ। एक बार फिर बोलिए।",
        )
        val report = AgentReport(
            requestId = request.id,
            outcome = ToolOutcome.FAILED,
            spoken = spoken,
            display = "$spoken\n(${e.javaClass.simpleName})",
            plan = run.plan,
            executions = run.executions,
            finishedAt = deps.clock.now(),
        )
        remember(request, report)
        emit(AgentEvent.Completed(request.id, report))
        return report
    }

    private fun compose(run: Run, outcome: ToolOutcome, question: UserQuestion?, missing: Set<Capability>): ComposedResponse {
        val input = ComposerInput(
            request = run.request,
            nlu = run.nlu,
            plan = run.plan,
            executions = run.executions,
            outcome = outcome,
            question = question,
            missingCapabilities = missing,
            now = deps.clock.nowZoned(),
        )
        return try {
            deps.composer.compose(input, run.style)
        } catch (e: Exception) {
            val text = when (outcome) {
                ToolOutcome.VERIFIED -> "OK"
                ToolOutcome.HANDED_OFF -> run.executions.lastOrNull()?.result?.summary ?: "Handed off"
                else -> question?.text ?: run.executions.lastOrNull()?.result?.summary ?: outcome.name.lowercase(Locale.ROOT)
            }
            ComposedResponse(text, text)
        }
    }

    private fun remember(request: AgentRequest, report: AgentReport) {
        recent.addLast(
            ConversationTurn(
                id = 0L,
                requestId = request.id,
                requestText = request.text,
                origin = request.origin,
                outcome = report.outcome,
                spoken = report.spoken,
                display = report.display,
                at = report.finishedAt,
            ),
        )
        while (recent.size > RECENT_TURNS) recent.removeFirst()
    }

    private fun transition(request: AgentRequest, to: AgentState) {
        _state.value = to
        emit(AgentEvent.StateChanged(request.id, to))
    }

    private fun emit(event: AgentEvent) {
        _events.tryEmit(event)
    }

    private fun appNames(apps: List<KnownApp>): List<String> =
        apps.flatMap { listOf(it.label) + it.aliases }.map { it.lowercase(Locale.ROOT) }.distinct()

    private fun labels(capabilities: Set<Capability>) = capabilities.joinToString(", ") { it.userLabel }

    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    internal companion object {
        const val MIN_CONFIDENCE = 0.5
        const val RECENT_TURNS = 6
        const val REASON = "reason"
        const val DECLINED = "declined"
        const val UNANSWERED = "unanswered"
        val FOLLOW_UP_TTL: Duration = Duration.ofMinutes(3)
        val CHOICE_ARGS = listOf("name", "app", "query", "key")
    }
}
