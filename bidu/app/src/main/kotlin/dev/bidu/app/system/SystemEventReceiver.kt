package dev.bidu.app.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.bidu.app.biduContainer
import dev.bidu.app.settings.BiduSettings
import dev.bidu.app.settings.SettingsRepository
import dev.bidu.app.voice.BiduForegroundService
import dev.bidu.app.voice.ListeningNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * After a reboot or an app update: re-arms reminders and, if the owner had listening on, posts
 * "Bidu paused — tap to resume". The microphone service is never started from here; Android 14+
 * forbids that from BOOT_COMPLETED, and a tap on the notification is the user interaction that
 * makes the start legitimate. When exact-alarm access is granted, reminders are re-armed as exact.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val restart = action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
        val exactAlarmsChanged = action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (!restart && !exactAlarmsChanged) return
        val container = context.biduContainer
        val pending = goAsync()
        container.appScope.launch {
            try {
                val rescheduled = async {
                    try {
                        withTimeoutOrNull(RESCHEDULE_BUDGET_MS) { container.reminderScheduler.rescheduleAll() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "reminder reschedule failed: ${e.javaClass.simpleName}")
                        null
                    }
                }
                if (restart && loadedSettings(container.settings).listeningEnabled && !BiduForegroundService.running.value) {
                    val posted = ListeningNotification.postResume(context)
                    if (!posted) Log.w(TAG, "resume notification not shown (notifications blocked)")
                }
                val count = rescheduled.await()
                Log.i(TAG, if (count == null) "reminders not rescheduled in time" else "rescheduled $count reminders")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "system event handling failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * The settings snapshot can still hold defaults (listening off) if the first DataStore read was
     * slow. In that case wait briefly for the stored values; no change within the window means the
     * snapshot already equals what is stored.
     */
    private suspend fun loadedSettings(repository: SettingsRepository): BiduSettings {
        val current = repository.current()
        if (current.listeningEnabled) return current
        return withTimeoutOrNull(SETTINGS_WAIT_MS) { repository.settings.drop(1).first() } ?: repository.current()
    }

    private companion object {
        const val TAG = "Bidu/SystemEvent"
        const val RESCHEDULE_BUDGET_MS = 7_000L
        const val SETTINGS_WAIT_MS = 2_000L
    }
}
