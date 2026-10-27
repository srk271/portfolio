package dev.bidu.app.ui.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import dev.bidu.app.R
import dev.bidu.app.assistant.AssistantController
import dev.bidu.app.permissions.DevicePermissions
import dev.bidu.app.settings.BiduSettings
import dev.bidu.app.settings.SettingsRepository
import dev.bidu.app.ui.UiMessage
import dev.bidu.app.ui.attempt
import dev.bidu.app.voice.ModelState
import dev.bidu.app.voice.VoskModelManager
import dev.bidu.core.model.Capability
import dev.bidu.core.model.PermissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Setup checklist. Long-running work (the model download, persisting onboarding) runs in the app
 * scope so it survives the user leaving the screen.
 */
class OnboardingViewModel(
    private val permissions: DevicePermissions,
    private val settingsRepository: SettingsRepository,
    private val models: VoskModelManager,
    private val assistant: AssistantController,
    private val appScope: CoroutineScope,
) : ViewModel() {
    val statuses: StateFlow<Map<Capability, PermissionStatus>> = permissions.statuses
    val model: StateFlow<ModelState> = models.state
    val settings: StateFlow<BiduSettings> = settingsRepository.settings

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    fun refresh() = permissions.refresh()

    fun statusOf(snapshot: Map<Capability, PermissionStatus>, capability: Capability): PermissionStatus =
        snapshot[capability] ?: permissions.status(capability)

    fun runtimePermissions(capability: Capability): List<String> = permissions.runtimePermissions(capability)

    fun settingsIntent(capability: Capability): Intent? = permissions.settingsIntent(capability)

    fun batterySettingsIntent(): Intent = permissions.batterySettingsIntent()

    /** Records the owner's consent (given in the dialog) and starts the one-time download. */
    fun downloadModel() {
        val current = models.state.value
        if (current is ModelState.Downloading || current is ModelState.Extracting) return
        appScope.launch {
            attempt {
                if (!settingsRepository.current().voskModelConsent) {
                    settingsRepository.update { it.copy(voskModelConsent = true) }
                }
                models.install()
            }.onFailure { e ->
                _messages.trySend(UiMessage(R.string.ui_msg_model_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun cancelDownload() = models.cancelInstall()

    /** Must be called while the activity is visible: starting the microphone service needs a foreground app. */
    fun startBidu() {
        assistant.setListening(true)
        appScope.launch {
            attempt { settingsRepository.update { it.copy(onboardingComplete = true) } }
                .onFailure { _messages.trySend(UiMessage(R.string.ui_msg_save_failed)) }
        }
    }

    fun settingsUnavailable() {
        _messages.trySend(UiMessage(R.string.ui_msg_settings_unavailable))
    }
}
