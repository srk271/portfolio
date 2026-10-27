package dev.bidu.app.assistant

import dev.bidu.app.voice.ModelState
import dev.bidu.app.voice.VoiceState
import dev.bidu.core.agent.AgentEvent
import dev.bidu.core.agent.AgentReport
import dev.bidu.core.agent.AgentState
import dev.bidu.core.agent.RequestOrigin
import dev.bidu.core.model.Capability
import dev.bidu.core.policy.ConfirmationRequest
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

enum class ActivityKind { REQUEST, STATE, TOOL, REPLY, ERROR }

/** One line of the activity log on the main screen. */
data class ActivityEntry(val at: Instant, val kind: ActivityKind, val text: String)

/** Everything the main screen and the service notification need, merged from engine and voice state. */
data class AssistantUiState(
    val agentState: AgentState = AgentState.IDLE,
    val voice: VoiceState = VoiceState(),
    val model: ModelState = ModelState.NotInstalled,
    val busy: Boolean = false,
    val lastTranscript: String? = null,
    val lastReport: AgentReport? = null,
    val pendingConfirmation: ConfirmationRequest? = null,
    val missingCapabilities: Set<Capability> = emptySet(),
    val lastError: String? = null,
    val activity: List<ActivityEntry> = emptyList(),
)

/**
 * Single entry point for anything that wants Bidu to do something: the foreground service after
 * a wake word, the main screen's text box and push-to-talk button, and notification actions.
 */
interface AssistantController {
    val uiState: StateFlow<AssistantUiState>
    val events: SharedFlow<AgentEvent>

    /** Queue a command. Requests run one at a time; a yes/no while a confirmation is pending answers it instead. */
    fun submitText(text: String, origin: RequestOrigin = RequestOrigin.TEXT)

    /** One-shot capture with the system recognizer, then submit. Works without the Vosk model. */
    fun pushToTalk()

    /** Start or stop background wake-word listening (the foreground service). */
    fun setListening(enabled: Boolean)

    /** Cancel the running request, if any. */
    fun cancel()

    /** Answer a pending confirmation. Returns false when it already expired. */
    fun resolveConfirmation(id: String, approved: Boolean): Boolean
}
