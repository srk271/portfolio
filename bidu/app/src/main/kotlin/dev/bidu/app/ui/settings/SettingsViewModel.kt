package dev.bidu.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidu.app.R
import dev.bidu.app.assistant.AssistantController
import dev.bidu.app.assistant.AssistantUiState
import dev.bidu.app.data.ActionLogRepository
import dev.bidu.app.data.HistoryRepository
import dev.bidu.app.permissions.DevicePermissions
import dev.bidu.app.security.SecretStore
import dev.bidu.app.settings.BiduSettings
import dev.bidu.app.settings.SettingsRepository
import dev.bidu.app.settings.WakePhrase
import dev.bidu.app.ui.UiMessage
import dev.bidu.app.ui.attempt
import dev.bidu.app.ui.parseWakePhrase
import dev.bidu.app.voice.SpeakResult
import dev.bidu.app.voice.Speaker
import dev.bidu.app.voice.TtsEngineInfo
import dev.bidu.app.voice.TtsVoiceInfo
import dev.bidu.core.model.Capability
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** What the speaker reports about installed engines and voices; empty until TTS is ready. */
data class TtsChoices(
    val engines: List<TtsEngineInfo> = emptyList(),
    val voices: List<TtsVoiceInfo> = emptyList(),
    val hindiAvailable: Boolean = false,
    val englishIndiaAvailable: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secrets: SecretStore,
    private val speaker: Speaker,
    private val assistant: AssistantController,
    private val history: HistoryRepository,
    private val actionLog: ActionLogRepository,
    private val permissions: DevicePermissions,
) : ViewModel() {
    val settings: StateFlow<BiduSettings> = settingsRepository.settings
    val ui: StateFlow<AssistantUiState> = assistant.uiState
    val ttsReady: StateFlow<Boolean> = speaker.ready

    private val _tts = MutableStateFlow(TtsChoices())
    val tts: StateFlow<TtsChoices> = _tts.asStateFlow()

    /** Null until the secret store answered. The key itself is never read back into the UI. */
    private val _hasApiKey = MutableStateFlow<Boolean?>(null)
    val hasApiKey: StateFlow<Boolean?> = _hasApiKey.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { refreshKeyState() }
        viewModelScope.launch {
            combine(speaker.ready, settingsRepository.settings.map { it.ttsEngine }.distinctUntilChanged()) { ready, _ -> ready }
                .collect { ready -> _tts.value = if (ready) loadTts() else TtsChoices() }
        }
    }

    fun update(transform: (BiduSettings) -> BiduSettings) {
        viewModelScope.launch {
            attempt { settingsRepository.update(transform) }.onFailure { send(R.string.ui_msg_save_failed) }
        }
    }

    fun setListening(enabled: Boolean) = assistant.setListening(enabled)

    fun listeningPermissions(): List<String> =
        (permissions.runtimePermissions(Capability.RECORD_AUDIO) + permissions.runtimePermissions(Capability.POST_NOTIFICATIONS)).distinct()

    fun refreshPermissions() = permissions.refresh()

    fun permissionsDenied() = send(R.string.ui_msg_permissions_denied)

    /** [index] null adds a phrase. Returns false (with a message) when the input is unusable. */
    fun saveWakePhrase(index: Int?, phrase: String, variants: String): Boolean {
        val parsed = parseWakePhrase(phrase, variants)
        if (parsed == null) {
            send(R.string.ui_msg_wake_invalid)
            return false
        }
        val duplicate = settings.value.wakePhrases.withIndex().any { (i, existing) -> i != index && existing.phrase == parsed.phrase }
        if (duplicate) {
            send(R.string.ui_msg_wake_duplicate)
            return false
        }
        update { s -> s.copy(wakePhrases = replaceOrAppend(s.wakePhrases, index, parsed)) }
        return true
    }

    fun deleteWakePhrase(index: Int) {
        if (settings.value.wakePhrases.size <= 1) {
            send(R.string.ui_msg_wake_keep_one)
            return
        }
        update { s ->
            if (s.wakePhrases.size <= 1 || index !in s.wakePhrases.indices) {
                s
            } else {
                s.copy(wakePhrases = s.wakePhrases.filterIndexed { i, _ -> i != index })
            }
        }
    }

    fun resetWakePhrases() = update { it.copy(wakePhrases = BiduSettings.DEFAULT_WAKE_PHRASES) }

    fun testSpeak(text: String) {
        viewModelScope.launch {
            when (attempt { speaker.speak(text, settings.value.responseLanguage) }.getOrNull()) {
                SpeakResult.SPOKEN, SpeakResult.INTERRUPTED -> Unit
                SpeakResult.UNAVAILABLE -> send(R.string.ui_msg_tts_unavailable)
                SpeakResult.FAILED, null -> send(R.string.ui_msg_tts_failed)
            }
        }
    }

    /** Called only after the owner accepted the consent dialog. */
    fun enableCloud() = update { it.copy(cloudEnabled = true, cloudConsentGiven = true) }

    /** Turning cloud off also withdraws consent, so turning it on again asks again. */
    fun disableCloud() = update { it.copy(cloudEnabled = false, cloudConsentGiven = false) }

    fun saveCloudModel(modelId: String) = update { it.copy(cloudModel = modelId.trim().ifBlank { null }) }

    fun saveApiKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return false
        viewModelScope.launch {
            attempt { secrets.put(SecretStore.CLOUD_API_KEY, trimmed) }
                .onSuccess { send(R.string.ui_msg_key_saved) }
                .onFailure { send(R.string.ui_msg_key_failed) }
            refreshKeyState()
        }
        return true
    }

    fun removeApiKey() {
        viewModelScope.launch {
            attempt { secrets.remove(SecretStore.CLOUD_API_KEY) }
                .onSuccess { send(R.string.ui_msg_key_removed) }
                .onFailure { send(R.string.ui_msg_key_failed) }
            refreshKeyState()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            attempt { history.clear() }
                .onSuccess { send(R.string.ui_msg_history_cleared) }
                .onFailure { send(R.string.ui_msg_clear_failed) }
        }
    }

    fun clearActionLog() {
        viewModelScope.launch {
            attempt { actionLog.clear() }
                .onSuccess { send(R.string.ui_msg_log_cleared) }
                .onFailure { send(R.string.ui_msg_clear_failed) }
        }
    }

    private suspend fun refreshKeyState() {
        _hasApiKey.value = attempt { secrets.has(SecretStore.CLOUD_API_KEY) }.getOrDefault(false)
    }

    private fun loadTts(): TtsChoices = TtsChoices(
        engines = runCatching { speaker.engines() }.getOrDefault(emptyList()),
        voices = runCatching { speaker.voices() }.getOrDefault(emptyList()),
        hindiAvailable = runCatching { speaker.isLanguageAvailable("hi-IN") }.getOrDefault(false),
        englishIndiaAvailable = runCatching { speaker.isLanguageAvailable("en-IN") }.getOrDefault(false),
    )

    private fun send(text: Int) {
        _messages.trySend(UiMessage(text))
    }

    private fun replaceOrAppend(list: List<WakePhrase>, index: Int?, phrase: WakePhrase): List<WakePhrase> =
        if (index == null || index !in list.indices) list + phrase else list.mapIndexed { i, p -> if (i == index) phrase else p }
}
