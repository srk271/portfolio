package dev.bidu.app.voice

import android.content.Context
import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log
import dev.bidu.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

/**
 * Offline free-form fallback (no grammar) on the installed Vosk model, used when the platform
 * recognizer is missing or failing. The model is Indian English, so Hindi words come out
 * approximated; it is a fallback, not the primary recognizer.
 */
internal class VoskCommandRecognizer(
    private val context: Context,
    private val runtime: VoskRuntime,
) {
    suspend fun recognize(
        modelDirectory: File,
        timeoutMillis: Long,
        session: CaptureSession,
        onPartial: (String) -> Unit,
    ): CaptureResult = withContext(runtime.dispatcher) {
        val model = try {
            runtime.model(modelDirectory)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "offline model load failed: ${t.javaClass.simpleName}")
            null
        }
        if (model == null) {
            CaptureResult.Failed(context.getString(R.string.voice_error_offline_model))
        } else {
            val recognizer = Recognizer(model, MicAudio.SAMPLE_RATE.toFloat())
            try {
                capture(recognizer, timeoutMillis, session, onPartial)
            } finally {
                recognizer.close()
            }
        }
    }

    private suspend fun capture(
        recognizer: Recognizer,
        timeoutMillis: Long,
        session: CaptureSession,
        onPartial: (String) -> Unit,
    ): CaptureResult {
        val record: AudioRecord = try {
            MicAudio.open(context)
        } catch (e: SecurityException) {
            return CaptureResult.Failed(context.getString(R.string.voice_error_mic_permission))
        } catch (e: IOException) {
            return CaptureResult.Failed(context.getString(R.string.voice_error_mic_unavailable))
        }
        val removeCancelHook = session.onCancel { MicAudio.safeStop(record) }
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                return CaptureResult.Failed(context.getString(R.string.voice_error_mic_unavailable))
            }
            val buffer = ShortArray(MicAudio.CHUNK_SAMPLES)
            val startedAt = SystemClock.elapsedRealtime()
            var heardSpeech = false
            var emptyReads = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                if (session.cancelled) return CaptureResult.Failed(DefaultVoiceController.CANCELLED_REASON)
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (elapsed >= timeoutMillis) return finish(VoskJson.text(recognizer.finalResult))
                if (!heardSpeech && elapsed >= NO_SPEECH_TIMEOUT_MS) return CaptureResult.NoSpeech
                val read = record.read(buffer, 0, buffer.size)
                if (session.cancelled) return CaptureResult.Failed(DefaultVoiceController.CANCELLED_REASON)
                if (read < 0) return CaptureResult.Failed(context.getString(R.string.voice_error_mic_unavailable))
                if (read == 0) {
                    if (++emptyReads > MAX_EMPTY_READS) {
                        return CaptureResult.Failed(context.getString(R.string.voice_error_mic_unavailable))
                    }
                    continue
                }
                emptyReads = 0
                if (recognizer.acceptWaveForm(buffer, read)) {
                    val text = VoskJson.text(recognizer.result)
                    if (text.isNotEmpty()) return finish(text)
                } else {
                    val partial = VoskJson.partialText(recognizer.partialResult)
                    if (partial.isNotEmpty()) {
                        heardSpeech = true
                        onPartial(partial)
                    }
                }
            }
        } finally {
            removeCancelHook()
            MicAudio.safeStop(record)
            record.release()
        }
    }

    private fun finish(text: String): CaptureResult =
        if (text.isBlank()) CaptureResult.NoSpeech else CaptureResult.Text(text.trim(), MODEL_LANGUAGE_TAG)

    private companion object {
        const val TAG = "Bidu/VoskCommand"
        const val MODEL_LANGUAGE_TAG = "en-IN"
        const val NO_SPEECH_TIMEOUT_MS = 6_000L
        const val MAX_EMPTY_READS = 50
    }
}
