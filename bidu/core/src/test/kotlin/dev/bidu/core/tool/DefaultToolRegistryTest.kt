package dev.bidu.core.tool

import dev.bidu.core.FakeTool
import dev.bidu.core.RecordingLog
import dev.bidu.core.agent.ActionLogEntry
import dev.bidu.core.agent.ActionLogSink
import dev.bidu.core.model.Language
import dev.bidu.core.testClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultToolRegistryTest {
    private val clock = testClock()
    private val context = ToolContext("req-1", Language.HINGLISH, clock)

    private fun ok(summary: String = "ok") = ToolResult.measured(summary, "measured")

    @Test
    fun `unknown tool fails with NOT_FOUND and is logged`() = runTest {
        val log = RecordingLog()
        val registry = DefaultToolRegistry(emptyList(), log, clock)
        val execution = registry.execute(ToolCall("c1", "teleport"), context)
        assertEquals(ToolOutcome.FAILED, execution.result.outcome)
        assertEquals(ToolErrorCode.NOT_FOUND, execution.result.error?.code)
        assertEquals(1, log.entries.size)
        assertEquals(0, log.entries.single().attempts)
    }

    @Test
    fun `invalid args never reach the tool and name the problem`() = runTest {
        val tool = FakeTool(BuiltinTools.SET_ALARM) { _, _ -> ok() }
        val log = RecordingLog()
        val registry = DefaultToolRegistry(listOf(tool), log, clock)
        val execution = registry.execute(ToolCall("c1", "set_alarm", ToolArgs.of("hour" to 25)), context)
        assertEquals(ToolErrorCode.INVALID_ARGS, execution.result.error?.code)
        assertTrue(execution.result.summary.contains("hour"))
        assertTrue(execution.result.summary.contains("minute"))
        assertTrue(tool.calls.isEmpty())
        assertEquals(ToolErrorCode.INVALID_ARGS, log.entries.single().errorCode)
    }

    @Test
    fun `valid args are normalised and sanitised in the log`() = runTest {
        val tool = FakeTool(BuiltinTools.DIAL_CONTACT) { _, _ -> ToolResult.handedOff("Opened dialer", "com.dialer", "DIAL") }
        val flash = FakeTool(BuiltinTools.FLASHLIGHT) { args, _ -> ToolResult.verified("Torch ${args.string("state")}", Evidence(EvidenceKind.SYSTEM_CALLBACK, "torch callback")) }
        val log = RecordingLog()
        val registry = DefaultToolRegistry(listOf(tool, flash), log, clock)
        registry.execute(ToolCall("c1", "dial_contact", ToolArgs.of("number" to "+91 98765 43210")), context)
        val torch = registry.execute(ToolCall("c2", "flashlight", ToolArgs.of("state" to "ON")), context)
        assertEquals("on", flash.calls.single().string("state"))
        assertEquals(ToolOutcome.VERIFIED, torch.result.outcome)
        val dialEntry = log.entries.first()
        assertFalse(dialEntry.argsSummary.contains("98765"), dialEntry.argsSummary)
        assertEquals("req-1", dialEntry.requestId)
        assertEquals(1, dialEntry.attempts)
    }

    @Test
    fun `slow tool times out`() = runTest {
        val slow = FakeTool(BuiltinTools.SET_TIMER) { _, _ ->
            delay(60_000)
            ok()
        }
        val registry = DefaultToolRegistry(listOf(slow), RecordingLog(), clock)
        val execution = registry.execute(ToolCall("c1", "set_timer", ToolArgs.of("seconds" to 60)), context)
        assertEquals(ToolErrorCode.TIMEOUT, execution.result.error?.code)
        assertEquals(1, execution.attempts)
        assertEquals(BuiltinTools.SET_TIMER.timeoutMillis, currentTime)
    }

    @Test
    fun `idempotent tool retries transient failures with backoff`() = runTest {
        val spec = BuiltinTools.BATTERY_LEVEL.copy(retry = RetryPolicy(maxAttempts = 3, initialBackoffMillis = 100, backoffMultiplier = 2.0))
        val flaky = FakeTool(spec) { _, attempt ->
            if (attempt < 3) ToolResult.failed(ToolErrorCode.UNAVAILABLE, "busy", retryable = true) else ok("76%")
        }
        val log = RecordingLog()
        val registry = DefaultToolRegistry(listOf(flaky), log, clock)
        val execution = registry.execute(ToolCall("c1", spec.name), context)
        assertEquals(ToolOutcome.VERIFIED, execution.result.outcome)
        assertEquals(3, execution.attempts)
        assertEquals(300, currentTime)
        assertEquals(1, log.entries.size)
        assertEquals(3, log.entries.single().attempts)
    }

    @Test
    fun `non-idempotent tools and permanent failures are not retried`() = runTest {
        val reminder = FakeTool(BuiltinTools.CREATE_REMINDER) { _, _ -> ToolResult.failed(ToolErrorCode.INTERNAL, "db", retryable = true) }
        val spec = BuiltinTools.BATTERY_LEVEL.copy(retry = RetryPolicy(maxAttempts = 3))
        val broken = FakeTool(spec) { _, _ -> ToolResult.failed(ToolErrorCode.UNAVAILABLE, "no battery service") }
        val registry = DefaultToolRegistry(listOf(reminder, broken), RecordingLog(), clock)
        val r = registry.execute(ToolCall("c1", "create_reminder", ToolArgs.of("text" to "x", "at" to 1_900_000_000_000L)), context)
        val b = registry.execute(ToolCall("c2", spec.name), context)
        assertEquals(1, r.attempts)
        assertEquals(1, b.attempts)
        assertEquals(1, reminder.calls.size)
    }

    @Test
    fun `crashing tool becomes FAILED INTERNAL`() = runTest {
        val crash = FakeTool(BuiltinTools.TELL_TIME) { _, _ -> error("boom") }
        val execution = DefaultToolRegistry(listOf(crash), RecordingLog(), clock).execute(ToolCall("c1", "tell_time"), context)
        assertEquals(ToolErrorCode.INTERNAL, execution.result.error?.code)
    }

    @Test
    fun `cancellation propagates and is logged as CANCELLED`() = runTest {
        val started = CompletableDeferred<Unit>()
        val hanging = FakeTool(BuiltinTools.WEB_SEARCH) { _, _ ->
            started.complete(Unit)
            awaitCancellation()
        }
        val log = RecordingLog()
        val registry = DefaultToolRegistry(listOf(hanging), log, clock)
        val job = async { registry.execute(ToolCall("c1", "web_search", ToolArgs.of("query" to "weather")), context) }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, log.entries.size)
        val entry = log.entries.single()
        assertEquals(ToolOutcome.CANCELLED, entry.outcome)
        assertEquals(1, entry.attempts)
    }

    @Test
    fun `a failing log sink does not change the result`() = runTest {
        val sink = object : ActionLogSink {
            override suspend fun append(entry: ActionLogEntry) = throw IllegalStateException("disk full")
        }
        val execution = DefaultToolRegistry(listOf(FakeTool(BuiltinTools.TELL_TIME) { _, _ -> ok() }), sink, clock)
            .execute(ToolCall("c1", "tell_time"), context)
        assertEquals(ToolOutcome.VERIFIED, execution.result.outcome)
    }

    @Test
    fun `duplicate tool names are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DefaultToolRegistry(
                listOf(FakeTool(BuiltinTools.TELL_TIME) { _, _ -> ok() }, FakeTool(BuiltinTools.TELL_TIME) { _, _ -> ok() }),
                RecordingLog(),
                clock,
            )
        }
    }
}
