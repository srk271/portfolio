package dev.bidu.core.tool

import dev.bidu.core.agent.ActionLogEntry
import dev.bidu.core.agent.ActionLogSink
import dev.bidu.core.agent.LogSanitizer
import dev.bidu.core.model.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant

/**
 * Executes tool calls under the [ToolRegistry] contract: validation, per-attempt timeout, retries with
 * exponential backoff only for idempotent tools and retryable failures, cooperative cancellation (logged as
 * CANCELLED, then rethrown) and exactly one sanitised action-log entry per call.
 */
class DefaultToolRegistry(
    tools: List<Tool>,
    private val actionLog: ActionLogSink,
    private val clock: Clock,
) : ToolRegistry {

    private val byName: Map<String, Tool> = tools.associateBy { it.spec.name }

    init {
        require(byName.size == tools.size) {
            "Duplicate tool names: " + tools.groupBy { it.spec.name }.filterValues { it.size > 1 }.keys
        }
    }

    override val specs: List<ToolSpec> = tools.map { it.spec }

    override fun find(name: String): Tool? = byName[name]

    override suspend fun execute(call: ToolCall, context: ToolContext): ToolExecution {
        val startedAt = clock.now()
        val tool = byName[call.tool]
            ?: return finish(call, context, startedAt, 0, ToolResult.failed(ToolErrorCode.NOT_FOUND, "No tool named '${call.tool}'"))

        val args = when (val validation = tool.spec.validate(call.args)) {
            is ValidationResult.Valid -> validation.args
            is ValidationResult.Invalid -> return finish(
                call, context, startedAt, 0,
                ToolResult.failed(ToolErrorCode.INVALID_ARGS, "Invalid arguments for ${call.tool}: " + validation.errors.joinToString("; ")),
            )
        }

        val spec = tool.spec
        val maxAttempts = if (spec.idempotent) spec.retry.maxAttempts else 1
        var backoff = spec.retry.initialBackoffMillis
        var attempts = 0
        var result: ToolResult
        while (true) {
            attempts++
            result = try {
                withTimeout(spec.timeoutMillis) { tool.execute(args, context) }
            } catch (e: TimeoutCancellationException) {
                ToolResult.failed(ToolErrorCode.TIMEOUT, "${call.tool} did not finish within ${spec.timeoutMillis} ms", retryable = true)
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    append(call, context, startedAt, attempts, ToolResult.cancelled("Cancelled while running"))
                }
                throw e
            } catch (e: Exception) {
                ToolResult.failed(ToolErrorCode.INTERNAL, "${call.tool} crashed: ${e.javaClass.simpleName}")
            }
            val retry = result.outcome == ToolOutcome.FAILED && result.error?.retryable == true && attempts < maxAttempts
            if (!retry) break
            delay(backoff)
            backoff = (backoff * spec.retry.backoffMultiplier).toLong().coerceAtLeast(1)
        }
        return finish(call, context, startedAt, attempts, result)
    }

    private suspend fun finish(call: ToolCall, context: ToolContext, startedAt: Instant, attempts: Int, result: ToolResult): ToolExecution =
        append(call, context, startedAt, attempts, result)

    private suspend fun append(call: ToolCall, context: ToolContext, startedAt: Instant, attempts: Int, result: ToolResult): ToolExecution {
        val duration = Duration.between(startedAt, clock.now()).toMillis().coerceAtLeast(0)
        val execution = ToolExecution(call, result, attempts, startedAt, duration)
        val entry = ActionLogEntry(
            requestId = context.requestId,
            callId = call.id,
            toolName = call.tool,
            argsSummary = summarizeArgs(call.args),
            outcome = result.outcome,
            evidenceSummary = LogSanitizer.sanitize(
                (result.evidence.map { "${it.kind}: ${it.description}" } + result.error?.let { "error: ${it.message}" }.let(::listOfNotNull))
                    .joinToString("; ")
                    .take(MAX_SUMMARY),
            ),
            errorCode = result.error?.code,
            attempts = attempts,
            durationMillis = duration,
            at = clock.now(),
        )
        try {
            actionLog.append(entry)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A broken log must not turn a real result into a failure.
        }
        return execution
    }

    private fun summarizeArgs(args: ToolArgs): String =
        LogSanitizer.sanitize(args.asMap().mapValues { (_, v) -> v.toString().let { s -> if (s.length > MAX_ARG) s.take(MAX_ARG) + "…" else s } })

    private companion object {
        const val MAX_ARG = 80
        const val MAX_SUMMARY = 400
    }
}
