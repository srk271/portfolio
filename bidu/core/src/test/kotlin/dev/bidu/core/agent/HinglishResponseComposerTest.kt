package dev.bidu.core.agent

import dev.bidu.core.IST
import dev.bidu.core.TEST_NOW
import dev.bidu.core.model.Language
import dev.bidu.core.model.ResponseStyle
import dev.bidu.core.plan.Plan
import dev.bidu.core.plan.PlanSource
import dev.bidu.core.tool.BuiltinTools
import dev.bidu.core.tool.ToolArgs
import dev.bidu.core.tool.ToolCall
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolExecution
import dev.bidu.core.tool.ToolOutcome
import dev.bidu.core.tool.ToolResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HinglishResponseComposerTest {
    private val composer = HinglishResponseComposer()
    private val now = TEST_NOW.atZone(IST)
    private val tomorrow9 = now.plusDays(1).withHour(9).withMinute(0)

    private fun exec(tool: String, result: ToolResult, args: ToolArgs = ToolArgs.EMPTY) =
        ToolExecution(ToolCall("c1", tool, args), result, 1, TEST_NOW, 5)

    private fun say(outcome: ToolOutcome, lang: Language, casual: Boolean = true, vararg executions: ToolExecution): String {
        val plan = Plan.Execute("goal", executions.map { it.call }.distinctBy { it.id }.ifEmpty { listOf(ToolCall("c1", "x")) }, PlanSource.RULES, 0.9)
        val input = ComposerInput(
            request = AgentRequest("r1", "text", RequestOrigin.TEXT, TEST_NOW),
            nlu = null,
            plan = plan,
            executions = executions.toList(),
            outcome = outcome,
            question = null,
            missingCapabilities = emptySet(),
            now = now,
        )
        return composer.compose(input, ResponseStyle(language = lang, casual = casual)).spoken
    }

    private val reminder = exec(
        BuiltinTools.CREATE_REMINDER_NAME,
        ToolResult.observed("Reminder saved", "row read back", mapOf("due" to tomorrow9.toOffsetDateTime().toString(), "exact" to "true")),
    )

    @Test
    fun `verified reminder in all three languages`() {
        assertEquals("Done bhai, kal subah 9 baje yaad dila dunga.", say(ToolOutcome.VERIFIED, Language.HINGLISH, true, reminder))
        assertEquals("Ho gaya, kal subah 9 baje yaad dila dunga.", say(ToolOutcome.VERIFIED, Language.HINGLISH, false, reminder))
        assertEquals("Done, I'll remind you tomorrow at 9 am.", say(ToolOutcome.VERIFIED, Language.ENGLISH, true, reminder))
        assertEquals("हो गया, कल सुबह 9 बजे याद दिला दूँगा।", say(ToolOutcome.VERIFIED, Language.HINDI, true, reminder))
    }

    @Test
    fun `inexact reminder says so`() {
        val inexact = exec(
            BuiltinTools.CREATE_REMINDER_NAME,
            ToolResult.observed("Reminder saved", "row read back", mapOf("due" to tomorrow9.toOffsetDateTime().toString(), "exact" to "false")),
        )
        assertTrue(say(ToolOutcome.VERIFIED, Language.HINGLISH, true, inexact).contains("late"))
    }

    @Test
    fun `cab hand-off is worded as a hand-off`() {
        val cab = exec(
            BuiltinTools.BOOK_CAB_NAME,
            ToolResult.handedOff("Opened Uber", "com.ubercab", "VIEW", mapOf("provider" to "Uber", "destination_prefilled" to "true")),
        )
        val spoken = say(ToolOutcome.HANDED_OFF, Language.HINGLISH, true, cab)
        assertEquals("Uber khol diya, destination set hai — ride wahan confirm karna.", spoken)
        assertFalse(spoken.contains("Done"))
        assertEquals("Opened Uber with the destination set — confirm the ride there.", say(ToolOutcome.HANDED_OFF, Language.ENGLISH, true, cab))
    }

    @Test
    fun `failures explain why and what next`() {
        val timeout = exec(BuiltinTools.FLASHLIGHT_NAME, ToolResult.failed(ToolErrorCode.TIMEOUT, "torch did not confirm", retryable = true))
        val spoken = say(ToolOutcome.FAILED, Language.HINGLISH, true, timeout)
        assertEquals("Torch ne time pe jawab nahi diya. Ek baar phir try karo.", spoken)
        val missingApp = exec(BuiltinTools.OPEN_APP_NAME, ToolResult.failed(ToolErrorCode.APP_NOT_INSTALLED, "No app"))
        assertTrue(say(ToolOutcome.FAILED, Language.ENGLISH, true, missingApp).startsWith("That app isn't on this phone"))
    }

    @Test
    fun `time and battery read measured data`() {
        val time = exec(BuiltinTools.TELL_TIME_NAME, ToolResult.measured("It is 7:05 PM", "clock", mapOf("hour" to "19", "minute" to "5")))
        assertEquals("Abhi shaam ke 7:05 baje hain.", say(ToolOutcome.VERIFIED, Language.HINGLISH, true, time))
        val battery = exec(BuiltinTools.BATTERY_LEVEL_NAME, ToolResult.measured("76%", "battery", mapOf("percent" to "76", "charging" to "true")))
        assertEquals("Battery is at 76%, and charging.", say(ToolOutcome.VERIFIED, Language.ENGLISH, true, battery))
    }

    @Test
    fun `when phrases`() {
        assertEquals("10 minute mein", composer.whenPhrase(now.plusMinutes(10), now, Language.HINGLISH))
        assertEquals("aaj raat 9 baje", composer.whenPhrase(now.withHour(21), now, Language.HINGLISH))
        assertEquals("parso shaam 6:30 baje", composer.whenPhrase(now.plusDays(2).withHour(18).withMinute(30), now, Language.HINGLISH))
        assertEquals("on Monday at 9 am", composer.whenPhrase(now.plusDays(6).withHour(9), now, Language.ENGLISH))
    }
}
