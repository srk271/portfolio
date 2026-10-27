package dev.bidu.app.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import dev.bidu.app.settings.SettingsRepository
import dev.bidu.core.model.Language
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Platform TextToSpeech with the engine and voice chosen in settings. Created and driven on the
 * main thread; [speak] suspends until the utterance is done so the caller can reopen the mic.
 * Speech uses USAGE_ASSISTANT with transient, ducking audio focus.
 */
class AndroidSpeaker(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : Speaker {

    private val appContext = context.applicationContext
    private val audioManager: AudioManager = appContext.getSystemService(AudioManager::class.java)
    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    /** True while an utterance is playing. */
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    // Written on the main thread only.
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var engineInUse: String? = null
    @Volatile private var initResult = CompletableDeferred<Boolean>()

    private val pending = ConcurrentHashMap<String, CompletableDeferred<SpeakResult>>()
    private val counter = AtomicLong()
    @Volatile private var currentUtterance: String? = null
    @Volatile private var released = false

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _speaking.value = true
        }

        override fun onDone(utteranceId: String?) {
            finish(utteranceId, SpeakResult.SPOKEN)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            finish(utteranceId, SpeakResult.FAILED)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            finish(utteranceId, SpeakResult.FAILED)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            finish(utteranceId, SpeakResult.INTERRUPTED)
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        // A call or another app took audio focus outright: stop talking over it.
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
    }

    init {
        scope.launch(Dispatchers.Main.immediate) {
            settings.settings.map { it.ttsEngine }.distinctUntilChanged().collect { engine -> initialise(engine) }
        }
    }

    override suspend fun speak(text: String, language: Language): SpeakResult {
        val utterance = text.trim()
        if (utterance.isEmpty() || released) return SpeakResult.FAILED
        if (!awaitInit()) return SpeakResult.UNAVAILABLE
        return withContext(Dispatchers.Main.immediate) {
            val engine = tts ?: return@withContext SpeakResult.UNAVAILABLE
            if (!applyVoice(engine, utterance, language)) return@withContext SpeakResult.UNAVAILABLE
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            if (audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                // Typically an ongoing call; speaking over it would be wrong.
                return@withContext SpeakResult.UNAVAILABLE
            }
            val id = "bidu-${counter.incrementAndGet()}"
            val done = CompletableDeferred<SpeakResult>()
            pending[id] = done
            try {
                val chunk = utterance.take(TextToSpeech.getMaxSpeechInputLength())
                currentUtterance = id
                if (engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, Bundle(), id) != TextToSpeech.SUCCESS) {
                    return@withContext SpeakResult.FAILED
                }
                withTimeoutOrNull(maxDurationMillis(chunk)) { done.await() } ?: run {
                    Log.w(TAG, "utterance did not finish in time")
                    if (currentUtterance == id) engine.stop()
                    SpeakResult.FAILED
                }
            } catch (e: CancellationException) {
                if (currentUtterance == id) engine.stop()
                throw e
            } finally {
                pending.remove(id)
                if (currentUtterance == id) {
                    currentUtterance = null
                    _speaking.value = false
                }
                audioManager.abandonAudioFocusRequest(focus)
            }
        }
    }

    override fun stop() {
        scope.launch(Dispatchers.Main.immediate) { tts?.stop() }
    }

    override fun isLanguageAvailable(languageTag: String): Boolean {
        val engine = tts ?: return false
        return try {
            engine.isLanguageAvailable(Locale.forLanguageTag(languageTag)) >= TextToSpeech.LANG_AVAILABLE
        } catch (e: RuntimeException) {
            false
        }
    }

    override fun engines(): List<TtsEngineInfo> {
        val engine = tts ?: return emptyList()
        return try {
            engine.engines.map { TtsEngineInfo(packageName = it.name, label = it.label) }
        } catch (e: RuntimeException) {
            emptyList()
        }
    }

    override fun voices(): List<TtsVoiceInfo> {
        val engine = tts ?: return emptyList()
        val enginePackage = engineInUse ?: engine.defaultEngine.orEmpty()
        return try {
            engine.voices.orEmpty()
                .filter { it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true }
                .map { TtsVoiceInfo(name = it.name, languageTag = it.locale.toLanguageTag(), enginePackage = enginePackage) }
                .sortedWith(compareBy({ it.languageTag }, { it.name }))
        } catch (e: RuntimeException) {
            emptyList()
        }
    }

    override fun release() {
        released = true
        scope.launch(Dispatchers.Main.immediate) { shutdownEngine() }
    }

    /** Waits for the current engine; follows a re-initialisation that replaced it meanwhile. */
    private suspend fun awaitInit(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + INIT_TIMEOUT_MS
        while (true) {
            val deferred = initResult
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) return false
            val ok = withTimeoutOrNull(remaining) { deferred.await() } ?: return false
            if (ok || deferred === initResult) return ok
        }
    }

    /** Main thread. */
    private fun initialise(enginePackage: String?) {
        if (released) return
        shutdownEngine()
        val result = CompletableDeferred<Boolean>()
        initResult = result
        engineInUse = enginePackage
        var created: TextToSpeech? = null
        created = TextToSpeech(appContext, { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                created?.setAudioAttributes(audioAttributes)
                created?.setOnUtteranceProgressListener(progressListener)
            } else {
                Log.w(TAG, "TTS init failed (status=$status)")
            }
            if (tts === created) _ready.value = ok
            result.complete(ok)
        }, enginePackage)
        tts = created
    }

    /** Main thread. */
    private fun shutdownEngine() {
        val engine = tts ?: return
        tts = null
        _ready.value = false
        try {
            engine.stop()
            engine.shutdown()
        } catch (e: RuntimeException) {
            Log.w(TAG, "TTS shutdown failed: ${e.javaClass.simpleName}")
        }
        pending.values.forEach { it.complete(SpeakResult.INTERRUPTED) }
        initResult.complete(false)
    }

    /** Main thread. Picks the configured voice when it fits the text, else the best locale available. */
    private fun applyVoice(engine: TextToSpeech, text: String, language: Language): Boolean {
        val candidates = localeCandidates(text, language)
        val wanted = candidates.first()
        val voiceName = settings.current().ttsVoice
        if (voiceName != null) {
            val voice: Voice? = try {
                engine.voices?.firstOrNull { it.name == voiceName }
            } catch (e: RuntimeException) {
                null
            }
            if (voice != null && voice.locale.language == wanted.language && engine.setVoice(voice) == TextToSpeech.SUCCESS) {
                return true
            }
        }
        for (locale in candidates) {
            val availability = try {
                engine.isLanguageAvailable(locale)
            } catch (e: RuntimeException) {
                TextToSpeech.LANG_NOT_SUPPORTED
            }
            if (availability >= TextToSpeech.LANG_AVAILABLE && engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE) {
                return true
            }
        }
        Log.w(TAG, "no installed TTS language fits the reply")
        return false
    }

    private fun finish(utteranceId: String?, result: SpeakResult) {
        if (utteranceId == null) return
        pending[utteranceId]?.complete(result)
        if (utteranceId == currentUtterance) _speaking.value = false
    }

    private companion object {
        const val TAG = "Bidu/Speaker"
        const val INIT_TIMEOUT_MS = 5_000L
        val HINDI: Locale = Locale.forLanguageTag("hi-IN")
        val INDIAN_ENGLISH: Locale = Locale.forLanguageTag("en-IN")
        val DEVANAGARI = Regex("[\\u0900-\\u097F]")

        /** Script decides the voice: Devanagari needs a Hindi voice; romanised Hinglish reads best in en-IN. */
        fun localeCandidates(text: String, language: Language): List<Locale> {
            val preferHindi = DEVANAGARI.containsMatchIn(text) || (language == Language.HINDI)
            val primary = if (preferHindi) listOf(HINDI, INDIAN_ENGLISH) else listOf(INDIAN_ENGLISH, HINDI)
            return (primary + Locale.getDefault() + Locale.ENGLISH).distinct()
        }

        /** Generous upper bound so a stuck engine cannot hold the mic closed forever. */
        fun maxDurationMillis(text: String): Long = 15_000L + text.length * 120L
    }
}
