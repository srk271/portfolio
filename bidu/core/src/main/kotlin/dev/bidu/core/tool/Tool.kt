package dev.bidu.core.tool

import dev.bidu.core.model.Clock
import dev.bidu.core.model.Language
import java.time.Instant

/** Per-request information handed to every tool. */
data class ToolContext(
    val requestId: String,
    val language: Language,
    val clock: Clock,
    /** Optional progress text for long operations; the controller surfaces it in the notification. */
    val onProgress: suspend (String) -> Unit = {},
)

/**
 * A capability Bidu can exercise. Implementations are plain classes in the app module; the
 * registry wraps every call with validation, permission checks, timeout, retry and logging, so
 * [execute] only receives args that already passed [ToolSpec.validate].
 */
interface Tool {
    val spec: ToolSpec
    suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult
}

/** One planned invocation. [id] is unique within a plan; [purpose] is a short human phrase for the log. */
data class ToolCall(
    val id: String,
    val tool: String,
    val args: ToolArgs = ToolArgs.EMPTY,
    val purpose: String = "",
)

/** A finished (or cancelled) invocation with timing, as recorded in the action log. */
data class ToolExecution(
    val call: ToolCall,
    val result: ToolResult,
    val attempts: Int,
    val startedAt: Instant,
    val durationMillis: Long,
)

/**
 * Registry of tools plus the execution wrapper. The contract of [execute]:
 * 1. Unknown tool -> FAILED(NOT_FOUND). Invalid args -> FAILED(INVALID_ARGS); nothing runs.
 * 2. Runs the tool under `withTimeout(spec.timeoutMillis)`; timeout -> FAILED(TIMEOUT, retryable).
 * 3. Retries only when `spec.idempotent` and the failure is retryable, up to `spec.retry.maxAttempts`.
 * 4. On coroutine cancellation: appends a CANCELLED action-log entry and rethrows.
 * 5. Every attempt sequence ends with exactly one action-log entry (args sanitised).
 */
interface ToolRegistry {
    val specs: List<ToolSpec>
    fun find(name: String): Tool?
    suspend fun execute(call: ToolCall, context: ToolContext): ToolExecution
}
