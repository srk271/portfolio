package dev.bidu.app.voice

import dev.bidu.core.model.Language
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Instant

enum class VoiceMode {
    /** Service not running; the mic is closed. */
    OFF,
    /** Vosk holds the mic and listens for a wake phrase. */
    WAKE_LISTENING,
    /** Vosk has released the mic; the command recognizer owns it. */
    CAPTURING,
    /** Service running but mic released on purpose (TTS speaking, phone call, model missing). */
    PAUSED,
}

data class VoiceState(
    val serviceRunning: Boolean = false,
    val mode: VoiceMode = VoiceMode.OFF,
    /** True when the Vosk model is installed and loaded; false means push-to-talk only. */
    val wakeWordAvailable: Boolean = false,
    val partialTranscript: String? = null,
    val lastWakePhrase: String? = null,
    val lastWakeAt: Instant? = null,
    val error: String? = null,
)

sealed interface VoiceEvent {
    /** A configured wake phrase was heard above threshold and outside the cooldown. */
    data class WakeDetected(val phrase: String, val heard: String, val confidence: Double) : VoiceEvent
    data class Error(val message: String) : VoiceEvent
}

sealed interface CaptureResult {
    data class Text(val text: String, val languageTag: String?) : CaptureResult
    data object NoSpeech : CaptureResult
    data class Failed(val reason: String) : CaptureResult
}

/**
 * Owns the microphone. Exactly one component may record at a time, so this controller sequences
 * Vosk (wake word) and the system SpeechRecognizer (command) and never lets them overlap.
 */
interface VoiceController {
    val state: StateFlow<VoiceState>
    val events: SharedFlow<VoiceEvent>

    /** Called by the foreground service after startForeground succeeded. No-op without a model. */
    fun startWakeListening()
    fun stopWakeListening()

    /** Releases the mic without leaving wake mode (while Bidu speaks); [resumeWakeListening] reopens it. */
    fun pauseWakeListening()
    fun resumeWakeListening()

    /**
     * One-shot command capture: pauses Vosk and releases its AudioRecord, runs the system recognizer
     * on the main thread with the configured language, falls back to Vosk free-form recognition when
     * the recognizer is unavailable, then resumes wake listening if it was active. Safe to call from
     * push-to-talk when no service is running.
     */
    suspend fun captureCommand(timeoutMillis: Long = 10_000): CaptureResult
    fun cancelCapture()

    fun release()
}

data class TtsEngineInfo(val packageName: String, val label: String)

data class TtsVoiceInfo(val name: String, val languageTag: String, val enginePackage: String)

enum class SpeakResult { SPOKEN, INTERRUPTED, UNAVAILABLE, FAILED }

/** Text-to-speech. [speak] suspends until the utterance finished so the caller can reopen the mic afterwards. */
interface Speaker {
    val ready: StateFlow<Boolean>
    suspend fun speak(text: String, language: Language): SpeakResult
    fun stop()
    fun isLanguageAvailable(languageTag: String): Boolean
    fun engines(): List<TtsEngineInfo>
    fun voices(): List<TtsVoiceInfo>
    fun release()
}

sealed interface ModelState {
    data object NotInstalled : ModelState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : ModelState
    data object Extracting : ModelState
    data class Installed(val directory: File, val name: String) : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Downloads and manages the on-device Vosk model. [install] refuses with [ModelState.Failed] unless
 * `BiduSettings.voskModelConsent` is true; the download goes to the cache directory, the archive is
 * checked for the expected model files, then unpacked and moved into place atomically.
 */
interface VoskModelManager {
    val state: StateFlow<ModelState>
    suspend fun install()
    fun cancelInstall()
    suspend fun remove()
    fun installedDirectory(): File?

    companion object {
        const val MODEL_NAME = "vosk-model-small-en-in-0.4"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        const val APPROX_SIZE_BYTES = 36L * 1024 * 1024
    }
}

/** Lets a tool switch background listening on or off without depending on the assistant controller. */
interface ListeningSwitch {
    fun isListening(): Boolean
    fun setListening(enabled: Boolean)
}
