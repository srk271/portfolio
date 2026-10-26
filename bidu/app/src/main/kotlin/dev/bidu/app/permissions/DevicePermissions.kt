package dev.bidu.app.permissions

import android.content.Intent
import dev.bidu.core.model.Capability
import dev.bidu.core.model.PermissionStatus
import dev.bidu.core.policy.PermissionGateway
import kotlinx.coroutines.flow.StateFlow

/**
 * Android view of [PermissionGateway]: maps each [Capability] to runtime permissions or to the
 * special-access settings screen that grants it, and keeps an observable snapshot for the UI.
 */
interface DevicePermissions : PermissionGateway {
    val statuses: StateFlow<Map<Capability, PermissionStatus>>

    /** Re-reads every status; the UI calls it from onResume and after permission results. */
    fun refresh()

    /** Runtime permission strings behind a capability; empty for special-access capabilities. */
    fun runtimePermissions(capability: Capability): List<String>

    /** Settings screen that grants a capability without a runtime dialog; null when a dialog suffices. */
    fun settingsIntent(capability: Capability): Intent?

    /** Samsung's battery/sleeping-apps screen when the device has it, otherwise the generic battery settings. */
    fun batterySettingsIntent(): Intent
}
