package dev.bidu.app.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.bidu.app.R
import dev.bidu.app.biduContainer
import dev.bidu.app.notifications.NotificationActions
import dev.bidu.app.notifications.NotificationChannels
import dev.bidu.app.notifications.NotificationIds
import dev.bidu.core.model.Reminder
import dev.bidu.core.model.ReminderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/** Fires reminder alarms (notification with Done / Snooze 10 min) and handles those two buttons. */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(NotificationActions.EXTRA_REMINDER_ID, -1L)
        val action = intent.action
        if (reminderId <= 0 || action == null || action !in HANDLED) return

        val app = context.applicationContext
        val container = app.biduContainer
        val pending = goAsync()
        container.appScope.launch(Dispatchers.IO) {
            try {
                val finished = withTimeoutOrNull(WORK_TIMEOUT_MILLIS) {
                    when (action) {
                        ReminderAlarms.ACTION_FIRE -> fire(app, reminderId)
                        ReminderAlarms.ACTION_DONE -> done(app, reminderId)
                        ReminderAlarms.ACTION_SNOOZE -> snooze(app, reminderId)
                        else -> Unit
                    }
                    true
                }
                if (finished == null) Log.w(TAG, "Reminder work timed out for $reminderId")
            } catch (e: Exception) {
                Log.w(TAG, "Reminder action failed for $reminderId: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, reminderId: Long) {
        val reminders = context.biduContainer.reminders
        ReminderAlarms.existingFirePendingIntent(context, reminderId)?.cancel()
        val reminder = reminders.get(reminderId) ?: return
        if (reminder.status != ReminderStatus.SCHEDULED) return
        val posted = postNotification(context, reminder)
        reminders.updateStatus(reminderId, ReminderStatus.FIRED)
        if (!posted) Log.w(TAG, "Reminder $reminderId fired but notifications are blocked")
    }

    private suspend fun done(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
        val reminders = context.biduContainer.reminders
        val reminder = reminders.get(reminderId) ?: return
        if (reminder.status == ReminderStatus.FIRED || reminder.status == ReminderStatus.SCHEDULED) {
            context.biduContainer.reminderScheduler.cancel(reminderId)
            reminders.updateStatus(reminderId, ReminderStatus.DISMISSED)
        }
    }

    /** Snoozing creates a fresh reminder 10 minutes out and retires the fired one. */
    private suspend fun snooze(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
        val container = context.biduContainer
        val reminder = container.reminders.get(reminderId) ?: return
        if (reminder.status == ReminderStatus.CANCELLED || reminder.status == ReminderStatus.DISMISSED) return
        val dueAt = container.clock.now().plus(Duration.ofMinutes(ReminderAlarms.SNOOZE_MINUTES))
        val snoozed = container.reminders.create(reminder.text, dueAt, container.reminderScheduler.canScheduleExact())
        container.reminderScheduler.schedule(snoozed)
        container.reminders.updateStatus(reminderId, ReminderStatus.DISMISSED)
    }

    private fun postNotification(context: Context, reminder: Reminder): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_bidu)
            .setContentTitle(context.getString(R.string.notification_reminder_title))
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(reminder.dueAt.toEpochMilli())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, reminder.id))
            .addAction(
                0,
                context.getString(R.string.reminder_action_done),
                ReminderAlarms.actionPendingIntent(context, ReminderAlarms.ACTION_DONE, reminder.id),
            )
            .addAction(
                0,
                context.getString(R.string.reminder_action_snooze),
                ReminderAlarms.actionPendingIntent(context, ReminderAlarms.ACTION_SNOOZE, reminder.id),
            )
            .build()
        return try {
            manager.notify(notificationId(reminder.id), notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun openAppIntent(context: Context, reminderId: Long): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(
            context,
            notificationId(reminderId),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun notificationId(reminderId: Long) = NotificationIds.REMINDER_BASE + reminderId.toInt()

    private companion object {
        const val TAG = "BiduReminders"
        const val WORK_TIMEOUT_MILLIS = 9_000L
        val HANDLED = setOf(ReminderAlarms.ACTION_FIRE, ReminderAlarms.ACTION_DONE, ReminderAlarms.ACTION_SNOOZE)
    }
}
