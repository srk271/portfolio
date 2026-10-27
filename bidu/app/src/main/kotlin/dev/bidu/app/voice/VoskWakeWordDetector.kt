package dev.bidu.app.voice

import android.content.Context
import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log
import dev.bidu.app.settings.WakePhrase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Grammar-restricted Vosk wake-word listener with its own AudioRecord. [run] executes on
 * [VoskRuntime.dispatcher] until cancelled; it pauses itself (mic released) while another app
 * or a call has the microphone and backs off exponentially on errors.
 */
internal class VoskWakeWordDetector(
    private val context: Context,
    private val runtime: VoskRuntime,
    private val mic: MicMonitor,
    private val config: () -> Config,
) {
    data class Config(val minConfidence: Double, val cooldownMillis: Long)

    enum class PauseReason { MIC_BUSY, IN_CALL, MIC_ERROR }

    interface Listener {
        /** The AudioRecord is open and decoding. */
        fun onListening()
        fun onPaused(reason: PauseReason)
        /** The model could not be loaded or there is nothing to listen for; the detector stays idle. */
        fun onUnavailable()
        fun onWake(phrase: String, heard: String, confidence: Double)
    }

    private enum class Outcome { SILENCED, IN_CALL, FAILED, STOPPED }

    @Volatile private var activeRecord: AudioRecord? = null
    @Volatile private var stopRequested = false

    /** Only touched on the Vosk thread. */
    private var lastWakeAt = 0L

    /** Unblocks a pending read right away; the caller then cancels the job running [run]. */
    fun interrupt() {
        stopRequested = true
        activeRecord?.let { MicAudio.safeStop(it) }
    }

    suspend fun run(modelDirectory: File, phrases: List<WakePhrase>, listener: Listener) {
        stopRequested = false
        val model: Model = try {
            runtime.model(modelDirectory)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // IOException from Vosk, or UnsatisfiedLinkError when the native library cannot load.
            Log.w(TAG, "wake model load failed: ${t.javaClass.simpleName}")
            listener.onUnavailable()
            awaitCancellation()
        }
        val matcher = WakeMatcher(phrases)
        if (matcher.isEmpty) {
            listener.onUnavailable()
            awaitCancellation()
        }
        var backoff = INITIAL_BACKOFF_MS
        while (true) {
            currentCoroutineContext().ensureActive()
            if (stopRequested) awaitCancellation()
            if (mic.isInCall()) {
                listener.onPaused(PauseReason.IN_CALL)
                mic.awaitMicLikelyFree(MAX_BACKOFF_MS)
                continue
            }
            val startedAt = SystemClock.elapsedRealtime()
            val outcome = try {
                listenOnce(model, matcher, listener)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "wake listening failed: ${e.javaClass.simpleName}")
                Outcome.FAILED
            }
            if (stopRequested || outcome == Outcome.STOPPED) awaitCancellation()
            if (SystemClock.elapsedRealtime() - startedAt >= HEALTHY_RUN_MS) backoff = INITIAL_BACKOFF_MS
            when (outcome) {
                Outcome.SILENCED -> {
                    listener.onPaused(PauseReason.MIC_BUSY)
                    mic.awaitMicLikelyFree(backoff)
                }
                Outcome.IN_CALL -> {
                    listener.onPaused(PauseReason.IN_CALL)
                    mic.awaitMicLikelyFree(MAX_BACKOFF_MS)
                }
                Outcome.FAILED -> {
                    listener.onPaused(PauseReason.MIC_ERROR)
                    delay(backoff)
                }
                Outcome.STOPPED -> Unit
            }
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private suspend fun listenOnce(model: Model, matcher: WakeMatcher, listener: Listener): Outcome {
        val recognizer = Recognizer(model, MicAudio.SAMPLE_RATE.toFloat(), matcher.grammarJson)
        try {
            recognizer.setWords(true)
            val record = MicAudio.open(context)
            activeRecord = record
            try {
                if (stopRequested) return Outcome.STOPPED
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return Outcome.FAILED
                mic.watch(record.audioSessionId)
                listener.onListening()
                val buffer = ShortArray(MicAudio.CHUNK_SAMPLES)
                var lastPartial = emptyList<String>()
                var silentSamples = 0
                var emptyReads = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (stopRequested) return Outcome.STOPPED
                    val read = record.read(buffer, 0, buffer.size)
                    if (stopRequested) return Outcome.STOPPED
                    if (read < 0) return Outcome.FAILED
                    if (read == 0) {
                        if (++emptyReads > MAX_EMPTY_READS) return Outcome.FAILED
                        continue
                    }
                    emptyReads = 0
                    if (mic.silenced) return Outcome.SILENCED
                    if (mic.isInCall()) return Outcome.IN_CALL
                    // A silenced client receives exact zeros; real microphones always have a noise floor.
                    silentSamples = if (MicAudio.isDigitalSilence(buffer, read)) silentSamples + read else 0
                    if (silentSamples >= SILENCED_AFTER_SAMPLES) return Outcome.SILENCED
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        val result = VoskJson.final(recognizer.result)
                        val partial = lastPartial
                        lastPartial = emptyList()
                        evaluate(result, partial, matcher, listener)
                    } else {
                        val partial = VoskJson.partialTokens(recognizer.partialResult)
                        if (partial.isNotEmpty()) lastPartial = partial
                    }
                }
            } finally {
                activeRecord = null
                mic.unwatch()
                MicAudio.safeStop(record)
                record.release()
            }
        } finally {
            recognizer.close()
        }
    }

    private fun evaluate(result: VoskJson.Final, lastPartial: List<String>, matcher: WakeMatcher, listener: Listener) {
        val match = matcher.match(result.tokens, lastPartial) ?: return
        val cfg = config()
        val confidence = result.minConfidence
        if (confidence != null && confidence < cfg.minConfidence) {
            Log.d(TAG, "wake candidate below confidence threshold")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (lastWakeAt != 0L && now - lastWakeAt < cfg.cooldownMillis) return
        lastWakeAt = now
        listener.onWake(match.phrase, match.heard, confidence ?: Double.NaN)
    }

    private companion object {
        const val TAG = "Bidu/Wake"
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val HEALTHY_RUN_MS = 30_000L
        const val MAX_EMPTY_READS = 50
        const val SILENCED_AFTER_SAMPLES = MicAudio.SAMPLE_RATE * 3
    }
}
