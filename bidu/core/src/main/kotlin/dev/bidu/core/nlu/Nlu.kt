package dev.bidu.core.nlu

import dev.bidu.core.model.Language
import dev.bidu.core.model.Script
import dev.bidu.core.model.UserQuestion
import java.time.ZonedDateTime

data class NluContext(
    val now: ZonedDateTime,
    /** Question Bidu asked last; lets "pehla wala" / "Rahul Sharma" resolve as an answer. */
    val pendingQuestion: UserQuestion? = null,
    /** Launcher labels and aliases of installed apps, lower-case, to improve app-name matching. */
    val knownAppNames: List<String> = emptyList(),
    /** Wake phrases to strip when the recognizer included them ("bhai whatsapp khol"). */
    val wakePhrases: List<String> = emptyList(),
)

data class NluResult(
    val intent: Intent,
    /** 0.0 - 1.0; the planner treats < 0.5 as "ask before acting" for anything above LOW risk. */
    val confidence: Double,
    val language: Language,
    val script: Script,
    /** Lower-cased, wake phrase removed, Devanagari digits normalised, filler words dropped. */
    val normalizedText: String,
    val rawText: String,
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be within 0..1" }
    }
}

/**
 * Rule-based offline understanding of Hindi, English and Hinglish, in Latin or Devanagari script.
 * Pure function: same text and context always give the same result.
 */
interface Nlu {
    fun parse(text: String, context: NluContext): NluResult
}

enum class TimeKind { ABSOLUTE, RELATIVE }

/** A time expression found in an utterance, resolved against `now`. [remainder] is the text without it. */
data class TimeMatch(
    val dateTime: ZonedDateTime,
    val kind: TimeKind,
    val matchedText: String,
    val remainder: String,
)

/**
 * Resolves expressions such as "kal subah 9 baje", "raat 9 baje", "10 minute baad", "parso",
 * "aaj shaam 6:30", "कल सुबह सात बजे". Returns null when the text carries no time.
 */
interface TimeExpressionParser {
    fun parse(text: String, now: ZonedDateTime): TimeMatch?
}
