package dev.bidu.app.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.bidu.app.notifications.NotificationActions

/**
 * PendingIntents for reminder alarms and notification buttons. Each reminder gets its own data URI,
 * so intents never collapse into each other even when request codes collide.
 */
object ReminderAlarms {
    const val ACTION_FIRE = "dev.bidu.app.action.REMINDER_FIRE"
    const val ACTION_SNOOZE = "dev.bidu.app.action.REMINDER_SNOOZE"
    /** Shared with the notification layer; handled by [ReminderAlarmReceiver] via an explicit intent. */
    const val ACTION_DONE = NotificationActions.DISMISS_REMINDER
    const val SNOOZE_MINUTES = 10L

    fun fireIntent(context: Context, reminderId: Long): Intent = receiverIntent(context, ACTION_FIRE, reminderId)

    fun firePendingIntent(context: Context, reminderId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(reminderId),
            fireIntent(context, reminderId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** The alarm's PendingIntent if one is currently registered, without creating it. */
    fun existingFirePendingIntent(context: Context, reminderId: Long): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            requestCode(reminderId),
            fireIntent(context, reminderId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )

    fun isArmed(context: Context, reminderId: Long): Boolean = existingFirePendingIntent(context, reminderId) != null

    fun actionPendingIntent(context: Context, action: String, reminderId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(reminderId),
            receiverIntent(context, action, reminderId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun receiverIntent(context: Context, action: String, reminderId: Long): Intent =
        Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("bidu://reminder/$reminderId"))
            .putExtra(NotificationActions.EXTRA_REMINDER_ID, reminderId)

    private fun requestCode(reminderId: Long): Int = (reminderId xor (reminderId ushr 32)).toInt()
}
