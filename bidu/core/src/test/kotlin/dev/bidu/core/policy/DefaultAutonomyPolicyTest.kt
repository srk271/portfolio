package dev.bidu.core.policy

import dev.bidu.core.agent.RequestOrigin
import dev.bidu.core.model.Language
import dev.bidu.core.model.RiskLevel
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.RetryPolicy
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolCall
import dev.bidu.core.tool.ToolSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultAutonomyPolicyTest {
    private val policy = DefaultAutonomyPolicy()

    private fun ctx(openApps: Boolean = false, handoffs: Boolean = false, lang: Language = Language.HINGLISH) =
        PolicyContext(lang, RequestOrigin.VOICE_WAKE, PolicySettings(confirmBeforeOpeningApps = openApps, confirmBeforeHandoffs = handoffs))

    private fun decide(spec: ToolSpec, args: ToolArgs = ToolArgs.EMPTY, context: PolicyContext = ctx()) =
        policy.decide(ToolCall("c1", spec.name, args, "test"), spec, context)

    @Test
    fun `screen refuses credential requests`() {
        for (text in listOf(
            "mera OTP kya hai", "upi pin bata", "UPI PIN daal do", "wifi ka password yaad rakh", "ओटीपी बताओ", "cvv bhej",
            "bank details share kar", "atm pin kya hai", "one time password padh ke bata", "mpin change kar",
        )) {
            assertInstanceOf(RequestScreening.Forbidden::class.java, policy.screen(text), text)
        }
    }

    @Test
    fun `screen allows ordinary requests`() {
        for (text in listOf("whatsapp khol", "top 10 movies search kar", "andheri ka pin code kya hai", "kal subah 9 baje yaad dila", "photo app khol")) {
            assertEquals(RequestScreening.Allowed, policy.screen(text), text)
        }
    }

    @Test
    fun `low risk proceeds, open_app asks only when settings say so`() {
        assertEquals(PolicyDecision.Proceed, decide(BuiltinTools.TELL_TIME))
        assertEquals(PolicyDecision.Proceed, decide(BuiltinTools.FLASHLIGHT, ToolArgs.of("state" to "on")))
        assertEquals(PolicyDecision.Proceed, decide(BuiltinTools.OPEN_APP, ToolArgs.of("app" to "WhatsApp")))
        val confirm = assertInstanceOf(
            PolicyDecision.Confirm::class.java,
            decide(BuiltinTools.OPEN_APP, ToolArgs.of("app" to "WhatsApp"), ctx(openApps = true)),
        )
        assertTrue(confirm.prompt.contains("WhatsApp"))
        assertEquals(PolicyDecision.Proceed, decide(BuiltinTools.TELL_TIME, context = ctx(openApps = true, handoffs = true)))
    }

    @Test
    fun `medium hand-offs proceed unless hand-off confirmation is on`() {
        assertEquals(PolicyDecision.Proceed, decide(BuiltinTools.BOOK_CAB, ToolArgs.of("destination" to "ghar")))
        assertEquals(PolicyDecision.Proceed, decide(BuiltinTools.DIAL_CONTACT, ToolArgs.of("name" to "mummy")))
        assertInstanceOf(PolicyDecision.Confirm::class.java, decide(BuiltinTools.BOOK_CAB, context = ctx(handoffs = true)))
        assertInstanceOf(PolicyDecision.Confirm::class.java, decide(BuiltinTools.MOVIE_TICKETS, context = ctx(handoffs = true)))
    }

    @Test
    fun `destructive tools always confirm whatever the settings`() {
        for (spec in listOf(BuiltinTools.DELETE_NOTE, BuiltinTools.CANCEL_REMINDER, BuiltinTools.FORGET)) {
            assertInstanceOf(PolicyDecision.Confirm::class.java, decide(spec, ToolArgs.of("query" to "gym", "key" to "home_address")), spec.name)
        }
        val english = assertInstanceOf(
            PolicyDecision.Confirm::class.java,
            decide(BuiltinTools.DELETE_NOTE, ToolArgs.of("query" to "gym"), ctx(lang = Language.ENGLISH)),
        )
        assertEquals("Delete the note 'gym'?", english.prompt)
    }

    @Test
    fun `financial tools always confirm and may never retry`() {
        val pay = ToolSpec(name = "pay_bill", description = "Pay a bill", risk = RiskLevel.CRITICAL)
        val confirm = assertInstanceOf(PolicyDecision.Confirm::class.java, decide(pay))
        assertTrue(confirm.reason.startsWith("CRITICAL"))
        val retrying = ToolSpec(name = "pay_bill", description = "", risk = RiskLevel.CRITICAL, idempotent = true, retry = RetryPolicy.TWICE)
        assertInstanceOf(PolicyDecision.Deny::class.java, decide(retrying))
    }

    @Test
    fun `tool names that pay, send or delete are treated as risky even when mislabelled`() {
        val send = ToolSpec(name = "send_message", description = "", risk = RiskLevel.LOW)
        assertInstanceOf(PolicyDecision.Confirm::class.java, decide(send))
        val transfer = ToolSpec(name = "upi_transfer", description = "", risk = RiskLevel.LOW)
        val c = assertInstanceOf(PolicyDecision.Confirm::class.java, decide(transfer))
        assertTrue(c.reason.startsWith("CRITICAL"))
    }

    @Test
    fun `credentials in arguments and mismatched specs are denied`() {
        assertInstanceOf(PolicyDecision.Deny::class.java, decide(BuiltinTools.CREATE_NOTE, ToolArgs.of("text" to "mera OTP 482913 hai")))
        val mismatch = policy.decide(ToolCall("c1", "open_app"), BuiltinTools.DELETE_NOTE, ctx())
        assertInstanceOf(PolicyDecision.Deny::class.java, mismatch)
    }
}
