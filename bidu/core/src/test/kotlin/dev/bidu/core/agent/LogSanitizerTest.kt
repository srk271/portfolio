package dev.bidu.core.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogSanitizerTest {

    @Test
    fun `api keys, otps, pins and phone numbers are redacted`() {
        val text = "key sk-ant-abcdefghijklmnop otp 482913 bhejo, upi pin 1234 hai, call +91 98765 43210"
        val out = LogSanitizer.sanitize(text)
        assertFalse(out.contains("sk-ant-abcdefghijklmnop"))
        assertFalse(out.contains("482913"))
        assertFalse(out.contains("1234"))
        assertFalse(out.contains("98765"))
        assertTrue(out.contains("[api-key]"))
    }

    @Test
    fun `ordinary text passes through`() {
        assertEquals("whatsapp khol bhai", LogSanitizer.sanitize("whatsapp khol bhai"))
        assertEquals("app=whatsapp, at=[digits]", LogSanitizer.sanitize(mapOf("app" to "whatsapp", "at" to 1700000)))
    }
}
