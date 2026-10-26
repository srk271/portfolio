package dev.bidu.core.tool

import dev.bidu.core.model.Capability
import dev.bidu.core.model.QuestionKind
import dev.bidu.core.model.UserQuestion

/**
 * What actually happened. Bidu never reports success it did not observe:
 *
 * - [VERIFIED]: the effect was observed (read back from storage, system callback fired, value measured).
 * - [HANDED_OFF]: an intent reached another app; the outcome there is not observable. Worded honestly
 *   ("Opened Uber with destination set; you confirm the ride there").
 * - [NEEDS_USER]: Bidu cannot continue without the user (permission, choice, tap a notification).
 * - [FAILED]: the tool ran and could not do it.
 * - [CANCELLED]: produced only by the registry/engine when the request was cancelled mid-flight.
 */
enum class ToolOutcome { VERIFIED, HANDED_OFF, NEEDS_USER, FAILED, CANCELLED }

enum class EvidenceKind(val observable: Boolean) {
    /** State read back after acting (row exists, alarm listed, torch callback reported on). */
    OBSERVED_STATE(true),
    /** The platform called back confirming the effect. */
    SYSTEM_CALLBACK(true),
    /** A value was measured directly (battery %, current time). */
    MEASURED(true),
    /** An intent was resolved and delivered; what the receiving app did is unknown. */
    INTENT_DELIVERED(false),
}

data class Evidence(
    val kind: EvidenceKind,
    val description: String,
    val data: Map<String, String> = emptyMap(),
)

enum class ToolErrorCode {
    INVALID_ARGS, PERMISSION_DENIED, NOT_FOUND, AMBIGUOUS, TIMEOUT, UNAVAILABLE, APP_NOT_INSTALLED,
    NETWORK, CANCELLED, INTERNAL,
}

data class ToolError(
    val code: ToolErrorCode,
    val message: String,
    /** True only when a retry could plausibly succeed (transient failure). */
    val retryable: Boolean = false,
)

/**
 * Result of one tool execution. Construction enforces the honesty rules: VERIFIED requires
 * observable evidence, HANDED_OFF requires a delivered intent, NEEDS_USER requires a question,
 * FAILED requires an error.
 */
data class ToolResult(
    val outcome: ToolOutcome,
    /** Short factual statement for logs and the composer, in English. */
    val summary: String,
    /** Structured facts the composer may read (e.g. "time" -> "19:05", "app" -> "WhatsApp"). */
    val data: Map<String, String> = emptyMap(),
    val evidence: List<Evidence> = emptyList(),
    val error: ToolError? = null,
    val question: UserQuestion? = null,
) {
    init {
        when (outcome) {
            ToolOutcome.VERIFIED -> require(evidence.any { it.kind.observable }) { "VERIFIED needs observable evidence" }
            ToolOutcome.HANDED_OFF -> require(evidence.any { it.kind == EvidenceKind.INTENT_DELIVERED }) { "HANDED_OFF needs INTENT_DELIVERED evidence" }
            ToolOutcome.NEEDS_USER -> require(question != null) { "NEEDS_USER needs a question" }
            ToolOutcome.FAILED -> require(error != null) { "FAILED needs an error" }
            ToolOutcome.CANCELLED -> Unit
        }
    }

    companion object {
        fun verified(summary: String, evidence: Evidence, data: Map<String, String> = emptyMap()): ToolResult =
            ToolResult(ToolOutcome.VERIFIED, summary, data, listOf(evidence))

        fun observed(summary: String, description: String, data: Map<String, String> = emptyMap()): ToolResult =
            verified(summary, Evidence(EvidenceKind.OBSERVED_STATE, description, data), data)

        fun measured(summary: String, description: String, data: Map<String, String> = emptyMap()): ToolResult =
            verified(summary, Evidence(EvidenceKind.MEASURED, description, data), data)

        fun handedOff(summary: String, targetPackage: String, action: String, data: Map<String, String> = emptyMap()): ToolResult =
            ToolResult(
                ToolOutcome.HANDED_OFF,
                summary,
                data + mapOf("target_package" to targetPackage),
                listOf(Evidence(EvidenceKind.INTENT_DELIVERED, "Intent $action delivered to $targetPackage")),
            )

        fun needsUser(summary: String, question: UserQuestion, data: Map<String, String> = emptyMap()): ToolResult =
            ToolResult(ToolOutcome.NEEDS_USER, summary, data, question = question)

        fun needsPermission(capabilities: Set<Capability>, summary: String = "Missing permission"): ToolResult =
            needsUser(
                summary,
                UserQuestion(
                    text = "Grant: " + capabilities.joinToString { it.userLabel },
                    kind = QuestionKind.PERMISSION,
                    capabilities = capabilities,
                ),
            )

        fun failed(code: ToolErrorCode, message: String, retryable: Boolean = false, data: Map<String, String> = emptyMap()): ToolResult =
            ToolResult(ToolOutcome.FAILED, message, data, error = ToolError(code, message, retryable))

        fun cancelled(summary: String = "Cancelled"): ToolResult = ToolResult(ToolOutcome.CANCELLED, summary)
    }
}
