package dev.bidu.app.assistant

import android.content.Context
import android.util.Log
import dev.bidu.app.R
import dev.bidu.app.data.HistoryRepository
import dev.bidu.app.notifications.NotificationIds
import dev.bidu.app.permissions.DevicePermissions
import dev.bidu.app.settings.SettingsRepository
import dev.bidu.app.voice.BiduForegroundService
import dev.bidu.app.voice.CaptureResult
import dev.bidu.app.voice.DefaultVoiceController
import dev.bidu.app.voice.ListeningNotification
import dev.bidu.app.voice.ModelState
import dev.bidu.app.voice.SpeakResult
import dev.bidu.app.voice.Speaker
import dev.bidu.app.voice.VoiceController
import dev.bidu.app.voice.VoiceEvent
import dev.bidu.app.voice.VoiceMode
import dev.bidu.app.voice.VoiceState
import dev.bidu.app.voice.VoskModelManager
import dev.bidu.core.agent.AgentEngine
import dev.bidu.core.agent.AgentEvent
import dev.bidu.core.agent.AgentReport
import dev.bidu.core.agent.AgentRequest
import dev.bidu.core.agent.AgentState
import dev.bidu.core.agent.ConversationTurn
import dev.bidu.core.agent.LogSanitizer
import dev.bidu.core.agent.RequestOrigin
import dev.bidu.core.model.Capability
import dev.bidu.core.model.Clock
import dev.bidu.core.model.Language
import dev.bidu.core.model.QuestionKind
import dev.bidu.core.model.UserQuestion
import dev.bidu.core.nlu.Intent
import dev.bidu.core.nlu.Nlu
import dev.bidu.core.nlu.NluContext
import dev.bidu.core.plan.DeclineReason
import dev.bidu.core.plan.Plan
import dev.bidu.core.policy.ConfirmationDecision
import dev.bidu.core.policy.ConfirmationRequest
import dev.bidu.core.policy.PendingConfirmations
import dev.bidu.core.tool.ToolOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Routes every command (wake word, push-to-talk, typed text, notification) through the agent
 * engine one at a time, keeps the merged UI state, stores history, speaks reports and runs
 * spoken confirmations through the shared [PendingConfirmations] hub.
 *
 * Microphone ordering: a voice turn holds wake listening paused from the wake word until its
 * report has been spoken; every TTS utterance and every capture also runs under [audioTurn] so
 * Bidu never records itself.
 */
class DefaultAssistantController(
    context: Context,
    private val engine: AgentEngine,
    private val nlu: Nlu,
    private val voice: VoiceController,
    private val speaker: Speaker,
    private val history: HistoryRepository,
    private val confirmations: PendingConfirmations,
    private val settings: SettingsRepository,
    private val permissions: DevicePermissions,
    private val voskModels: VoskModelManager,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : AssistantController {

    private val appContext = context.applicationContext

    private data class Local(
        val processing: Boolean = false,
        val voiceTurn: Boolean = false,
        val lastTranscript: String? = null,
        val lastReport: AgentReport? = null,
        val missingCapabilities: Set<Capability> = emptySet(),
        val lastError: String? = null,
        val activity: List<ActivityEntry> = emptyList(),
    )

    private class Queued(val text: String, val origin: RequestOrigin) {
        val done = CompletableDeferred<AgentReport?>()
    }

    private val local = MutableStateFlow(Local())

    override val uiState: StateFlow<AssistantUiState> =
        combine(local, engine.state, voice.state, voskModels.state, confirmations.pending) { l, engineState, voiceState, model, pending ->
            merge(l, engineState, voiceState, model, pending)
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            merge(local.value, engine.state.value, voice.state.value, voskModels.state.value, confirmations.pending.value),
        )

    override val events: SharedFlow<AgentEvent> = engine.events

    private val queue = Channel<Queued>(MAX_QUEUED)
    private val audioTurn = Mutex()
    private val holdLock = Any()
    private var wakeHolds = 0
    private val voiceTurnActive = AtomicBoolean(false)

    @Volatile private var currentRequest: AgentRequest? = null

    // Touched only from the confirmations collector.
    private var confirmationJob: Job? = null
    private var shownConfirmationId: String? = null

    init {
        scope.launch { for (item in queue) process(item) }
        scope.launch { voice.events.collect { onVoiceEvent(it) } }
        scope.launch { engine.events.collect { onEngineEvent(it) } }
        scope.launch { confirmations.pending.collect { onPendingConfirmation(it) } }
    }

    override fun submitText(text: String, origin: RequestOrigin) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        scope.launch { enqueue(clean, origin) }
    }

    override fun pushToTalk() {
        startVoiceTurn(RequestOrigin.PUSH_TO_TALK)
    }

    override fun setListening(enabled: Boolean) {
        if (enabled) {
            val missing = permissions.missing(LISTENING_CAPABILITIES)
            if (missing.isNotEmpty()) {
                local.update {
                    it.copy(
                        missingCapabilities = it.missingCapabilities + missing,
                        lastError = appContext.getString(R.string.voice_listening_needs, missing.joinToString { c -> c.userLabel }),
                    )
                }
                return
            }
            local.update { it.copy(missingCapabilities = it.missingCapabilities - LISTENING_CAPABILITIES, lastError = null) }
            scope.launch { settings.update { it.copy(listeningEnabled = true) } }
            if (!BiduForegroundService.start(appContext)) {
                local.update { it.copy(lastError = appContext.getString(R.string.voice_listening_blocked)) }
                ListeningNotification.postResume(appContext)
            }
        } else {
            // Release the microphone first; persisting the choice can follow.
            voice.stopWakeListening()
            BiduForegroundService.stop(appContext)
            ListeningNotification.cancel(appContext, NotificationIds.RESUME)
            scope.launch { settings.update { it.copy(listeningEnabled = false) } }
        }
    }

    override fun cancel() {
        voice.cancelCapture()
        engine.cancel()
        speaker.stop()
    }

    override fun resolveConfirmation(id: String, approved: Boolean): Boolean =
        confirmations.resolve(id, if (approved) ConfirmationDecision.APPROVED else ConfirmationDecision.REJECTED)

    // ---- Requests ---------------------------------------------------------------------------

    /** Returns the request's completion, or null when the text answered a confirmation or was dropped. */
    private fun enqueue(text: String, origin: RequestOrigin): CompletableDeferred<AgentReport?>? {
        local.update { it.copy(lastTranscript = text) }
        if (answerPendingConfirmation(text)) return null
        val item = Queued(text, origin)
        if (queue.trySend(item).isFailure) {
            local.update { it.copy(lastError = appContext.getString(R.string.voice_queue_full)) }
            return null
        }
        return item.done
    }

    private suspend fun process(item: Queued) {
        val request = AgentRequest(
            id = UUID.randomUUID().toString(),
            text = item.text,
            origin = item.origin,
            receivedAt = clock.now(),
        )
        currentRequest = request
        local.update { it.copy(processing = true, lastError = null) }
        addActivity(ActivityKind.REQUEST, item.text)
        var report: AgentReport? = null
        try {
            val finished = try {
                engine.handle(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "engine failed: ${e.javaClass.simpleName}")
                failureReport(request)
            }
            report = finished
            currentRequest = null
            local.update {
                it.copy(
                    processing = false,
                    lastReport = finished,
                    missingCapabilities = finished.missingCapabilities,
                )
            }
            addActivity(if (finished.outcome == ToolOutcome.FAILED) ActivityKind.ERROR else ActivityKind.REPLY, finished.display)
            persist(request, finished)
            if (finished.missingCapabilities.isNotEmpty() && BiduForegroundService.running.value) {
                ListeningNotification.post(appContext, NotificationIds.RESULT, ListeningNotification.permissionNeeded(appContext, finished.missingCapabilities))
            }
            say(finished.spoken, replyLanguage())
        } finally {
            currentRequest = null
            local.update { it.copy(processing = false) }
            item.done.complete(report)
        }
    }

    private suspend fun persist(request: AgentRequest, report: AgentReport) {
        val forbidden = (report.plan as? Plan.Decline)?.reason == DeclineReason.FORBIDDEN
        val turn = ConversationTurn(
            id = 0L,
            requestId = request.id,
            // A refused credential request may itself contain the secret; keep only a scrubbed copy.
            requestText = if (forbidden) LogSanitizer.sanitize(request.text) else request.text,
            origin = request.origin,
            outcome = report.outcome,
            spoken = report.spoken,
            display = report.display,
            at = report.finishedAt,
        )
        try {
            history.append(turn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "history append failed: ${e.javaClass.simpleName}")
        }
    }

    private fun failureReport(request: AgentRequest): AgentReport {
        val spoken = localized(R.string.voice_spoken_error_hinglish, R.string.voice_spoken_error_english, R.string.voice_spoken_error_hindi)
        return AgentReport(
            requestId = request.id,
            outcome = ToolOutcome.FAILED,
            spoken = spoken,
            display = spoken,
            finishedAt = clock.now(),
        )
    }

    // ---- Voice turns ------------------------------------------------------------------------

    private fun onVoiceEvent(event: VoiceEvent) {
        when (event) {
            is VoiceEvent.WakeDetected -> {
                addActivity(ActivityKind.STATE, "Wake word: ${event.phrase}")
                startVoiceTurn(RequestOrigin.VOICE_WAKE)
            }
            is VoiceEvent.Error -> addActivity(ActivityKind.ERROR, event.message)
        }
    }

    private fun startVoiceTurn(origin: RequestOrigin) {
        if (!voiceTurnActive.compareAndSet(false, true)) return
        scope.launch {
            try {
                local.update { it.copy(voiceTurn = true, lastError = null) }
                withWakeHold<Unit> {
                    when (val result = audioTurn.withLock { voice.captureCommand() }) {
                        is CaptureResult.Text -> {
                            val done = enqueue(result.text, origin)
                            // Keep the wake word paused until this command's reply has been spoken.
                            done?.await()
                        }
                        CaptureResult.NoSpeech -> {
                            say(notUnderstood(), replyLanguage())
                        }
                        is CaptureResult.Failed -> {
                            if (result.reason != DefaultVoiceController.CANCELLED_REASON) {
                                local.update { it.copy(lastError = result.reason) }
                                addActivity(ActivityKind.ERROR, result.reason)
                                say(
                                    localized(
                                        R.string.voice_spoken_capture_failed_hinglish,
                                        R.string.voice_spoken_capture_failed_english,
                                        R.string.voice_spoken_capture_failed_hindi,
                                    ),
                                    replyLanguage(),
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "voice turn failed: ${e.javaClass.simpleName}")
            } finally {
                voiceTurnActive.set(false)
                local.update { it.copy(voiceTurn = false) }
            }
        }
    }

    /** Speaks with the wake word paused and the microphone otherwise idle. */
    private suspend fun say(text: String, language: Language): SpeakResult {
        if (text.isBlank()) return SpeakResult.SPOKEN
        return audioTurn.withLock {
            withWakeHold { speaker.speak(text, language) }
        }
    }

    private inline fun <T> withWakeHold(block: () -> T): T {
        synchronized(holdLock) {
            if (wakeHolds++ == 0) voice.pauseWakeListening()
        }
        try {
            return block()
        } finally {
            synchronized(holdLock) {
                if (--wakeHolds == 0) voice.resumeWakeListening()
            }
        }
    }

    // ---- Confirmations ----------------------------------------------------------------------

    private fun onPendingConfirmation(request: ConfirmationRequest?) {
        if (request == null) {
            confirmationJob?.cancel()
            confirmationJob = null
            if (shownConfirmationId != null) ListeningNotification.cancel(appContext, NotificationIds.CONFIRMATION)
            shownConfirmationId = null
            return
        }
        if (request.id == shownConfirmationId) return
        shownConfirmationId = request.id
        confirmationJob?.cancel()
        confirmationJob = null
        ListeningNotification.post(appContext, NotificationIds.CONFIRMATION, ListeningNotification.confirmation(appContext, request))
        addActivity(ActivityKind.STATE, request.prompt)
        val origin = currentRequest?.origin
        if (origin != null && origin != RequestOrigin.TEXT) {
            confirmationJob = scope.launch { askByVoice(request) }
        }
    }

    /** Speaks the prompt and listens for "haan"/"nahi"; the notification and the in-app dialog stay available. */
    private suspend fun askByVoice(request: ConfirmationRequest) {
        withWakeHold<Unit> {
            say(request.prompt, request.language)
            for (attempt in 0 until VOICE_CONFIRM_ATTEMPTS) {
                if (!stillPending(request)) return
                val result = audioTurn.withLock { voice.captureCommand(CONFIRM_CAPTURE_MS) }
                if (!stillPending(request)) return
                when (result) {
                    is CaptureResult.Text -> {
                        local.update { it.copy(lastTranscript = result.text) }
                        val decision = classifyAnswer(result.text, request)
                        if (decision != null) {
                            confirmations.resolve(request.id, decision)
                            return
                        }
                    }
                    CaptureResult.NoSpeech -> Unit
                    is CaptureResult.Failed -> return
                }
                if (attempt < VOICE_CONFIRM_ATTEMPTS - 1) {
                    say(
                        localized(R.string.voice_spoken_yes_or_no_hinglish, R.string.voice_spoken_yes_or_no_english, R.string.voice_spoken_yes_or_no_hindi),
                        request.language,
                    )
                }
            }
        }
    }

    private fun stillPending(request: ConfirmationRequest): Boolean = confirmations.pending.value?.id == request.id

    /**
     * Text arriving while a confirmation is pending: yes/no answers it; "cancel" rejects it; any
     * other command rejects it too (so the engine does not sit waiting) and then runs normally.
     */
    private fun answerPendingConfirmation(text: String): Boolean {
        val pending = confirmations.pending.value ?: return false
        val decision = classifyAnswer(text, pending)
        if (decision != null) {
            confirmations.resolve(pending.id, decision)
            return true
        }
        confirmations.resolve(pending.id, ConfirmationDecision.REJECTED)
        return false
    }

    private fun classifyAnswer(text: String, request: ConfirmationRequest): ConfirmationDecision? {
        val parsed = try {
            nlu.parse(
                text,
                NluContext(
                    now = clock.nowZoned(),
                    pendingQuestion = UserQuestion(text = request.prompt, kind = QuestionKind.CHOICE, options = YES_NO_OPTIONS),
                    wakePhrases = settings.current().wakePhrases.map { it.phrase },
                ),
            ).intent
        } catch (e: RuntimeException) {
            Log.w(TAG, "nlu failed on answer: ${e.javaClass.simpleName}")
            null
        }
        return when (parsed) {
            is Intent.Confirm -> if (parsed.approved) ConfirmationDecision.APPROVED else ConfirmationDecision.REJECTED
            Intent.Cancel -> ConfirmationDecision.REJECTED
            else -> simpleYesNo(text)
        }
    }

    // ---- Engine events and UI state -----------------------------------------------------------

    private fun onEngineEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.ToolStarted -> addActivity(ActivityKind.TOOL, event.call.purpose.ifBlank { event.call.tool })
            is AgentEvent.ToolFinished -> addActivity(
                ActivityKind.TOOL,
                "${event.execution.call.tool}: ${event.execution.result.outcome.name.lowercase(Locale.ROOT)}",
            )
            is AgentEvent.PermissionMissing -> local.update { it.copy(missingCapabilities = event.capabilities) }
            is AgentEvent.Progress -> addActivity(ActivityKind.STATE, event.message)
            else -> Unit
        }
    }

    private fun addActivity(kind: ActivityKind, text: String) {
        if (text.isBlank()) return
        val entry = ActivityEntry(at = clock.now(), kind = kind, text = text)
        local.update { it.copy(activity = (it.activity + entry).takeLast(MAX_ACTIVITY)) }
    }

    private fun merge(
        l: Local,
        engineState: AgentState,
        voiceState: VoiceState,
        model: ModelState,
        pending: ConfirmationRequest?,
    ): AssistantUiState {
        val agentState = when {
            voiceState.mode == VoiceMode.CAPTURING -> AgentState.CAPTURING_COMMAND
            engineState in ENGINE_WORKING -> engineState
            voiceState.mode == VoiceMode.WAKE_LISTENING -> AgentState.LISTENING_WAKE
            engineState == AgentState.ERROR -> AgentState.ERROR
            else -> AgentState.IDLE
        }
        return AssistantUiState(
            agentState = agentState,
            voice = voiceState,
            model = model,
            busy = l.processing || l.voiceTurn,
            lastTranscript = l.lastTranscript,
            lastReport = l.lastReport,
            pendingConfirmation = pending,
            missingCapabilities = l.missingCapabilities,
            lastError = l.lastError,
            activity = l.activity,
        )
    }

    private fun replyLanguage(): Language = settings.current().responseLanguage

    private fun notUnderstood(): String =
        localized(R.string.voice_spoken_not_understood_hinglish, R.string.voice_spoken_not_understood_english, R.string.voice_spoken_not_understood_hindi)

    private fun localized(hinglish: Int, english: Int, hindi: Int): String = appContext.getString(
        when (replyLanguage()) {
            Language.HINGLISH -> hinglish
            Language.ENGLISH -> english
            Language.HINDI -> hindi
        },
    )

    private companion object {
        const val TAG = "Bidu/Assistant"
        const val MAX_QUEUED = 4
        const val MAX_ACTIVITY = 50
        const val VOICE_CONFIRM_ATTEMPTS = 2
        const val CONFIRM_CAPTURE_MS = 8_000L

        val LISTENING_CAPABILITIES = setOf(Capability.RECORD_AUDIO, Capability.POST_NOTIFICATIONS)
        val YES_NO_OPTIONS = listOf("haan", "nahi")
        val ENGINE_WORKING = setOf(
            AgentState.UNDERSTANDING,
            AgentState.PLANNING,
            AgentState.CHECKING_PERMISSIONS,
            AgentState.EXECUTING,
            AgentState.VERIFYING,
            AgentState.REPORTING,
        )

        private val YES = setOf(
            "haan", "haa", "ha", "han", "haan ji", "ji haan", "ji", "yes", "yeah", "yep", "ok", "okay", "theek hai",
            "thik hai", "kar do", "kardo", "karo", "confirm", "sure", "हाँ", "हां", "जी", "जी हाँ", "ठीक है", "कर दो",
        )
        private val NO = setOf(
            "nahi", "nahin", "nai", "na", "no", "nope", "mat karo", "mat", "rehne do", "rahne do", "cancel", "stop",
            "नहीं", "ना", "मत करो", "रहने दो",
        )
        private val PUNCTUATION = Regex("[\\p{Punct}।]+")
        private val SPACES = Regex("\\s+")

        /** Safety net for short answers the NLU did not classify; longer utterances are never guessed. */
        fun simpleYesNo(text: String): ConfirmationDecision? {
            val normalized = text.lowercase(Locale.ROOT).replace(PUNCTUATION, " ").replace(SPACES, " ").trim()
            if (normalized.split(' ').size > 3) return null
            val withoutAddress = normalized.removeSuffix(" bhai").removeSuffix(" bidu").trim()
            return when (withoutAddress) {
                in YES -> ConfirmationDecision.APPROVED
                in NO -> ConfirmationDecision.REJECTED
                else -> null
            }
        }
    }
}
