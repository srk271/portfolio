package dev.bidu.core.tool

import dev.bidu.core.model.RiskLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolSpecTest {

    @Test
    fun `valid args are normalised`() {
        val result = BuiltinTools.CREATE_REMINDER.validate(ToolArgs.of("text" to "doodh lena hai", "at" to "1700000000000", "extra" to 1))
        val valid = assertInstanceOf(ValidationResult.Valid::class.java, result)
        assertEquals("doodh lena hai", valid.args.string("text"))
        assertEquals(1_700_000_000_000L, valid.args.long("at"))
        assertFalse(valid.args.has("extra"))
    }

    @Test
    fun `missing required and out of range args are reported together`() {
        val result = BuiltinTools.SET_ALARM.validate(ToolArgs.of("hour" to 25))
        val invalid = assertInstanceOf(ValidationResult.Invalid::class.java, result)
        assertEquals(2, invalid.errors.size)
        assertTrue(invalid.errors.any { it.contains("hour") })
        assertTrue(invalid.errors.any { it.contains("minute") })
    }

    @Test
    fun `allowed values are case-insensitive and lower-cased`() {
        val valid = assertInstanceOf(
            ValidationResult.Valid::class.java,
            BuiltinTools.FLASHLIGHT.validate(ToolArgs.of("state" to "ON")),
        )
        assertEquals("on", valid.args.string("state"))
        assertInstanceOf(ValidationResult.Invalid::class.java, BuiltinTools.FLASHLIGHT.validate(ToolArgs.of("state" to "dim")))
    }

    @Test
    fun `only idempotent tools may retry`() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolSpec(name = "send_money", description = "", idempotent = false, retry = RetryPolicy.TWICE, risk = RiskLevel.CRITICAL)
        }
    }

    @Test
    fun `builtin specs have unique names and no critical risk`() {
        assertEquals(BuiltinTools.all.size, BuiltinTools.all.map { it.name }.toSet().size)
        assertTrue(BuiltinTools.all.none { it.risk == RiskLevel.CRITICAL })
        assertNotNull(BuiltinTools.byName(BuiltinTools.BOOK_CAB_NAME))
    }
}
