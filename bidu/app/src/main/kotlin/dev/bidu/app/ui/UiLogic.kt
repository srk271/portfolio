package dev.bidu.app.ui

import dev.bidu.app.assistant.AssistantUiState
import dev.bidu.app.settings.WakePhrase
import dev.bidu.app.voice.ModelState
import dev.bidu.app.voice.TtsVoiceInfo
import dev.bidu.app.voice.VoiceMode
import dev.bidu.core.agent.AgentState
import java.util.Locale

/** What the big status line on the home screen says, derived only from live controller state. */
sealed interface HomeStatus {
    /** [state] is null when the controller is busy without a more specific engine state. */
    data class Working(val state: AgentState?) : HomeStatus
    data class Error(val reason: String?) : HomeStatus
    data class Listening(val phrases: List<String>) : HomeStatus
    data object Paused : HomeStatus
    data object Starting : HomeStatus
    data object ListeningOff : HomeStatus
    data object WakeWordUnavailable : HomeStatus
}

private val WORKING_STATES = setOf(
    AgentState.CAPTURING_COMMAND,
    AgentState.UNDERSTANDING,
    AgentState.PLANNING,
    AgentState.CHECKING_PERMISSIONS,
    AgentState.EXECUTING,
    AgentState.VERIFYING,
    AgentState.REPORTING,
)

fun homeStatus(ui: AssistantUiState, wakePhrases: List<WakePhrase>): HomeStatus {
    val voice = ui.voice
    return when {
        ui.agentState == AgentState.ERROR -> HomeStatus.Error(ui.lastError ?: voice.error)
        ui.busy || ui.agentState in WORKING_STATES || voice.mode == VoiceMode.CAPTURING -> HomeStatus.Working(
            when {
                ui.agentState in WORKING_STATES -> ui.agentState
                voice.mode == VoiceMode.CAPTURING -> AgentState.CAPTURING_COMMAND
                else -> null
            },
        )
        voice.error != null -> HomeStatus.Error(voice.error)
        !voice.serviceRunning -> HomeStatus.ListeningOff
        !voice.wakeWordAvailable -> HomeStatus.WakeWordUnavailable
        voice.mode == VoiceMode.WAKE_LISTENING -> HomeStatus.Listening(wakePhrases.map { displayPhrase(it.phrase) })
        voice.mode == VoiceMode.PAUSED -> HomeStatus.Paused
        else -> HomeStatus.Starting
    }
}

private val NOT_SPOKEN = Regex("[^\\p{L}\\p{M}\\p{N}'\\s]")
private val SPACES = Regex("\\s+")

/** Lowercase words only, single spaces: the form the wake-word grammar and the NLU expect. */
fun normalizeSpoken(text: String): String =
    text.replace(NOT_SPOKEN, " ").replace(SPACES, " ").trim().lowercase(Locale.ROOT)

/**
 * Builds a wake phrase from the editor fields. Variants are separated by commas, semicolons or new
 * lines; when none are given the phrase itself is the only variant. Returns null for a blank phrase.
 */
fun parseWakePhrase(phrase: String, variantsText: String): WakePhrase? {
    val name = normalizeSpoken(phrase)
    if (name.isEmpty()) return null
    val variants = variantsText.split(',', ';', '\n')
        .map(::normalizeSpoken)
        .filter { it.isNotEmpty() }
        .distinct()
    return WakePhrase(name, variants.ifEmpty { listOf(name) })
}

fun variantsText(phrase: WakePhrase): String = phrase.variants.joinToString(", ")

/** "hey bidu" -> "Hey Bidu". */
fun displayPhrase(phrase: String): String =
    phrase.split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }

fun normalizeMemoryKey(key: String): String = key.replace(SPACES, " ").trim().lowercase(Locale.ROOT)

private fun isIndianLanguageTag(tag: String): Boolean {
    val t = tag.replace('_', '-').lowercase(Locale.ROOT)
    return t == "hi" || t.startsWith("hi-") || t == "en-in" || t.startsWith("en-in-")
}

/**
 * Voices offered in settings: those of the chosen engine (all engines when null) in Hindi or Indian
 * English; when the engine has none of those, every voice of that engine.
 */
fun voiceChoices(voices: List<TtsVoiceInfo>, enginePackage: String?): List<TtsVoiceInfo> {
    val forEngine = if (enginePackage == null) voices else voices.filter { it.enginePackage == enginePackage }
    val indian = forEngine.filter { isIndianLanguageTag(it.languageTag) }
    return indian.ifEmpty { forEngine }.sortedWith(compareBy<TtsVoiceInfo>({ it.languageTag }, { it.name }))
}

/** Fraction for a determinate progress bar, or null when the total size is unknown. */
fun downloadFraction(state: ModelState.Downloading): Float? {
    val total = state.totalBytes ?: return null
    if (total <= 0L) return null
    return (state.downloadedBytes.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
}
