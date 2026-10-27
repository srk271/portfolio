package dev.bidu.core.agent

import dev.bidu.core.FakeApps
import dev.bidu.core.FakeConfirmations
import dev.bidu.core.FakeMemory
import dev.bidu.core.FakePermissions
import dev.bidu.core.FakeTool
import dev.bidu.core.RecordingLog
import dev.bidu.core.TEST_NOW
import dev.bidu.core.memory.MemoryKeys
import dev.bidu.core.model.Capability
import dev.bidu.core.model.QuestionKind
import dev.bidu.core.model.UserQuestion
import dev.bidu.core.nlu.HinglishNlu
import dev.bidu.core.plan.DeclineReason
import dev.bidu.core.plan.Plan
import dev.bidu.core.plan.PlanRequest
import dev.bidu.core.plan.Planner
import dev.bidu.core.plan.RulePlanner
import dev.bidu.core.policy.ConfirmationDecision
import dev.bidu.core.policy.DefaultAutonomyPolicy
import dev.bidu.core.testClock
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.DefaultToolRegistry
import dev.bidu.core.tool.Evidence
import dev.bidu.core.tool.EvidenceKind
import dev.bidu.core.tool.Tool
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolOutcome
import dev.bidu.core.tool.ToolResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAgentEngineTest {

    private class Harness(
        val scope: TestScope,
        tools: List<Tool>,
        denied: Set<Capability> = emptySet(),
        memory: Map<String, String> = emptyMap(),
        planner: Planner = RulePlanner(),
    ) {
        val log = RecordingLog()
        val confirmations = FakeConfirmations()
        val clock = testClock()
        val engine = DefaultAgentEngine(
            EngineDependencies(
                nlu = HinglishNlu(),
                planner = planner,
                registry = DefaultToolRegistry(tools, log, clock),
                permissions = FakePermissions(denied),
                confirmations = confirmations,
                policy = DefaultAutonomyPolicy(),
                composer = HinglishResponseComposer(),
                actionLog = log,
                memory = FakeMemory(memory),
                appCatalog = FakeApps(),
                clock = clock,
                settings = { EngineSettings() },
            ),
            scope.backgroundScope,
        )
        val events = mutableListOf<AgentEvent>()

        init {
            scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) { engine.events.collect { events += it } }
        }

        var n = 0
        suspend fun say(text: String): AgentReport = engine.handle(AgentRequest("r${++n}", text, RequestOrigin.TEXT, TEST_NOW))

        fun states(requestId: String) = events.filterIsInstance<AgentEvent.StateChanged>().filter { it.requestId == requestId }.map { it.state }
    }

    private val torch = FakeTool(BuiltinTools.FLASHLIGHT) { args, _ ->
        ToolResult.verified("Torch ${args.string("state")}", Evidence(EvidenceKind.SYSTEM_CALLBACK, "torch callback"), mapOf("state" to args.string("state")!!))
    }

    private fun reminderTool() = FakeTool(BuiltinTools.CREATE_REMINDER) { args, _ ->
        val due = java.time.Instant.ofEpochMilli(args.requireLong("at")).atZone(dev.bidu.core.IST)
        ToolResult.observed("Reminder saved", "row read back", mapOf("due" to due.toOffsetDateTime().toString(), "exact" to "true", "text" to args.requireString("text")))
    }

    private fun cabTool() = FakeTool(BuiltinTools.BOOK_CAB) { _, _ ->
        ToolResult.handedOff("Opened Uber", "com.ubercab", "VIEW", mapOf("provider" to "Uber", "destination_prefilled" to "true"))
    }

    private fun recallTool(memory: Map<String, String>) = FakeTool(BuiltinTools.RECALL) { args, _ ->
        val key = args.requireString("key")
        memory[key]?.let { ToolResult.observed("Recalled $key", "memory read", mapOf("key" to key, "value" to it)) }
            ?: ToolResult.failed(ToolErrorCode.NOT_FOUND, "Nothing saved for '$key'")
    }

    @Test
    fun `verified request walks every state and reports honestly`() = runTest {
        val h = Harness(this, listOf(torch))
        val report = h.say("torch on kar")
        assertEquals(ToolOutcome.VERIFIED, report.outcome)
        assertEquals("Torch on kar di.", report.spoken)
        assertEquals(
            listOf(
                AgentState.UNDERSTANDING, AgentState.PLANNING, AgentState.CHECKING_PERMISSIONS, AgentState.EXECUTING,
                AgentState.VERIFYING, AgentState.REPORTING, AgentState.IDLE,
            ),
            h.states("r1"),
        )
        assertInstanceOf(AgentEvent.Completed::class.java, h.events.last())
        assertEquals(AgentState.IDLE, h.engine.state.value)
        assertEquals(1, h.log.entries.size)
    }

    @Test
    fun `reminder success is only claimed after the tool verified it`() = runTest {
        val h = Harness(this, listOf(reminderTool()))
        val report = h.say("kal subah 9 baje yaad dila dena doodh lena hai")
        assertEquals(ToolOutcome.VERIFIED, report.outcome)
        assertEquals("Done bhai, kal subah 9 baje yaad dila dunga.", report.spoken)
    }

    @Test
    fun `cab to home recalls the address then hands off`() = runTest {
        val memory = mapOf(MemoryKeys.HOME_ADDRESS to "12 MG Road, Andheri")
        val cab = cabTool()
        val h = Harness(this, listOf(recallTool(memory), cab), memory = memory)
        val report = h.say("ghar jaane ke liye cab book kar")
        assertEquals(ToolOutcome.HANDED_OFF, report.outcome)
        assertEquals("Uber khol diya, destination set hai — ride wahan confirm karna.", report.spoken)
        assertEquals("12 MG Road, Andheri", cab.calls.single().string("destination"))
        assertEquals(2, report.executions.size)
    }

    @Test
    fun `missing home address stops the plan before the cab app`() = runTest {
        val cab = cabTool()
        val h = Harness(this, listOf(recallTool(emptyMap()), cab))
        val report = h.say("ghar ke liye cab bula")
        assertEquals(ToolOutcome.FAILED, report.outcome)
        assertTrue(cab.calls.isEmpty())
        assertTrue(report.spoken.contains("address save nahi hai"), report.spoken)
    }

    @Test
    fun `missing permission asks before anything runs`() = runTest {
        val tool = reminderTool()
        val h = Harness(this, listOf(tool), denied = setOf(Capability.POST_NOTIFICATIONS))
        val report = h.say("10 minute baad yaad dila chai")
        assertEquals(ToolOutcome.NEEDS_USER, report.outcome)
        assertEquals(setOf(Capability.POST_NOTIFICATIONS), report.missingCapabilities)
        assertEquals(QuestionKind.PERMISSION, report.question?.kind)
        assertTrue(tool.calls.isEmpty())
        assertTrue(h.events.any { it is AgentEvent.PermissionMissing })
    }

    @Test
    fun `destructive call needs a yes`() = runTest {
        val delete = FakeTool(BuiltinTools.DELETE_NOTE) { _, _ -> ToolResult.observed("Deleted", "row gone") }
        val h = Harness(this, listOf(delete))
        h.confirmations.decision = ConfirmationDecision.REJECTED
        val rejected = h.say("gym wala note delete kar")
        assertEquals(ToolOutcome.CANCELLED, rejected.outcome)
        assertEquals("Theek hai, nahi kiya.", rejected.spoken)
        assertTrue(delete.calls.isEmpty())
        assertEquals(1, h.confirmations.asked.size)

        h.confirmations.decision = ConfirmationDecision.APPROVED
        val approved = h.say("gym wala note delete kar")
        assertEquals(ToolOutcome.VERIFIED, approved.outcome)
        assertEquals("gym", delete.calls.single().string("query"))
    }

    @Test
    fun `forbidden requests are declined before understanding`() = runTest {
        val h = Harness(this, listOf(torch))
        val report = h.say("bhai mera UPI PIN bata")
        assertEquals(ToolOutcome.FAILED, report.outcome)
        assertEquals(DeclineReason.FORBIDDEN, assertInstanceOf(Plan.Decline::class.java, report.plan).reason)
        assertFalse(h.events.any { it is AgentEvent.Understood })
    }

    @Test
    fun `first failure stops the rest of the plan`() = runTest {
        val search = FakeTool(BuiltinTools.WEB_SEARCH) { _, _ -> ToolResult.failed(ToolErrorCode.UNAVAILABLE, "No browser") }
        val tickets = FakeTool(BuiltinTools.MOVIE_TICKETS) { _, _ -> ToolResult.handedOff("Opened BookMyShow", "com.bt.bms", "MAIN") }
        val h = Harness(this, listOf(search, tickets))
        val report = h.say("aaj raat 9 baje INOX Marine Lines mein do tickets book kar")
        assertEquals(ToolOutcome.FAILED, report.outcome)
        assertTrue(tickets.calls.isEmpty())
        assertFalse(report.spoken.contains("Done"))
    }

    @Test
    fun `movie plan hands off twice and never says done`() = runTest {
        val search = FakeTool(BuiltinTools.WEB_SEARCH) { _, _ -> ToolResult.handedOff("Opened search", "com.android.chrome", "VIEW") }
        val tickets = FakeTool(BuiltinTools.MOVIE_TICKETS) { _, _ ->
            ToolResult.handedOff("Opened BookMyShow", "com.bt.bms", "MAIN", mapOf("app" to "BookMyShow"))
        }
        val h = Harness(this, listOf(search, tickets))
        val report = h.say("aaj raat 9 baje INOX Marine Lines mein do tickets book kar")
        assertEquals(ToolOutcome.HANDED_OFF, report.outcome)
        assertTrue(report.spoken.startsWith("Showtimes ka search aur BookMyShow khol diya"), report.spoken)
        assertTrue(report.spoken.contains("2 seats"), report.spoken)
        assertTrue(report.spoken.contains("payment"), report.spoken)
    }

    @Test
    fun `cancel stops a running tool and reports CANCELLED`() = runTest {
        val started = CompletableDeferred<Unit>()
        val hanging = FakeTool(BuiltinTools.WEB_SEARCH) { _, _ ->
            started.complete(Unit)
            awaitCancellation()
        }
        val h = Harness(this, listOf(hanging))
        val pending = async { h.say("google kar mumbai weather") }
        started.await()
        h.engine.cancel()
        val report = pending.await()
        assertEquals(ToolOutcome.CANCELLED, report.outcome)
        assertEquals("Theek hai, rok diya.", report.spoken)
        assertEquals(ToolOutcome.CANCELLED, h.log.entries.single().outcome)
        assertEquals(AgentState.IDLE, h.engine.state.value)
        assertInstanceOf(AgentEvent.Completed::class.java, h.events.last())
    }

    @Test
    fun `unexpected exception enters ERROR and the next request recovers`() = runTest {
        var explode = true
        val planner = object : Planner {
            override suspend fun plan(request: PlanRequest): Plan {
                if (explode) error("planner bug")
                return RulePlanner().plan(request)
            }
        }
        val h = Harness(this, listOf(torch), planner = planner)
        val broken = h.say("torch on kar")
        assertEquals(ToolOutcome.FAILED, broken.outcome)
        assertEquals(AgentState.ERROR, h.engine.state.value)
        explode = false
        assertEquals(ToolOutcome.VERIFIED, h.say("torch on kar").outcome)
        assertEquals(AgentState.IDLE, h.engine.state.value)
    }

    @Test
    fun `not understood is reported as such`() = runTest {
        val h = Harness(this, listOf(torch))
        val report = h.say("asdf qwerty zxcv")
        assertEquals(ToolOutcome.FAILED, report.outcome)
        assertTrue(report.spoken.startsWith("Samajh nahi aaya"))
    }

    @Test
    fun `clarification is merged with the answer`() = runTest {
        val tool = reminderTool()
        val h = Harness(this, listOf(tool))
        val ask = h.say("yaad dila dena doodh lena hai")
        assertEquals(ToolOutcome.NEEDS_USER, ask.outcome)
        val done = h.say("kal subah 9 baje")
        assertEquals(ToolOutcome.VERIFIED, done.outcome)
        assertEquals("doodh lena hai", tool.calls.single().string("text"))
    }

    @Test
    fun `choice question resumes the stopped call with the picked option`() = runTest {
        val dial = FakeTool(BuiltinTools.DIAL_CONTACT) { args, _ ->
            when (args.string("name")) {
                "rahul" -> ToolResult.needsUser("2 matches", UserQuestion("Kaunsa Rahul?", QuestionKind.CHOICE, listOf("Rahul Sharma", "Rahul Verma")))
                else -> ToolResult.handedOff("Opened dialer", "com.dialer", "DIAL", mapOf("name" to args.string("name")!!))
            }
        }
        val h = Harness(this, listOf(dial))
        val ask = h.say("rahul ko call kar")
        assertEquals(ToolOutcome.NEEDS_USER, ask.outcome)
        assertTrue(ask.spoken.contains("Rahul Verma"))
        val done = h.say("dusra wala")
        assertEquals(ToolOutcome.HANDED_OFF, done.outcome)
        assertEquals("Rahul Verma", dial.calls.last().string("name"))
        assertTrue(done.spoken.contains("call ka button tum dabana"), done.spoken)
    }
}
