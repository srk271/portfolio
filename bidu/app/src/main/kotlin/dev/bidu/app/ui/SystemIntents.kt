package dev.bidu.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.bidu.core.model.Capability

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** The settings screen for a capability when [dev.bidu.app.permissions.DevicePermissions.settingsIntent] has none. */
fun fallbackSettingsIntent(context: Context, capability: Capability): Intent {
    val packageUri = Uri.fromParts("package", context.packageName, null)
    return when (capability) {
        Capability.DISPLAY_OVER_OTHER_APPS -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
        Capability.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
        Capability.IGNORE_BATTERY_OPTIMIZATIONS -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        Capability.POST_NOTIFICATIONS ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        else -> appDetailsIntent(context)
    }
}

/** Opens the first of [intents] that the device can show. Returns false when none could be opened. */
fun Context.openSettings(vararg intents: Intent): Boolean {
    for (intent in intents) {
        try {
            startActivity(intent)
            return true
        } catch (e: RuntimeException) {
            // ActivityNotFoundException or SecurityException: try the next screen.
        }
    }
    return false
}
