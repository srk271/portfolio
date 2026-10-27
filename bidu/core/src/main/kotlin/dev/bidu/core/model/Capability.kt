package dev.bidu.core.model

/**
 * Platform capabilities a tool may need. The core never sees Android permission strings; the
 * app-side [dev.bidu.core.policy.PermissionGateway] maps each capability to runtime permissions,
 * special-access settings or consent flags.
 */
enum class Capability(val userLabel: String) {
    RECORD_AUDIO("Microphone"),
    POST_NOTIFICATIONS("Notifications"),
    READ_CONTACTS("Contacts"),
    EXACT_ALARMS("Exact alarms"),
    DISPLAY_OVER_OTHER_APPS("Display over other apps"),
    IGNORE_BATTERY_OPTIMIZATIONS("Unrestricted battery"),
    INTERNET("Internet"),
    CLOUD_REASONING("Cloud reasoning consent"),
}

enum class PermissionStatus {
    GRANTED,
    DENIED,
    /** The capability cannot be granted on this device or build (for example no exact-alarm support). */
    UNAVAILABLE,
}
