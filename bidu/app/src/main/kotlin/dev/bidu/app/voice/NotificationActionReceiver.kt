package dev.bidu.app.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.bidu.app.biduContainer
import dev.bidu.app.notifications.NotificationActions
import dev.bidu.app.notifications.NotificationIds
import dev.bidu.core.model.ReminderStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Handles the buttons on Bidu's notifications. Not exported; only our own PendingIntents reach it. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = context.biduContainer
        when (intent.action) {
            NotificationActions.STOP_LISTENING -> container.assistant.setListening(false)
            NotificationActions.RESUME_LISTENING -> container.assistant.setListening(true)
            NotificationActions.CANCEL_REQUEST -> container.assistant.cancel()
            NotificationActions.CONFIRM_YES, NotificationActions.CONFIRM_NO -> {
                val id = intent.getStringExtra(NotificationActions.EXTRA_CONFIRMATION_ID)
                if (id != null) {
                    val answered = container.assistant.resolveConfirmation(id, intent.action == NotificationActions.CONFIRM_YES)
                    if (!answered) Log.i(TAG, "confirmation already answered or expired")
                }
                ListeningNotification.cancel(context, NotificationIds.CONFIRMATION)
            }
            NotificationActions.DISMISS_REMINDER -> dismissReminder(context, intent)
            ListeningNotification.ACTION_PUSH_TO_TALK -> container.assistant.pushToTalk()
            ListeningNotification.ACTION_SNOOZE_ELAPSED -> BiduForegroundService.resumeAfterSnooze(context)
            else -> Log.w(TAG, "unknown notification action")
        }
    }

    private fun dismissReminder(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(NotificationActions.EXTRA_REMINDER_ID, -1L)
            .takeIf { it >= 0 }
            ?: intent.getIntExtra(NotificationActions.EXTRA_REMINDER_ID, -1).toLong().takeIf { it >= 0 }
            ?: return
        ListeningNotification.cancel(context, (NotificationIds.REMINDER_BASE + reminderId).toInt())
        val container = context.biduContainer
        val pending = goAsync()
        container.appScope.launch {
            try {
                withTimeoutOrNull(RECEIVER_BUDGET_MS) { container.reminders.updateStatus(reminderId, ReminderStatus.DISMISSED) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "reminder dismiss failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "Bidu/NotifAction"
        const val RECEIVER_BUDGET_MS = 8_000L
    }
}
