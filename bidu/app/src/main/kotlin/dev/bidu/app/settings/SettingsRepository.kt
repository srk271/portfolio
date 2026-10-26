package dev.bidu.app.settings

import dev.bidu.core.agent.EngineSettings
import dev.bidu.core.model.Language
import dev.bidu.core.model.ResponseStyle
import dev.bidu.core.policy.PolicySettings
import kotlinx.coroutines.flow.StateFlow

/**
 * A wake phrase as the user says it, plus the in-vocabulary spellings the grammar-restricted Vosk
 * recognizer should actually listen for. "Bidu" is not in the en-IN model, so the variants are
 * phonetic stand-ins the user can edit when a phrase keeps missing or false-firing.
 */
data class WakePhrase(val phrase: String, val variants: List<String>) {
    init {
        require(phrase.isNotBlank()) { "wake phrase must not be blank" }
        require(variants.isNotEmpty()) { "wake phrase '$phrase' needs at least one variant" }
    }
}

/** Every user-facing setting. Persisted in DataStore by WS3; secrets live in [dev.bidu.app.security.SecretStore]. */
data class BiduSettings(
    val onboardingComplete: Boolean = false,
    /** Desired state of background listening; after a reboot the resume notification is posted when true. */
    val listeningEnabled: Boolean = false,
    val wakePhrases: List<WakePhrase> = DEFAULT_WAKE_PHRASES,
    /** Minimum per-word confidence from Vosk before a wake phrase counts as heard. */
    val wakeMinConfidence: Double = 0.7,
    /** Ignore further wake detections for this long after one fires. */
    val wakeCooldownMillis: Long = 2_500,
    /** The user agreed to download the Vosk model from alphacephei.com. */
    val voskModelConsent: Boolean = false,
    /** BCP-47 tag handed to the system SpeechRecognizer for command capture. */
    val recognizerLanguageTag: String = "en-IN",
    val responseLanguage: Language = Language.HINGLISH,
    val casualTone: Boolean = true,
    /** TTS engine package and voice name; null means the system default. */
    val ttsEngine: String? = null,
    val ttsVoice: String? = null,
    val confirmBeforeOpeningApps: Boolean = false,
    val confirmBeforeHandoffs: Boolean = false,
    val confirmationTimeoutMillis: Long = 60_000,
    /** Cloud reasoning is off until both the switch and the consent flag are set. */
    val cloudEnabled: Boolean = false,
    val cloudConsentGiven: Boolean = false,
    /** Model identifier sent to the cloud API; null uses the adapter's default. */
    val cloudModel: String? = null,
    val historyLimit: Int = 200,
) {
    fun cloudAllowed(): Boolean = cloudEnabled && cloudConsentGiven

    fun responseStyle(): ResponseStyle = ResponseStyle(language = responseLanguage, casual = casualTone)

    fun policySettings(): PolicySettings = PolicySettings(
        confirmBeforeOpeningApps = confirmBeforeOpeningApps,
        confirmBeforeHandoffs = confirmBeforeHandoffs,
        confirmationTimeoutMillis = confirmationTimeoutMillis,
    )

    fun engineSettings(): EngineSettings = EngineSettings(
        style = responseStyle(),
        policy = policySettings(),
        wakePhrases = wakePhrases.map { it.phrase },
    )

    companion object {
        val DEFAULT_WAKE_PHRASES: List<WakePhrase> = listOf(
            WakePhrase("hey bidu", listOf("hey bidu", "hey buddy", "hey be do", "hey bee do", "hey beedu")),
            WakePhrase("bhai", listOf("bhai", "bye", "by", "buy")),
        )
    }
}

/** Single source of truth for settings. Reads are synchronous snapshots; writes are suspend and durable. */
interface SettingsRepository {
    val settings: StateFlow<BiduSettings>
    fun current(): BiduSettings = settings.value
    suspend fun update(transform: (BiduSettings) -> BiduSettings)
}
