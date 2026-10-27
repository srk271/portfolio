package dev.bidu.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.bidu.app.R

/** Channel ids shared by the service (WS2), reminders and open-app fallback (WS3). Created once at startup. */
object NotificationChannels {
    const val LISTENING = "bidu.listening"
    const val RESULTS = "bidu.results"
    const val REMINDERS = "bidu.reminders"

    /** "Bidu paused — tap to resume": HIGH so it heads up, since listening stays off until the user taps it. */
    const val RESUME = "bidu.resume"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(LISTENING, context.getString(R.string.channel_listening), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_listening_description)
                    setShowBadge(false)
                },
                NotificationChannel(RESULTS, context.getString(R.string.channel_results), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_results_description)
                },
                NotificationChannel(REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_reminders_description)
                },
                NotificationChannel(RESUME, context.getString(R.string.channel_resume), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_resume_description)
                },
            ),
        )
    }
}

/** Fixed notification ids. Reminders use REMINDER_BASE + reminderId so each one can be cancelled alone. */
object NotificationIds {
    const val FOREGROUND = 1
    const val RESUME = 2
    const val RESULT = 3
    const val CONFIRMATION = 4
    const val OPEN_APP_FALLBACK = 5
    const val REMINDER_BASE = 10_000
}

/** Broadcast actions handled by `dev.bidu.app.voice.NotificationActionReceiver`. */
object NotificationActions {
    const val STOP_LISTENING = "dev.bidu.app.action.STOP_LISTENING"
    const val RESUME_LISTENING = "dev.bidu.app.action.RESUME_LISTENING"
    const val CANCEL_REQUEST = "dev.bidu.app.action.CANCEL_REQUEST"
    const val CONFIRM_YES = "dev.bidu.app.action.CONFIRM_YES"
    const val CONFIRM_NO = "dev.bidu.app.action.CONFIRM_NO"
    const val DISMISS_REMINDER = "dev.bidu.app.action.DISMISS_REMINDER"

    const val EXTRA_CONFIRMATION_ID = "dev.bidu.app.extra.CONFIRMATION_ID"
    const val EXTRA_REMINDER_ID = "dev.bidu.app.extra.REMINDER_ID"
}
