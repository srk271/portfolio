package dev.bidu.app.tools

import android.Manifest
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.bidu.app.R
import dev.bidu.app.notifications.NotificationChannels
import dev.bidu.app.notifications.NotificationIds
import dev.bidu.core.model.Capability
import dev.bidu.core.model.QuestionKind
import dev.bidu.core.model.UserQuestion
import dev.bidu.core.tool.ToolErrorCode
import dev.bidu.core.tool.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Starts activities on behalf of the assistant. Android blocks activity starts from the background,
 * so a direct start happens only when Bidu is in the foreground or holds "Display over other apps";
 * otherwise a "Tap to open X" notification is posted and the caller reports NEEDS_USER.
 */
class IntentLauncher(context: Context) {
    private val app = context.applicationContext

    sealed interface Outcome {
        /** startActivity returned normally; [targetPackage] is the resolved handler. */
        data class Started(val targetPackage: String) : Outcome
        data class NotificationPosted(val targetPackage: String) : Outcome
        data object NoHandler : Outcome
        /** Background start not allowed and notifications are blocked: nothing reached the user. */
        data object Blocked : Outcome
        data class Failed(val code: ToolErrorCode, val reason: String) : Outcome
    }

    fun resolve(intent: Intent): ResolveInfo? {
        val pm = app.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }

    fun canResolve(intent: Intent): Boolean = resolve(intent) != null

    fun launchIntentFor(packageName: String): Intent? = app.packageManager.getLaunchIntentForPackage(packageName)

    fun canStartDirectly(): Boolean = Settings.canDrawOverlays(app) || isInForeground()

    /** [label] names the destination in the fallback notification ("Tap to open WhatsApp"). */
    suspend fun launch(intent: Intent, label: String): Outcome = withContext(Dispatchers.Main.immediate) {
        val target = resolve(intent)?.activityInfo?.packageName ?: return@withContext Outcome.NoHandler
        val startable = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (canStartDirectly()) {
            try {
                app.startActivity(startable)
                return@withContext Outcome.Started(target)
            } catch (e: ActivityNotFoundException) {
                return@withContext Outcome.NoHandler
            } catch (e: SecurityException) {
                return@withContext Outcome.Failed(ToolErrorCode.PERMISSION_DENIED, "The target app refused the request")
            }
        }
        if (postTapToOpen(startable, label)) Outcome.NotificationPosted(target) else Outcome.Blocked
    }

    private fun postTapToOpen(intent: Intent, label: String): Boolean {
        val manager = NotificationManagerCompat.from(app)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val pending = PendingIntent.getActivity(
            app,
            NotificationIds.OPEN_APP_FALLBACK,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = app.getString(R.string.notification_tap_to_open, label)
        val notification = NotificationCompat.Builder(app, NotificationChannels.RESULTS)
            .setSmallIcon(R.drawable.ic_stat_bidu)
            .setContentTitle(title)
            .setContentText(app.getString(R.string.tools_tap_to_open_reason))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setTimeoutAfter(FALLBACK_TIMEOUT_MILLIS)
            .build()
        return try {
            manager.notify(NotificationIds.OPEN_APP_FALLBACK, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun isInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private companion object {
        const val FALLBACK_TIMEOUT_MILLIS = 5 * 60_000L
    }
}

/** Maps a launch [IntentLauncher.Outcome] to an honest tool result. */
internal fun IntentLauncher.Outcome.toToolResult(
    handedOffSummary: String,
    action: String,
    label: String,
    data: Map<String, String> = emptyMap(),
): ToolResult = when (this) {
    is IntentLauncher.Outcome.Started -> ToolResult.handedOff(handedOffSummary, targetPackage, action, data)
    is IntentLauncher.Outcome.NotificationPosted -> ToolResult.needsUser(
        "Android blocked opening $label from the background; posted a tap-to-open notification",
        UserQuestion(text = "Tap the notification to open $label", kind = QuestionKind.ACTION_REQUIRED),
        data + mapOf("target_package" to targetPackage, "fallback" to "notification"),
    )
    IntentLauncher.Outcome.NoHandler -> ToolResult.failed(ToolErrorCode.APP_NOT_INSTALLED, "No app on this phone can open $label", data = data)
    IntentLauncher.Outcome.Blocked -> ToolResult.needsPermission(
        setOf(Capability.DISPLAY_OVER_OTHER_APPS, Capability.POST_NOTIFICATIONS),
        "Cannot open $label from the background without 'Display over other apps' or notifications",
    )
    is IntentLauncher.Outcome.Failed -> ToolResult.failed(code, reason, data = data)
}
