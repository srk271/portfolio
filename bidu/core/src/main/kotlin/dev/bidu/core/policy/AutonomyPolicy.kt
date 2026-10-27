package dev.bidu.core.policy

import dev.bidu.core.agent.RequestOrigin
import dev.bidu.core.model.Language
import dev.bidu.core.tool.ToolCall
import dev.bidu.core.tool.ToolSpec

/** User-tunable knobs. Payment/HIGH-risk confirmation is not tunable and therefore not here. */
data class PolicySettings(
    val confirmBeforeOpeningApps: Boolean = false,
    val confirmBeforeHandoffs: Boolean = false,
    val confirmationTimeoutMillis: Long = 60_000,
)

data class PolicyContext(
    val language: Language,
    val origin: RequestOrigin,
    val settings: PolicySettings,
)

sealed interface PolicyDecision {
    data object Proceed : PolicyDecision
    data class Confirm(val prompt: String, val reason: String) : PolicyDecision
    data class Deny(val reason: String) : PolicyDecision
}

sealed interface RequestScreening {
    data object Allowed : RequestScreening
    /** The utterance asks for something Bidu must never do (OTP, UPI PIN, passwords, bank credentials). */
    data class Forbidden(val reason: String) : RequestScreening
}

/**
 * Decides, per tool call, whether to run, ask or refuse. Baseline rules (the default implementation
 * must honour these; settings can only add confirmations, never remove them):
 *
 * | risk      | decision                                                   |
 * |-----------|------------------------------------------------------------|
 * | LOW       | Proceed (Confirm if settings.confirmBeforeOpeningApps for open_app) |
 * | MEDIUM    | Proceed (Confirm if settings.confirmBeforeHandoffs)        |
 * | HIGH      | Confirm, always                                            |
 * | CRITICAL  | Confirm, always, one call per request, never retried       |
 *
 * [screen] runs before planning on the raw utterance and refuses credential/OTP/PIN requests.
 */
interface AutonomyPolicy {
    fun screen(utterance: String): RequestScreening
    fun decide(call: ToolCall, spec: ToolSpec, context: PolicyContext): PolicyDecision
}
