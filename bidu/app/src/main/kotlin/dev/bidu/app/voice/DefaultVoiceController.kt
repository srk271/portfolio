package dev.bidu.app.voice

import android.content.Context
import android.util.Log
import dev.bidu.app.R
import dev.bidu.app.settings.SettingsRepository
import dev.bidu.app.settings.WakePhrase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * Sequences the microphone between the Vosk wake-word detector and command capture. Desired
 * state lives in a few flags; [reconcile] (serialised by a mutex) starts or stops the detector to
 * match them, so overlapping start/pause/capture requests always converge and never overlap.
 *
 * The detector only runs while [BiduForegroundService] is running, so listening is always
 * backed by the visible foreground notification.
 */
class DefaultVoiceController(
    context: Context,
    private val settings: SettingsRepository,
    private val models: VoskModelManager,
    private val scope: CoroutineScope,
) : VoiceController {

    private val appContext = context.applicationContext
    private val runtime = VoskRuntime()
    private val micMonitor = MicMonitor(appContext)
    private val detector = VoskWakeWordDetector(appContext, runtime, micMonitor) {
        val current = settings.current()
        VoskWakeWordDetector.Config(current.wakeMinConfidence, current.wakeCooldownMillis)
    }
    private val systemRecognizer = SystemCommandRecognizer(appContext)
    private val voskRecognizer = VoskCommandRecognizer(appContext, runtime)
    private val cue = CaptureCue(appContext)

    private val _state = MutableStateFlow(VoiceState(wakeWordAvailable = models.state.value is ModelState.Installed))
    override val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    private val control = Mutex()
    private val captureLock = Mutex()

    @Volatile private var wakeWanted = false
    @Volatile private var pauseRequested = false
    @Volatile private var capturing = false
    @Volatile private var serviceRunning = false
    @Volatile private var released = false

    @Volatile private var detectorListening = false
    @Volatile private var detectorProblem: String? = null
    @Volatile private var modelLoadFailed = false
    @Volatile private var captureProblem: String? = null
    @Volatile private var currentCapture: CaptureSession? = null

    // Guarded by [control].
    private var detectorJob: Job? = null
    private var detectorPhrases: List<WakePhrase>? = null
    private var detectorModelDir: File? = null

    private val detectorListener = object : VoskWakeWordDetector.Listener {
        override fun onListening() {
            detectorListening = true
            detectorProblem = null
            publish()
        }

        override fun onPaused(reason: VoskWakeWordDetector.PauseReason) {
            detectorListening = false
            detectorProblem = appContext.getString(
                when (reason) {
                    VoskWakeWordDetector.PauseReason.MIC_BUSY -> R.string.voice_error_mic_busy
                    VoskWakeWordDetector.PauseReason.IN_CALL -> R.string.voice_error_in_call
                    VoskWakeWordDetector.PauseReason.MIC_ERROR -> R.string.voice_error_mic_failed
                },
            )
            publish()
        }

        override fun onUnavailable() {
            detectorListening = false
            modelLoadFailed = true
            detectorProblem = appContext.getString(R.string.voice_error_model_load)
            publish()
            // Tear the idle detector down; the next startWakeListening() retries the load.
            requestReconcile()
        }

        override fun onWake(phrase: String, heard: String, confidence: Double) {
            Log.i(TAG, "wake word detected")
            _state.update { it.copy(lastWakePhrase = phrase, lastWakeAt = Instant.now()) }
            _events.tryEmit(VoiceEvent.WakeDetected(phrase = phrase, heard = heard, confidence = confidence))
        }
    }

    init {
        scope.launch {
            BiduForegroundService.running.collect { running ->
                serviceRunning = running
                reconcile()
            }
        }
        scope.launch {
            models.state.collect { modelState ->
                if (modelState is ModelState.Installed) modelLoadFailed = false
                reconcile()
            }
        }
        scope.launch {
            settings.settings.map { it.wakePhrases }.distinctUntilChanged().collect { reconcile() }
        }
    }

    override fun startWakeListening() {
        wakeWanted = true
        // A fresh start retries a model that failed to load earlier (for example under memory pressure).
        modelLoadFailed = false
        requestReconcile()
    }

    override fun stopWakeListening() {
        wakeWanted = false
        requestReconcile()
    }

    override fun pauseWakeListening() {
        pauseRequested = true
        requestReconcile()
    }

    override fun resumeWakeListening() {
        pauseRequested = false
        requestReconcile()
    }

    override suspend fun captureCommand(timeoutMillis: Long): CaptureResult {
        if (released) return CaptureResult.Failed(appContext.getString(R.string.voice_error_mic_unavailable))
        if (!captureLock.tryLock()) return CaptureResult.Failed(appContext.getString(R.string.voice_error_capture_busy))
        val session = CaptureSession()
        currentCapture = session
        try {
            if (!MicAudio.hasPermission(appContext)) {
                val message = appContext.getString(R.string.voice_error_mic_permission)
                captureProblem = message
                publish()
                return CaptureResult.Failed(message)
            }
            captureProblem = null
            capturing = true
            // Stops the detector and waits until its AudioRecord is released.
            reconcile()
            publish()
            cue.play()
            return recognize(session, timeoutMillis)
        } finally {
            currentCapture = null
            capturing = false
            _state.update { it.copy(partialTranscript = null) }
            withContext(NonCancellable) { reconcile() }
            captureLock.unlock()
        }
    }

    override fun cancelCapture() {
        currentCapture?.cancel()
    }

    override fun release() {
        released = true
        wakeWanted = false
        currentCapture?.cancel()
        scope.launch {
            control.withLock {
                stopDetectorLocked()
                withContext(runtime.dispatcher) { runtime.closeModel() }
            }
            runtime.shutdown()
            publish()
        }
    }

    private suspend fun recognize(session: CaptureSession, timeoutMillis: Long): CaptureResult {
        val current = settings.current()
        val languageTag = current.recognizerLanguageTag
        val preferOffline = current.preferOfflineRecognition
        val onPartial: (String) -> Unit = { text -> _state.update { it.copy(partialTranscript = text) } }
        var lastFailure: String? = null
        // true = on-device recognizer. The preferred engine goes first; the other one is the fallback.
        val engines = buildList<Boolean> {
            val regular = systemRecognizer.isAvailable()
            val onDevice = systemRecognizer.isOnDeviceAvailable()
            if (preferOffline && onDevice) add(true)
            if (regular) add(false)
            if (!preferOffline && onDevice) add(true)
        }
        for (onDevice in engines) {
            if (session.cancelled) return CaptureResult.Failed(CANCELLED_REASON)
            when (val outcome = systemRecognizer.recognize(onDevice, preferOffline, languageTag, timeoutMillis, session, onPartial)) {
                is SystemCommandRecognizer.Outcome.Text -> return CaptureResult.Text(outcome.text, languageTag)
                SystemCommandRecognizer.Outcome.NoSpeech -> return CaptureResult.NoSpeech
                SystemCommandRecognizer.Outcome.Cancelled -> return CaptureResult.Failed(CANCELLED_REASON)
                SystemCommandRecognizer.Outcome.PermissionDenied -> {
                    val message = appContext.getString(R.string.voice_error_mic_permission)
                    captureProblem = message
                    publish()
                    return CaptureResult.Failed(message)
                }
                is SystemCommandRecognizer.Outcome.Unavailable -> {
                    Log.w(TAG, "system recognizer unavailable (onDevice=$onDevice): ${outcome.reason}")
                    lastFailure = outcome.reason
                }
            }
        }
        if (session.cancelled) return CaptureResult.Failed(CANCELLED_REASON)
        val modelDir = models.installedDirectory()
        if (modelDir != null) {
            Log.i(TAG, "falling back to offline Vosk recognition")
            return voskRecognizer.recognize(modelDir, timeoutMillis, session, onPartial)
        }
        val failure = lastFailure
        return CaptureResult.Failed(
            if (failure == null) {
                appContext.getString(R.string.voice_error_no_recognizer)
            } else {
                appContext.getString(R.string.voice_error_recognizer_failed, failure)
            },
        )
    }

    private fun requestReconcile() {
        scope.launch { reconcile() }
    }

    private suspend fun reconcile() {
        control.withLock {
            if (released) return@withLock
            val modelDir = models.installedDirectory()
            val phrases = settings.current().wakePhrases
            val shouldRun = serviceRunning && wakeWanted && !pauseRequested && !capturing &&
                modelDir != null && !modelLoadFailed && MicAudio.hasPermission(appContext)
            val job = detectorJob
            val running = job != null && job.isActive
            if (running && (!shouldRun || phrases != detectorPhrases || modelDir != detectorModelDir)) {
                stopDetectorLocked()
            }
            if (modelDir == null && runtime.hasModel()) {
                withContext(runtime.dispatcher) { runtime.closeModel() }
            }
            if (shouldRun && detectorJob?.isActive != true && modelDir != null) {
                startDetectorLocked(modelDir, phrases)
            }
            if (!shouldRun) detectorListening = false
        }
        publish()
    }

    private fun startDetectorLocked(modelDir: File, phrases: List<WakePhrase>) {
        detectorPhrases = phrases
        detectorModelDir = modelDir
        detectorProblem = null
        micMonitor.start()
        detectorJob = scope.launch(runtime.dispatcher) {
            detector.run(modelDir, phrases, detectorListener)
        }
    }

    private suspend fun stopDetectorLocked() {
        val job = detectorJob ?: return
        detector.interrupt()
        job.cancelAndJoin()
        detectorJob = null
        detectorListening = false
        micMonitor.stop()
    }

    private fun publish() {
        val running = serviceRunning
        val listening = detectorListening && !capturing
        val mode = when {
            capturing -> VoiceMode.CAPTURING
            listening -> VoiceMode.WAKE_LISTENING
            running -> VoiceMode.PAUSED
            else -> VoiceMode.OFF
        }
        val modelInstalled = models.state.value is ModelState.Installed
        val wakeProblem = when {
            !running || !wakeWanted || listening || capturing || pauseRequested -> null
            !modelInstalled -> appContext.getString(R.string.voice_error_no_model)
            !MicAudio.hasPermission(appContext) -> appContext.getString(R.string.voice_error_mic_permission)
            else -> detectorProblem
        }
        _state.update {
            it.copy(
                serviceRunning = running,
                mode = mode,
                wakeWordAvailable = modelInstalled && !modelLoadFailed,
                partialTranscript = if (capturing) it.partialTranscript else null,
                error = wakeProblem ?: captureProblem,
            )
        }
    }

    companion object {
        private const val TAG = "Bidu/Voice"

        /** [CaptureResult.Failed.reason] when the capture was cancelled on purpose; nothing to report. */
        const val CANCELLED_REASON = "cancelled"
    }
}
