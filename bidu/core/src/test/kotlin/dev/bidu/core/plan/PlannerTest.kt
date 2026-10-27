package dev.bidu.core.plan

import dev.bidu.core.IST
import dev.bidu.core.TEST_NOW
import dev.bidu.core.agent.AgentRequest
import dev.bidu.core.agent.RequestOrigin
import dev.bidu.core.memory.MemoryKeys
import dev.bidu.core.model.Language
import dev.bidu.core.model.MemoryFact
import dev.bidu.core.nlu.HinglishNlu
import dev.bidu.core.nlu.NluContext
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.ZonedDateTime

class PlannerTest {
    private val now: ZonedDateTime = TEST_NOW.atZone(IST)
    private val nlu = HinglishNlu()

    private fun request(text: String, memory: Map<String, String> = emptyMap(), lang: Language = Language.HINGLISH) = PlanRequest(
        request = AgentRequest("r1", text, RequestOrigin.TEXT, TEST_NOW),
        nlu = nlu.parse(text, NluContext(now)),
        tools = BuiltinTools.all,
        context = PlanContext(now, lang, memory.map { (k, v) -> MemoryFact(k, v, TEST_NOW) }),
    )

    private suspend fun plan(text: String, memory: Map<String, String> = emptyMap()) = RulePlanner().plan(request(text, memory))

    @Test
    fun `reminder becomes one create_reminder call with epoch millis`() = runTest {
        val plan = assertInstanceOf(Plan.Execute::class.java, plan("kal subah 9 baje yaad dila dena doodh lena hai"))
        val call = plan.calls.single()
        assertEquals(BuiltinTools.CREATE_REMINDER_NAME, call.tool)
        assertEquals("doodh lena hai", call.args.string("text"))
        assertEquals(now.plusDays(1).withHour(9).toInstant().toEpochMilli(), call.args.long("at"))
        assertEquals(PlanSource.RULES, plan.source)
    }

    @Test
    fun `cab to ghar resolves the saved address first`() = runTest {
        val plan = assertInstanceOf(
            Plan.Execute::class.java,
            plan("ghar jaane ke liye cab book kar", mapOf(MemoryKeys.HOME_ADDRESS to "12 MG Road, Andheri")),
        )
        assertEquals(listOf(BuiltinTools.RECALL_NAME, BuiltinTools.BOOK_CAB_NAME), plan.calls.map { it.tool })
        assertEquals(MemoryKeys.HOME_ADDRESS, plan.calls[0].args.string("key"))
        assertEquals("12 MG Road, Andheri", plan.calls[1].args.string("destination"))
    }

    @Test
    fun `cab with a provider and no saved place is one hand-off`() = runTest {
        val plan = assertInstanceOf(Plan.Execute::class.java, plan("airport ke liye uber bula"))
        val call = plan.calls.single()
        assertEquals("airport", call.args.string("destination"))
        assertEquals("uber", call.args.string("provider"))
    }

    @Test
    fun `movie with venue and time searches showtimes then opens the booking app`() = runTest {
        val plan = assertInstanceOf(Plan.Execute::class.java, plan("aaj raat 9 baje INOX Marine Lines mein do tickets book kar"))
        assertEquals(listOf(BuiltinTools.WEB_SEARCH_NAME, BuiltinTools.MOVIE_TICKETS_NAME), plan.calls.map { it.tool })
        val query = plan.calls[0].args.string("query")!!
        assertTrue(query.contains("INOX Marine Lines") && query.contains("showtimes") && query.contains("9:00 PM"), query)
    }

    @Test
    fun `incomplete and unsupported requests ask or decline`() = runTest {
        val clarify = assertInstanceOf(Plan.Clarify::class.java, plan("yaad dila dena doodh lena hai"))
        assertTrue(clarify.question.text.startsWith("Kab yaad dilaun"))
        val pay = assertInstanceOf(Plan.Decline::class.java, plan("paytm se 500 rupaye bhej"))
        assertEquals(DeclineReason.UNSUPPORTED, pay.reason)
        val gibberish = assertInstanceOf(Plan.Decline::class.java, plan("asdf qwerty zxcv"))
        assertEquals(DeclineReason.NOT_UNDERSTOOD, gibberish.reason)
    }

    @Test
    fun `tools missing from the request are not planned`() = runTest {
        val req = request("torch on kar").copy(tools = BuiltinTools.all.filterNot { it.name == BuiltinTools.FLASHLIGHT_NAME })
        assertEquals(DeclineReason.UNSUPPORTED, assertInstanceOf(Plan.Decline::class.java, RulePlanner().plan(req)).reason)
    }

    @Test
    fun `likes are merged with what is already remembered`() = runTest {
        val plan = assertInstanceOf(Plan.Execute::class.java, plan("mujhe chai pasand hai", mapOf("likes" to "biryani")))
        assertEquals("biryani, chai", plan.calls.single().args.string("value"))
    }

    @Test
    fun `help and cancel are plain replies in the reply language`() = runTest {
        assertInstanceOf(Plan.Reply::class.java, plan("help"))
        val english = RulePlanner().plan(request("rehne do", lang = Language.ENGLISH))
        assertEquals("Okay, never mind.", assertInstanceOf(Plan.Reply::class.java, english).text)
    }

    // ---- FallbackPlanner ----

    private class ScriptedCloud(val answer: () -> Plan) : Planner {
        var calls = 0
        override suspend fun plan(request: PlanRequest): Plan {
            calls++
            return answer()
        }
    }

    private val cloudPlan = Plan.Execute("weather", listOf(ToolCall("c1", BuiltinTools.WEB_SEARCH_NAME)), PlanSource.CLOUD, 0.8)

    @Test
    fun `known intents never reach the cloud`() = runTest {
        val cloud = ScriptedCloud { cloudPlan }
        val plan = FallbackPlanner(RulePlanner(), cloud) { true }.plan(request("torch on kar"))
        assertEquals(PlanSource.RULES, plan.source)
        assertEquals(0, cloud.calls)
    }

    @Test
    fun `unknown goes to the cloud only when allowed`() = runTest {
        val cloud = ScriptedCloud { cloudPlan }
        assertEquals(cloudPlan, FallbackPlanner(RulePlanner(), cloud) { true }.plan(request("asdf qwerty zxcv")))
        val offline = FallbackPlanner(RulePlanner(), cloud) { false }.plan(request("asdf qwerty zxcv"))
        assertEquals(DeclineReason.NOT_UNDERSTOOD, assertInstanceOf(Plan.Decline::class.java, offline).reason)
        assertEquals(1, cloud.calls)
    }

    @Test
    fun `cloud failure or foreign tools give an honest not-understood`() = runTest {
        val failing = FallbackPlanner(RulePlanner(), ScriptedCloud { throw IOException("offline") }) { true }.plan(request("asdf qwerty zxcv"))
        val decline = assertInstanceOf(Plan.Decline::class.java, failing)
        assertEquals(DeclineReason.NOT_UNDERSTOOD, decline.reason)
        assertTrue(decline.explanation.contains("cloud"))
        val rogue = Plan.Execute("x", listOf(ToolCall("c1", "transfer_money")), PlanSource.CLOUD, 0.9)
        val guarded = FallbackPlanner(RulePlanner(), ScriptedCloud { rogue }) { true }.plan(request("asdf qwerty zxcv"))
        assertInstanceOf(Plan.Decline::class.java, guarded)
    }

    @Test
    fun `deliberate refusals stay offline`() = runTest {
        val cloud = ScriptedCloud { cloudPlan }
        val plan = FallbackPlanner(RulePlanner(), cloud) { true }.plan(request("paytm se 500 rupaye bhej"))
        assertEquals(DeclineReason.UNSUPPORTED, assertInstanceOf(Plan.Decline::class.java, plan).reason)
        assertEquals(0, cloud.calls)
    }
}
