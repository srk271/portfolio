package dev.bidu.core.plan

import dev.bidu.core.model.say
import dev.bidu.core.nlu.Intent
import kotlinx.coroutines.CancellationException

/**
 * Offline first. Cloud reasoning is consulted only when the offline understanding is unknown or
 * low-confidence, [cloudAllowed] says yes, and the offline plan is not a deliberate refusal. A cloud
 * failure, a cloud "not understood" or a cloud plan that uses tools it was not given falls back to the
 * offline answer, worded honestly.
 */
class FallbackPlanner(
    private val offline: Planner,
    private val cloud: Planner,
    private val cloudAllowed: () -> Boolean,
) : Planner {

    override suspend fun plan(request: PlanRequest): Plan {
        val local = offline.plan(request)
        val unsure = request.nlu.intent is Intent.Unknown || request.nlu.confidence < RulePlanner.LOW_CONFIDENCE
        if (!unsure) return local
        if (local is Plan.Decline && local.reason != DeclineReason.NOT_UNDERSTOOD) return local
        if (!allowed()) return local

        val remote = try {
            cloud.plan(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val allowedTools = request.tools.map { it.name }.toSet()
        return when {
            remote == null -> cloudFailed(request, local)
            remote is Plan.Execute && remote.calls.any { it.tool !in allowedTools } -> cloudFailed(request, local)
            remote is Plan.Decline && remote.reason == DeclineReason.NOT_UNDERSTOOD -> local
            else -> remote
        }
    }

    private fun allowed(): Boolean = try {
        cloudAllowed()
    } catch (e: Exception) {
        false
    }

    private fun cloudFailed(request: PlanRequest, local: Plan): Plan {
        if (local !is Plan.Decline) return local
        val lang = request.context.language
        return Plan.Decline(
            DeclineReason.NOT_UNDERSTOOD,
            lang.say(
                "Samajh nahi aaya, aur cloud se bhi jawab nahi mila. Thoda alag tareeke se bolo.",
                "I didn't understand that, and cloud reasoning didn't answer either. Try saying it differently.",
                "समझ नहीं आया, और क्लाउड से भी जवाब नहीं मिला। थोड़ा अलग तरह से बोलिए।",
            ),
            PlanSource.RULES,
        )
    }
}
