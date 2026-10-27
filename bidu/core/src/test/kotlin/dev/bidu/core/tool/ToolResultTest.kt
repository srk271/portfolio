package dev.bidu.core.tool

import dev.bidu.core.model.Capability
import dev.bidu.core.model.QuestionKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ToolResultTest {

    @Test
    fun `verified requires observable evidence`() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolResult(ToolOutcome.VERIFIED, "torch on")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolResult(
                ToolOutcome.VERIFIED,
                "opened app",
                evidence = listOf(Evidence(EvidenceKind.INTENT_DELIVERED, "launched")),
            )
        }
        val ok = ToolResult.observed("torch on", "TorchCallback reported enabled")
        assertEquals(ToolOutcome.VERIFIED, ok.outcome)
    }

    @Test
    fun `handed off carries the target package`() {
        val result = ToolResult.handedOff("Opened Uber", "com.ubercab", "android.intent.action.VIEW")
        assertEquals(ToolOutcome.HANDED_OFF, result.outcome)
        assertEquals("com.ubercab", result.data["target_package"])
    }

    @Test
    fun `needs user and failed require their payloads`() {
        assertThrows(IllegalArgumentException::class.java) { ToolResult(ToolOutcome.NEEDS_USER, "which one") }
        assertThrows(IllegalArgumentException::class.java) { ToolResult(ToolOutcome.FAILED, "broke") }
        val permission = ToolResult.needsPermission(setOf(Capability.READ_CONTACTS))
        assertEquals(QuestionKind.PERMISSION, permission.question?.kind)
        assertEquals(setOf(Capability.READ_CONTACTS), permission.question?.capabilities)
    }
}
