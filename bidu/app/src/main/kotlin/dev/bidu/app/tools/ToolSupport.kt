package dev.bidu.app.tools

import dev.bidu.core.model.QuestionKind
import dev.bidu.core.model.UserQuestion
import dev.bidu.core.tool.ToolResult
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object Formats {
    private val TIME_24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val TIME_12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val DAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)
    private val DATE_LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

    fun time24(t: ZonedDateTime): String = TIME_24.format(t)
    fun time12(t: ZonedDateTime): String = TIME_12.format(t)
    fun time12(t: LocalTime): String = TIME_12.format(t)
    fun dayTime(at: Instant, zone: ZoneId): String = DAY_TIME.format(at.atZone(zone))
    fun dateLong(t: ZonedDateTime): String = DATE_LONG.format(t)
    fun weekday(t: ZonedDateTime): String = WEEKDAY.format(t)
}

internal fun choiceQuestion(text: String, options: List<String>): UserQuestion =
    UserQuestion(text = text, kind = QuestionKind.CHOICE, options = options)

internal fun freeTextQuestion(text: String): UserQuestion = UserQuestion(text = text, kind = QuestionKind.FREE_TEXT)

internal fun needsChoice(summary: String, question: String, options: List<String>, data: Map<String, String> = emptyMap()): ToolResult =
    ToolResult.needsUser(summary, choiceQuestion(question, options), data + mapOf("options" to options.joinToString(" | ")))

internal fun String.preview(max: Int = 60): String {
    val singleLine = replace(Regex("""\s+"""), " ").trim()
    return if (singleLine.length <= max) singleLine else singleLine.take(max - 1).trimEnd() + "…"
}
