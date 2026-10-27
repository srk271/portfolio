package dev.bidu.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot command capture with the platform [SpeechRecognizer]. Everything runs on the main
 * thread as the API requires; callbacks are bridged to a [CompletableDeferred].
 */
internal class SystemCommandRecognizer(private val context: Context) {

    sealed interface Outcome {
        data class Text(val text: String) : Outcome
        data object NoSpeech : Outcome
        data object PermissionDenied : Outcome
        data object Cancelled : Outcome
        /** Worth falling back to another recognizer. */
        data class Unavailable(val reason: String, val busy: Boolean = false) : Outcome
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** createOnDeviceSpeechRecognizer exists from API 31, but availability can only be queried from API 33. */
    fun isOnDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    suspend fun recognize(
        onDevice: Boolean,
        preferOffline: Boolean,
        languageTag: String,
        timeoutMillis: Long,
        session: CaptureSession,
        onPartial: (String) -> Unit,
    ): Outcome = withContext(Dispatchers.Main.immediate) {
        val first = runOnce(onDevice, preferOffline, languageTag, timeoutMillis, session, onPartial)
        if (first is Outcome.Unavailable && first.busy && !session.cancelled) {
            delay(BUSY_RETRY_DELAY_MS)
            val second = runOnce(onDevice, preferOffline, languageTag, timeoutMillis, session, onPartial)
            if (second is Outcome.Unavailable && second.busy) Outcome.Unavailable("recognizer busy") else second
        } else {
            first
        }
    }

    private suspend fun runOnce(
        onDevice: Boolean,
        preferOffline: Boolean,
        languageTag: String,
        timeoutMillis: Long,
        session: CaptureSession,
        onPartial: (String) -> Unit,
    ): Outcome {
        if (session.cancelled) return Outcome.Cancelled
        val recognizer = try {
            if (onDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "recognizer creation failed: ${e.javaClass.simpleName}")
            return Outcome.Unavailable("recognizer could not be created")
        }
        val done = CompletableDeferred<Outcome>()
        var latestPartial: String? = null
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                done.complete(mapError(error, languageTag))
            }

            override fun onResults(results: Bundle?) {
                val text = bestText(results)
                done.complete(if (text == null) Outcome.NoSpeech else Outcome.Text(text))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = bestText(partialResults) ?: return
                latestPartial = text
                onPartial(text)
            }
        })
        val removeCancelHook = session.onCancel { done.complete(Outcome.Cancelled) }
        try {
            recognizer.startListening(buildIntent(languageTag, onDevice || preferOffline))
            val outcome = withTimeoutOrNull(timeoutMillis) { done.await() }
            if (outcome != null) return outcome
            // Overall time is up: ask for a final result from what was heard so far.
            recognizer.stopListening()
            val late = withTimeoutOrNull(FINALIZE_GRACE_MS) { done.await() }
            return late ?: latestPartial?.let { Outcome.Text(it) } ?: Outcome.NoSpeech
        } catch (e: RuntimeException) {
            Log.w(TAG, "recognizer failed: ${e.javaClass.simpleName}")
            return Outcome.Unavailable("recognizer failed")
        } finally {
            removeCancelHook()
            try {
                recognizer.cancel()
                recognizer.destroy()
            } catch (e: RuntimeException) {
                Log.w(TAG, "recognizer teardown failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun buildIntent(languageTag: String, preferOffline: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            // Honoured by some recognition services only; harmless where ignored.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, COMPLETE_SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, POSSIBLY_COMPLETE_SILENCE_MS)
        }

    private fun bestText(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull { !it.isNullOrBlank() }
            ?.trim()

    private fun mapError(error: Int, languageTag: String): Outcome = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Outcome.NoSpeech
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Outcome.PermissionDenied
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Outcome.Unavailable("recognizer busy", busy = true)
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> Outcome.Unavailable("recognition service unreachable")
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> Outcome.Unavailable("language $languageTag not available")
        SpeechRecognizer.ERROR_AUDIO -> Outcome.Unavailable("audio capture error")
        SpeechRecognizer.ERROR_CLIENT -> Outcome.Unavailable("recognizer client error")
        else -> Outcome.Unavailable("recognizer error $error")
    }

    private companion object {
        const val TAG = "Bidu/Recognizer"
        const val BUSY_RETRY_DELAY_MS = 500L
        const val FINALIZE_GRACE_MS = 2_000L
        const val COMPLETE_SILENCE_MS = 1_500L
        const val POSSIBLY_COMPLETE_SILENCE_MS = 1_200L
    }
}
