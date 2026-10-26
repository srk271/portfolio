package dev.bidu.core.policy

import dev.bidu.core.model.Capability
import dev.bidu.core.model.PermissionStatus

/**
 * Answers "may Bidu use this capability right now?". The core only checks; requesting happens in
 * the UI (runtime permission dialogs cannot be shown from the background service). When something
 * is missing the engine reports NEEDS_USER and the app deep-links to the right settings screen.
 */
interface PermissionGateway {
    fun status(capability: Capability): PermissionStatus

    fun missing(capabilities: Set<Capability>): Set<Capability> =
        capabilities.filterTo(mutableSetOf()) { status(it) != PermissionStatus.GRANTED }
}
