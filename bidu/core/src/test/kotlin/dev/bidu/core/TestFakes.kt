package dev.bidu.core

import dev.bidu.core.agent.ActionLogEntry
import dev.bidu.core.agent.ActionLogSink
import dev.bidu.core.memory.AppCatalog
import dev.bidu.core.memory.KnownApp
import dev.bidu.core.memory.KnownApps
import dev.bidu.core.memory.MemoryStore
import dev.bidu.core.model.Capability
import dev.bidu.core.model.FixedClock
import dev.bidu.core.model.MemoryFact
import dev.bidu.core.model.PermissionStatus
import dev.bidu.core.policy.ConfirmationDecision
import dev.bidu.core.policy.ConfirmationGateway
import dev.bidu.core.policy.ConfirmationRequest
import dev.bidu.core.policy.PermissionGateway
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolContext
import dev.bidu.core.tool.ToolResult
import dev.bidu.core.tool.ToolSpec
import java.time.Instant
import java.time.ZoneId

/** Tuesday 27 Oct 2026, 10:00 in Asia/Kolkata. */
val TEST_NOW: Instant = Instant.parse("2026-10-27T04:30:00Z")
val IST: ZoneId = ZoneId.of("Asia/Kolkata")

fun testClock() = FixedClock(TEST_NOW, IST)

/** A scripted tool: [behavior] gets the validated args and the 1-based attempt number. */
class FakeTool(override val spec: ToolSpec, private val behavior: suspend (ToolArgs, Int) -> ToolResult) : Tool {
    val calls = mutableListOf<ToolArgs>()

    override suspend fun execute(args: ToolArgs, context: ToolContext): ToolResult {
        calls += args
        return behavior(args, calls.size)
    }
}

class RecordingLog : ActionLogSink {
    val entries = mutableListOf<ActionLogEntry>()
    override suspend fun append(entry: ActionLogEntry) {
        entries += entry
    }
}

class FakePermissions(private val denied: Set<Capability> = emptySet()) : PermissionGateway {
    override fun status(capability: Capability): PermissionStatus =
        if (capability in denied) PermissionStatus.DENIED else PermissionStatus.GRANTED
}

class FakeConfirmations(var decision: ConfirmationDecision = ConfirmationDecision.APPROVED) : ConfirmationGateway {
    val asked = mutableListOf<ConfirmationRequest>()
    override suspend fun confirm(request: ConfirmationRequest): ConfirmationDecision {
        asked += request
        return decision
    }
}

class FakeMemory(initial: Map<String, String> = emptyMap()) : MemoryStore {
    private val facts = initial.mapValuesTo(mutableMapOf()) { (k, v) -> MemoryFact(k, v, TEST_NOW) }
    override suspend fun get(key: String): MemoryFact? = facts[key]
    override suspend fun put(key: String, value: String): MemoryFact = MemoryFact(key, value, TEST_NOW).also { facts[key] = it }
    override suspend fun remove(key: String): Boolean = facts.remove(key) != null
    override suspend fun all(): List<MemoryFact> = facts.values.toList()
}

class FakeApps(private val list: List<KnownApp> = KnownApps.hints) : AppCatalog {
    override suspend fun apps(): List<KnownApp> = list
}
