package dev.bidu.app.voice

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.text.format.DateFormat
import dev.bidu.app.R
import dev.bidu.app.assistant.AssistantUiState
import dev.bidu.app.notifications.NotificationActions
import dev.bidu.app.notifications.NotificationChannels
import dev.bidu.app.notifications.NotificationIds
import dev.bidu.app.settings.WakePhrase
import dev.bidu.core.agent.AgentState
import dev.bidu.core.model.Capability
import dev.bidu.core.policy.ConfirmationRequest
import java.time.Instant
import java.util.Date
import java.util.Locale

/** Builds and posts the FOREGROUND, RESUME, RESULT and CONFIRMATION notifications. */
object ListeningNotification {
    /** Broadcast to [NotificationActionReceiver]: run one push-to-talk capture. */
    const val ACTION_PUSH_TO_TALK = "dev.bidu.app.action.PUSH_TO_TALK"

    /** Broadcast to [NotificationActionReceiver] when a "Pause 30 min" snooze ends. */
    const val ACTION_SNOOZE_ELAPSED = "dev.bidu.app.action.SNOOZE_ELAPSED"

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    private const val RC_OPEN = 100
    private const val RC_STOP = 101
    private const val RC_TALK = 102
    private const val RC_PAUSE = 103
    private const val RC_RESUME = 104
    private const val RC_CANCEL = 105
    private const val RC_REFRESH = 106
    private const val RC_YES = 107
    private const val RC_NO = 108
    private const val RC_SNOOZE = 109

    private val WORKING_STATES = setOf(
        AgentState.UNDERSTANDING,
        AgentState.PLANNING,
        AgentState.CHECKING_PERMISSIONS,
        AgentState.EXECUTING,
        AgentState.VERIFYING,
        AgentState.REPORTING,
    )

    data class ForegroundContent(
        val title: String,
        val text: String,
        val detail: String?,
        val showTalk: Boolean,
        val showCancel: Boolean,
        val showPause: Boolean,
        val showResume: Boolean,
    )

    fun foregroundContent(
        context: Context,
        ui: AssistantUiState?,
        voice: VoiceState,
        phrases: List<WakePhrase>,
        snoozedUntil: Instant?,
        speaking: Boolean,
    ): ForegroundContent {
        val appName = context.getString(R.string.app_name)
        val pending = ui?.pendingConfirmation
        val modelMissing = ui?.model?.let { it !is ModelState.Installed } ?: !voice.wakeWordAvailable
        var title = appName
        var talk = false
        var cancel = false
        var pause = false
        var resume = false
        val text: String = when {
            voice.mode == VoiceMode.CAPTURING -> {
                cancel = true
                context.getString(R.string.voice_notification_capturing)
            }
            pending != null -> {
                cancel = true
                context.getString(R.string.voice_notification_waiting_answer)
            }
            ui != null && (ui.agentState in WORKING_STATES || ui.busy) && !speaking -> {
                cancel = true
                context.getString(R.string.notification_working)
            }
            speaking -> context.getString(R.string.voice_notification_speaking)
            snoozedUntil != null -> {
                title = context.getString(R.string.notification_paused_title)
                resume = true
                talk = true
                context.getString(R.string.voice_notification_snoozed, formatTime(context, snoozedUntil))
            }
            modelMissing -> {
                title = context.getString(R.string.voice_notification_no_model_title)
                talk = true
                context.getString(R.string.voice_notification_no_model_text)
            }
            voice.mode == VoiceMode.WAKE_LISTENING -> {
                title = context.getString(R.string.notification_listening_title)
                talk = true
                pause = true
                context.getString(R.string.voice_notification_listening_for, phraseLabel(phrases))
            }
            voice.error != null -> {
                title = context.getString(R.string.notification_paused_title)
                talk = true
                voice.error
            }
            voice.mode == VoiceMode.PAUSED -> {
                talk = true
                context.getString(R.string.voice_notification_wake_paused)
            }
            else -> context.getString(R.string.voice_notification_starting)
        }
        val extra = when {
            pending != null -> pending.prompt
            else -> ui?.lastReport?.display?.takeIf { it.isNotBlank() }?.let {
                context.getString(R.string.voice_notification_last_result, it)
            }
        }
        return ForegroundContent(
            title = title,
            text = text,
            detail = extra?.let { "$text\n$it" },
            showTalk = talk,
            showCancel = cancel,
            showPause = pause,
            showResume = resume,
        )
    }

    /** First foreground notification, built without touching the dependency graph; replaced within ~150 ms. */
    fun starting(context: Context): Notification = foreground(
        context,
        ForegroundContent(
            title = context.getString(R.string.app_name),
            text = context.getString(R.string.voice_notification_starting),
            detail = null,
            showTalk = false,
            showCancel = false,
            showPause = false,
            showResume = false,
        ),
    )

    fun foreground(context: Context, content: ForegroundContent): Notification {
        val builder = Notification.Builder(context, NotificationChannels.LISTENING)
            .setSmallIcon(R.drawable.ic_stat_bidu)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context, NotificationChannels.LISTENING))
            .setContentIntent(openAppIntent(context))
            // Android 14+ lets users swipe FGS notifications away; put it straight back so listening stays visible.
            .setDeleteIntent(serviceIntent(context, BiduForegroundService.ACTION_REFRESH, RC_REFRESH))
        content.detail?.let { builder.setStyle(Notification.BigTextStyle().bigText(it)) }
        builder.addAction(action(context, R.string.notification_action_stop, broadcast(context, NotificationActions.STOP_LISTENING, RC_STOP)))
        if (content.showCancel) {
            builder.addAction(action(context, R.string.notification_action_cancel, broadcast(context, NotificationActions.CANCEL_REQUEST, RC_CANCEL)))
        }
        if (content.showTalk) {
            builder.addAction(action(context, R.string.voice_action_talk, broadcast(context, ACTION_PUSH_TO_TALK, RC_TALK)))
        }
        if (content.showPause) {
            builder.addAction(action(context, R.string.voice_action_pause, serviceIntent(context, BiduForegroundService.ACTION_PAUSE, RC_PAUSE)))
        }
        if (content.showResume) {
            builder.addAction(action(context, R.string.notification_action_resume, resumeIntent(context)))
        }
        return builder.build()
    }

    /** "Bidu paused — tap to resume": the tap starts the microphone service from a user interaction. */
    fun resume(context: Context): Notification {
        val resume = resumeIntent(context)
        return Notification.Builder(context, NotificationChannels.RESUME)
            .setSmallIcon(R.drawable.ic_stat_bidu)
            .setContentTitle(context.getString(R.string.notification_paused_title))
            .setContentText(context.getString(R.string.notification_paused_text))
            .setContentIntent(resume)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .addAction(action(context, R.string.notification_action_resume, resume))
            .build()
    }

    fun micPermissionNeeded(context: Context): Notification =
        Notification.Builder(context, NotificationChannels.RESUME)
            .setSmallIcon(R.drawable.ic_stat_bidu)
            .setContentTitle(context.getString(R.string.voice_mic_permission_title))
            .setContentText(context.getString(R.string.voice_mic_permission_text))
            .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.voice_mic_permission_text)))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()

    fun confirmation(context: Context, request: ConfirmationRequest): Notification {
        val yes = broadcast(context, NotificationActions.CONFIRM_YES, RC_YES) {
            putExtra(NotificationActions.EXTRA_CONFIRMATION_ID, request.id)
        }
        val no = broadcast(context, NotificationActions.CONFIRM_NO, RC_NO) {
            putExtra(NotificationActions.EXTRA_CONFIRMATION_ID, request.id)
        }
        return Notification.Builder(context, NotificationChannels.RESULTS)
            .setSmallIcon(R.drawable.ic_stat_bidu)
            .setContentTitle(context.getString(R.string.voice_confirmation_title))
            .setContentText(request.prompt)
            .setStyle(Notification.BigTextStyle().bigText(request.prompt))
            .setContentIntent(openAppIntent(context))
            .setTimeoutAfter(request.timeoutMillis)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context, NotificationChannels.RESULTS))
            .addAction(action(context, R.string.notification_action_no, no))
            .addAction(action(context, R.string.notification_action_yes, yes))
            .build()
    }

    fun permissionNeeded(context: Context, capabilities: Set<Capability>): Notification {
        val text = context.getString(R.string.voice_permission_result_text, capabilities.joinToString { it.userLabel })
        return Notification.Builder(context, NotificationChannels.RESULTS)
            .setSmallIcon(R.drawable.ic_stat_bidu)
            .setContentTitle(context.getString(R.string.voice_permission_result_title))
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
    }

    /** Posts the resume notification, or the microphone-permission one when that is what blocks listening. */
    fun postResume(context: Context): Boolean {
        val notification = if (MicAudio.hasPermission(context)) resume(context) else micPermissionNeeded(context)
        return post(context, NotificationIds.RESUME, notification)
    }

    fun post(context: Context, id: Int, notification: Notification): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        return try {
            manager.notify(id, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }

    /** False when the user blocked notifications or the listening channel, i.e. listening would be invisible. */
    fun listeningVisible(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(NotificationChannels.LISTENING) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    internal fun snoozeElapsedIntent(context: Context): PendingIntent =
        broadcast(context, ACTION_SNOOZE_ELAPSED, RC_SNOOZE)

    private fun resumeIntent(context: Context): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            RC_RESUME,
            Intent(context, BiduForegroundService::class.java).setAction(BiduForegroundService.ACTION_RESUME),
            FLAGS,
        )

    private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, BiduForegroundService::class.java).setAction(action),
            FLAGS,
        )

    private fun broadcast(context: Context, action: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationActionReceiver::class.java).setAction(action).apply(extras),
            FLAGS,
        )

    private fun openAppIntent(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, RC_OPEN, launch, FLAGS)
    }

    private fun action(context: Context, titleRes: Int, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_bidu), context.getString(titleRes), intent).build()

    private fun publicVersion(context: Context, channel: String): Notification =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_bidu)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.voice_notification_public))
            .build()

    private fun phraseLabel(phrases: List<WakePhrase>): String =
        phrases.take(3).joinToString(" / ") { phrase ->
            "“" + phrase.phrase.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.getDefault()) } } + "”"
        }

    private fun formatTime(context: Context, instant: Instant): String =
        DateFormat.getTimeFormat(context).format(Date.from(instant))
}
