package dev.bidu.app.reminders

import dev.bidu.core.model.Reminder
import java.time.Instant

/** What the AlarmManager actually accepted; [exact] is false when the inexact fallback was used. */
data class ScheduledAlarm(val reminderId: Long, val triggerAt: Instant, val exact: Boolean)

interface ReminderScheduler {
    /** Schedules or replaces the alarm for [reminder] and returns what was set. */
    suspend fun schedule(reminder: Reminder): ScheduledAlarm
    suspend fun cancel(reminderId: Long)
    /** Re-arms every SCHEDULED reminder after boot or an app update; past-due ones fire at once. Returns the count. */
    suspend fun rescheduleAll(): Int
    fun canScheduleExact(): Boolean
}
