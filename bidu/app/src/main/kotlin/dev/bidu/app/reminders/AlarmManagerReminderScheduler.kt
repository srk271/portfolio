package dev.bidu.app.reminders

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import dev.bidu.app.data.ReminderRepository
import dev.bidu.app.notifications.NotificationIds
import dev.bidu.core.model.Clock
import dev.bidu.core.model.Reminder
import dev.bidu.core.model.ReminderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Exact alarm (setExactAndAllowWhileIdle) when the platform allows it, otherwise the inexact
 * setAndAllowWhileIdle fallback; the result and the stored row both record which one was used.
 */
class AlarmManagerReminderScheduler(
    context: Context,
    private val reminders: ReminderRepository,
    private val clock: Clock,
) : ReminderScheduler {
    private val app = context.applicationContext
    private val alarmManager: AlarmManager = app.getSystemService(AlarmManager::class.java)

    override suspend fun schedule(reminder: Reminder): ScheduledAlarm = withContext(Dispatchers.IO) {
        val earliest = clock.now().plusMillis(PAST_DUE_DELAY_MILLIS)
        val triggerAt = if (reminder.dueAt.isBefore(earliest)) earliest else reminder.dueAt
        val exact = arm(reminder.id, triggerAt)
        if (exact != reminder.exact) reminders.updateExact(reminder.id, exact)
        ScheduledAlarm(reminder.id, triggerAt, exact)
    }

    override suspend fun cancel(reminderId: Long) = withContext(Dispatchers.IO) {
        ReminderAlarms.existingFirePendingIntent(app, reminderId)?.let { pending ->
            alarmManager.cancel(pending)
            pending.cancel()
        }
        NotificationManagerCompat.from(app).cancel(NotificationIds.REMINDER_BASE + reminderId.toInt())
    }

    override suspend fun rescheduleAll(): Int = withContext(Dispatchers.IO) {
        val now = clock.now()
        var count = 0
        reminders.scheduled().sortedBy { it.dueAt }.forEachIndexed { index, reminder ->
            // Past-due reminders fire right away, a few seconds apart so their notifications stay distinct.
            val due = if (reminder.dueAt.isAfter(now)) reminder.dueAt else now.plusMillis(PAST_DUE_DELAY_MILLIS * (index + 1))
            try {
                val exact = arm(reminder.id, due)
                if (exact != reminder.exact) reminders.updateExact(reminder.id, exact)
                count++
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not re-arm reminder ${reminder.id}: ${e.javaClass.simpleName}")
            }
        }
        count
    }

    override fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    /** Returns true when an exact alarm was accepted, false for the inexact fallback. */
    private fun arm(reminderId: Long, triggerAt: Instant): Boolean {
        val pending = ReminderAlarms.firePendingIntent(app, reminderId)
        val at = triggerAt.toEpochMilli()
        if (alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return true
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm refused, using inexact fallback")
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        return false
    }

    /** Whether the row is still SCHEDULED and its alarm PendingIntent is registered. */
    suspend fun isScheduled(reminderId: Long): Boolean {
        val row = reminders.get(reminderId) ?: return false
        return row.status == ReminderStatus.SCHEDULED && ReminderAlarms.isArmed(app, reminderId)
    }

    private companion object {
        const val TAG = "BiduReminders"
        const val PAST_DUE_DELAY_MILLIS = 2_000L
    }
}
