package dev.bidu.app.voice

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import dev.bidu.app.AppContainer
import dev.bidu.app.biduContainer
import dev.bidu.app.notifications.NotificationIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Microphone foreground service that keeps wake-word listening alive while the owner uses other
 * apps. The persistent notification always shows what Bidu is doing and carries Stop. If Android
 * refuses the microphone foreground start (background start, mic permission revoked), the service
 * stops cleanly and posts "Bidu paused — tap to resume" instead of listening invisibly.
 */
class BiduForegroundService : Service() {

    private var scope: CoroutineScope? = null
    private var notificationJob: Job? = null
    private var inForeground = false
    private var startFailed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        val container = biduContainer
        if (!enterForeground(container)) return START_NOT_STICKY
        stopRequested.set(false)
        when (action) {
            ACTION_PAUSE -> snooze(container)
            ACTION_REFRESH -> Unit
            ACTION_RESUME -> {
                clearSnooze()
                container.voice.startWakeListening()
                // Started from the resume notification: remember that listening is wanted again.
                scope?.launch { container.settings.update { it.copy(listeningEnabled = true) } }
            }
            else -> if (_snoozedUntil.value == null) container.voice.startWakeListening()
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping Bidu out of Recents must not stop listening; only Stop does.
        Log.i(TAG, "task removed; service keeps running")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        _running.value = false
        val container = biduContainer
        container.voice.stopWakeListening()
        clearSnooze()
        notificationJob = null
        scope?.cancel()
        scope = null
        if (inForeground) stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
        val userStopped = stopRequested.getAndSet(false)
        if (!userStopped && !startFailed && container.settings.current().listeningEnabled) {
            // Stopped by the system rather than by the owner: offer a one-tap restart.
            ListeningNotification.postResume(this)
        }
        super.onDestroy()
    }

    /** Must run first in onStartCommand: Android allows only a few seconds before startForeground. */
    private fun enterForeground(container: AppContainer): Boolean {
        if (inForeground) {
            ListeningNotification.post(this, NotificationIds.FOREGROUND, buildNotification(container))
            return true
        }
        try {
            startForeground(NotificationIds.FOREGROUND, buildNotification(container), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            return failStart("foreground start not allowed from background")
        } catch (e: SecurityException) {
            return failStart("microphone foreground service refused: ${e.javaClass.simpleName}")
        } catch (e: IllegalStateException) {
            return failStart("foreground start failed: ${e.javaClass.simpleName}")
        }
        inForeground = true
        if (!ListeningNotification.listeningVisible(this)) {
            // Listening must never be invisible: without a visible notification there is no listening.
            Log.w(TAG, "notifications blocked; not listening")
            stopForeground(STOP_FOREGROUND_REMOVE)
            inForeground = false
            startFailed = true
            stopSelf()
            return false
        }
        startFailed = false
        ListeningNotification.cancel(this, NotificationIds.RESUME)
        _running.value = true
        observeState(container)
        return true
    }

    private fun failStart(reason: String): Boolean {
        Log.w(TAG, reason)
        startFailed = true
        ListeningNotification.postResume(this)
        stopSelf()
        return false
    }

    private fun observeState(container: AppContainer) {
        if (notificationJob != null) return
        val serviceScope = scope ?: return
        val speaking: StateFlow<Boolean> = (container.speaker as? AndroidSpeaker)?.speaking ?: MutableStateFlow(false)
        notificationJob = serviceScope.launch {
            combine(
                container.assistant.uiState,
                container.voice.state,
                container.settings.settings,
                _snoozedUntil,
                speaking,
            ) { ui, voice, settings, snoozedUntil, isSpeaking ->
                ListeningNotification.foregroundContent(this@BiduForegroundService, ui, voice, settings.wakePhrases, snoozedUntil, isSpeaking)
            }.collectLatest { content ->
                // Coalesce bursts of state changes; the platform rate-limits notification updates.
                delay(UPDATE_DEBOUNCE_MS)
                ListeningNotification.post(this@BiduForegroundService, NotificationIds.FOREGROUND, ListeningNotification.foreground(this@BiduForegroundService, content))
            }
        }
    }

    private fun buildNotification(container: AppContainer): Notification {
        val content = ListeningNotification.foregroundContent(
            context = this,
            ui = null,
            voice = container.voice.state.value,
            phrases = container.settings.current().wakePhrases,
            snoozedUntil = _snoozedUntil.value,
            speaking = false,
        )
        return ListeningNotification.foreground(this, content)
    }

    private fun snooze(container: AppContainer) {
        val until = Instant.now().plusMillis(SNOOZE_MILLIS)
        _snoozedUntil.value = until
        container.voice.stopWakeListening()
        val alarms = getSystemService(AlarmManager::class.java) ?: return
        alarms.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + SNOOZE_MILLIS,
            ListeningNotification.snoozeElapsedIntent(this),
        )
    }

    private fun clearSnooze() {
        if (_snoozedUntil.value == null) return
        _snoozedUntil.value = null
        getSystemService(AlarmManager::class.java)?.cancel(ListeningNotification.snoozeElapsedIntent(this))
    }

    companion object {
        private const val TAG = "Bidu/Service"
        private const val SNOOZE_MILLIS = 30L * 60 * 1000
        private const val UPDATE_DEBOUNCE_MS = 150L

        const val ACTION_START = "dev.bidu.app.action.SERVICE_START"
        const val ACTION_RESUME = "dev.bidu.app.action.SERVICE_RESUME"
        const val ACTION_PAUSE = "dev.bidu.app.action.SERVICE_PAUSE"
        const val ACTION_REFRESH = "dev.bidu.app.action.SERVICE_REFRESH"

        private val _running = MutableStateFlow(false)

        /** True between a successful startForeground and onDestroy. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _snoozedUntil = MutableStateFlow<Instant?>(null)
        val snoozedUntil: StateFlow<Instant?> = _snoozedUntil.asStateFlow()

        private val stopRequested = AtomicBoolean(false)

        /**
         * Starts listening. Returns false when Android refuses a microphone foreground start right
         * now (app in the background without a user interaction); callers then post the resume
         * notification so one tap starts it.
         */
        fun start(context: Context, action: String = ACTION_START): Boolean = try {
            context.startForegroundService(Intent(context, BiduForegroundService::class.java).setAction(action))
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException is an IllegalStateException.
            Log.w(TAG, "start refused: ${e.javaClass.simpleName}")
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "start refused: ${e.javaClass.simpleName}")
            false
        }

        /** Stops listening at the owner's request; the microphone is released immediately. */
        fun stop(context: Context) {
            if (_running.value) stopRequested.set(true)
            context.stopService(Intent(context, BiduForegroundService::class.java))
        }

        /** Ends a "Pause 30 min" snooze if the service is still running. */
        internal fun resumeAfterSnooze(context: Context) {
            if (!_running.value) return
            try {
                // The app already has a foreground service, so a plain start is allowed here.
                context.startService(Intent(context, BiduForegroundService::class.java).setAction(ACTION_RESUME))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "snooze resume refused: ${e.javaClass.simpleName}")
                ListeningNotification.postResume(context)
            }
        }
    }
}
