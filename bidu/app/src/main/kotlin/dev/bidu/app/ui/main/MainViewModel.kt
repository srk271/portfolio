package dev.bidu.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidu.app.R
import dev.bidu.app.assistant.AssistantController
import dev.bidu.app.assistant.AssistantUiState
import dev.bidu.app.data.HistoryRepository
import dev.bidu.app.permissions.DevicePermissions
import dev.bidu.app.settings.BiduSettings
import dev.bidu.app.settings.SettingsRepository
import dev.bidu.app.ui.UiMessage
import dev.bidu.core.agent.ConversationTurn
import dev.bidu.core.agent.RequestOrigin
import dev.bidu.core.model.Capability
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    private val assistant: AssistantController,
    history: HistoryRepository,
    settingsRepository: SettingsRepository,
    private val permissions: DevicePermissions,
) : ViewModel() {
    val ui: StateFlow<AssistantUiState> = assistant.uiState
    val settings: StateFlow<BiduSettings> = settingsRepository.settings

    val turns: StateFlow<List<ConversationTurn>> = history.observeRecent(RECENT_TURNS)
        .map { turns -> turns.sortedByDescending { it.at } }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** Returns true when the text was queued, so the caller can clear its field. */
    fun submit(text: String): Boolean {
        val command = text.trim()
        if (command.isEmpty()) return false
        assistant.submitText(command, RequestOrigin.TEXT)
        return true
    }

    fun pushToTalk() = assistant.pushToTalk()

    fun setListening(enabled: Boolean) = assistant.setListening(enabled)

    fun cancel() = assistant.cancel()

    fun answer(confirmationId: String, approved: Boolean) {
        if (!assistant.resolveConfirmation(confirmationId, approved)) {
            _messages.trySend(UiMessage(R.string.ui_msg_confirmation_expired))
        }
    }

    fun refreshPermissions() = permissions.refresh()

    fun microphonePermissions(): List<String> = permissions.runtimePermissions(Capability.RECORD_AUDIO)

    fun listeningPermissions(): List<String> =
        (permissions.runtimePermissions(Capability.RECORD_AUDIO) + permissions.runtimePermissions(Capability.POST_NOTIFICATIONS)).distinct()

    fun permissionsDenied() {
        _messages.trySend(UiMessage(R.string.ui_msg_permissions_denied))
    }

    private companion object {
        const val RECENT_TURNS = 50
    }
}
