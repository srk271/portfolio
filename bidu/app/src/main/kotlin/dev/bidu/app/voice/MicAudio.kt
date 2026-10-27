package dev.bidu.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** 16 kHz mono PCM16 capture shared by the wake-word detector and the offline command recognizer. */
internal object MicAudio {
    const val SAMPLE_RATE = 16_000

    /** 100 ms per read keeps cancellation and silencing checks responsive. */
    const val CHUNK_SAMPLES = SAMPLE_RATE / 10

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Opens (but does not start) a VOICE_RECOGNITION recorder. The caller owns release(). */
    @SuppressLint("MissingPermission")
    fun open(context: Context): AudioRecord {
        if (!hasPermission(context)) throw SecurityException("RECORD_AUDIO not granted")
        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) throw IOException("16 kHz mono capture is not supported ($minBytes)")
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val record = try {
            AudioRecord.Builder()
                .setContext(context)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(format)
                // At least half a second of audio so a slow decode step never overruns the buffer.
                .setBufferSizeInBytes(maxOf(minBytes * 2, SAMPLE_RATE))
                .build()
        } catch (e: UnsupportedOperationException) {
            throw IOException("AudioRecord configuration rejected", e)
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IOException("AudioRecord did not initialise; the microphone may be in use")
        }
        return record
    }

    fun isDigitalSilence(buffer: ShortArray, count: Int): Boolean {
        for (i in 0 until count) if (buffer[i].toInt() != 0) return false
        return true
    }

    fun safeStop(record: AudioRecord) {
        try {
            record.stop()
        } catch (_: IllegalStateException) {
        }
    }
}

/**
 * Watches for the platform taking the microphone away: another app silencing our capture
 * (Android 10+ concurrent-capture policy) and calls/VoIP via the audio mode. Event driven, so a
 * paused detector waits on [awaitMicLikelyFree] instead of polling.
 */
internal class MicMonitor(context: Context) {
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = context.mainExecutor
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()
    private var registered = false

    @Volatile private var watchedSession = 0

    @Volatile var silenced: Boolean = false
        private set

    @Volatile private var inCallFlag = false

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>?) {
            val session = watchedSession
            silenced = session != 0 && configs.orEmpty().any { it.clientAudioSessionId == session && it.isClientSilenced }
            changes.trySend(Unit)
        }
    }

    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        inCallFlag = isCallMode(mode)
        changes.trySend(Unit)
    }

    fun start() {
        synchronized(lock) {
            if (registered) return
            inCallFlag = isCallMode(audioManager.mode)
            audioManager.registerAudioRecordingCallback(recordingCallback, mainHandler)
            audioManager.addOnModeChangedListener(mainExecutor, modeListener)
            registered = true
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!registered) return
            audioManager.unregisterAudioRecordingCallback(recordingCallback)
            audioManager.removeOnModeChangedListener(modeListener)
            registered = false
            watchedSession = 0
            silenced = false
        }
    }

    fun watch(audioSessionId: Int) {
        silenced = false
        watchedSession = audioSessionId
    }

    fun unwatch() {
        watchedSession = 0
        silenced = false
    }

    fun isInCall(): Boolean = if (registered) inCallFlag else isCallMode(audioManager.mode)

    /** Returns when no call is active and nobody else is recording, or after [timeoutMillis]. */
    suspend fun awaitMicLikelyFree(timeoutMillis: Long) {
        withTimeoutOrNull(timeoutMillis) {
            while (true) {
                if (!isInCall() && audioManager.activeRecordingConfigurations.isEmpty()) break
                changes.receive()
            }
        }
    }

    // NORMAL is 0; RINGTONE, IN_CALL, IN_COMMUNICATION, CALL_SCREENING and the redirect modes are all > 0.
    private fun isCallMode(mode: Int): Boolean = mode > AudioManager.MODE_NORMAL
}

/** Short tone plus a haptic tick that tells the user "speak now". Never blocks for long. */
internal class CaptureCue(private val context: Context) {
    suspend fun play() {
        vibrate()
        withContext(Dispatchers.Default) {
            val tone = try {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
            } catch (e: RuntimeException) {
                Log.w(TAG, "cue tone unavailable: ${e.javaClass.simpleName}")
                null
            }
            if (tone != null) {
                try {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_MILLIS)
                    // Let the beep finish so the recognizer does not hear it.
                    delay(TONE_MILLIS + 60L)
                } finally {
                    tone.release()
                }
            }
        }
    }

    private fun vibrate() {
        try {
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
            if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } catch (e: RuntimeException) {
            Log.w(TAG, "haptic cue failed: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "Bidu/Voice"
        const val TONE_VOLUME = 70
        const val TONE_MILLIS = 120
    }
}

/** One command capture; [cancel] may come from any thread (notification, UI, engine cancel). */
internal class CaptureSession {
    @Volatile var cancelled: Boolean = false
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun onCancel(block: () -> Unit): () -> Unit {
        listeners.add(block)
        if (cancelled) block()
        return { listeners.remove(block) }
    }

    fun cancel() {
        if (cancelled) return
        cancelled = true
        listeners.forEach { it() }
    }
}
