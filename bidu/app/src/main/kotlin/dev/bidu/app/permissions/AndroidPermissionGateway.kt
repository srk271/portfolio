package dev.bidu.app.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dev.bidu.app.settings.SettingsRepository
import dev.bidu.core.model.Capability
import dev.bidu.core.model.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live capability checks. [status] always reads the platform directly (all checks are cheap binder
 * calls); [statuses] is the snapshot the UI observes, refreshed by [refresh] and on every [status] call.
 * Settings intents carry no task flags: callers outside an Activity add FLAG_ACTIVITY_NEW_TASK.
 */
class AndroidPermissionGateway(
    context: Context,
    private val settings: SettingsRepository,
) : DevicePermissions {
    private val app = context.applicationContext
    private val packageUri: Uri = Uri.parse("package:${app.packageName}")
    private val snapshot = MutableStateFlow(readAll())

    override val statuses: StateFlow<Map<Capability, PermissionStatus>> = snapshot.asStateFlow()

    override fun refresh() {
        snapshot.value = readAll()
    }

    override fun status(capability: Capability): PermissionStatus {
        val current = read(capability)
        if (snapshot.value[capability] != current) snapshot.value = snapshot.value + (capability to current)
        return current
    }

    override fun runtimePermissions(capability: Capability): List<String> = when (capability) {
        Capability.RECORD_AUDIO -> listOf(Manifest.permission.RECORD_AUDIO)
        Capability.POST_NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
        Capability.READ_CONTACTS -> listOf(Manifest.permission.READ_CONTACTS)
        Capability.EXACT_ALARMS,
        Capability.DISPLAY_OVER_OTHER_APPS,
        Capability.IGNORE_BATTERY_OPTIMIZATIONS,
        Capability.INTERNET,
        Capability.CLOUD_REASONING,
        -> emptyList()
    }

    override fun settingsIntent(capability: Capability): Intent? = when (capability) {
        Capability.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
        Capability.DISPLAY_OVER_OTHER_APPS -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
        Capability.IGNORE_BATTERY_OPTIMIZATIONS -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        Capability.POST_NOTIFICATIONS ->
            // Below Android 13 there is no dialog; on 13+ the dialog suffices unless the user disabled
            // notifications in system settings after granting.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            } else {
                null
            }
        Capability.INTERNET -> Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        Capability.RECORD_AUDIO, Capability.READ_CONTACTS, Capability.CLOUD_REASONING -> null
    }

    override fun batterySettingsIntent(): Intent {
        val samsung = Intent().setComponent(ComponentName(SAMSUNG_DEVICE_CARE, SAMSUNG_BATTERY_ACTIVITY))
        if (resolves(samsung)) return samsung
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    }

    private fun readAll(): Map<Capability, PermissionStatus> = Capability.entries.associateWith { read(it) }

    private fun read(capability: Capability): PermissionStatus = when (capability) {
        Capability.RECORD_AUDIO -> granted(hasPermission(Manifest.permission.RECORD_AUDIO))
        Capability.POST_NOTIFICATIONS -> granted(notificationsAllowed())
        Capability.READ_CONTACTS -> granted(hasPermission(Manifest.permission.READ_CONTACTS))
        Capability.EXACT_ALARMS -> granted(app.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true)
        Capability.DISPLAY_OVER_OTHER_APPS -> granted(Settings.canDrawOverlays(app))
        Capability.IGNORE_BATTERY_OPTIMIZATIONS ->
            granted(app.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(app.packageName) == true)
        Capability.INTERNET -> if (networkUsable()) PermissionStatus.GRANTED else PermissionStatus.UNAVAILABLE
        Capability.CLOUD_REASONING -> granted(settings.current().cloudAllowed())
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return false
        return app.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    private fun networkUsable(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun hasPermission(permission: String): Boolean =
        app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun resolves(intent: Intent): Boolean {
        val pm = app.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, 0)
        }
        return info?.activityInfo?.exported == true
    }

    private fun granted(value: Boolean) = if (value) PermissionStatus.GRANTED else PermissionStatus.DENIED

    private companion object {
        // One UI "Device care > Battery". Only resolvable when the manifest's <queries> lists this package;
        // otherwise the app-details page (Battery > Unrestricted) is used.
        const val SAMSUNG_DEVICE_CARE = "com.samsung.android.lool"
        const val SAMSUNG_BATTERY_ACTIVITY = "com.samsung.android.sm.battery.ui.BatteryActivity"
    }
}
