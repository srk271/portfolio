package dev.bidu.core.agent

/**
 * Strips secrets and secret-looking values before anything is logged or stored in the action log.
 * Conservative on purpose: a redacted year in a log line costs nothing, a leaked OTP does.
 */
object LogSanitizer {
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""sk-ant-[A-Za-z0-9_\-]{8,}""") to "[api-key]",
        Regex("""(?i)\b(otp|pin|upi pin|password|passcode|cvv|mpin)\b\s*(?:hai|is|:|=)?\s*\S+""") to "$1 [redacted]",
        Regex("""\+?\d[\d\s\-]{8,}\d""") to "[number]",
        Regex("""\b\d{4,8}\b""") to "[digits]",
    )

    fun sanitize(text: String): String =
        rules.fold(text) { acc, (regex, replacement) -> acc.replace(regex, replacement) }

    fun sanitize(values: Map<String, Any?>): String =
        values.entries.joinToString(", ") { (k, v) -> "$k=${sanitize(v.toString())}" }
}
