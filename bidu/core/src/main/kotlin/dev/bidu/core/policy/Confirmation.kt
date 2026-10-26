package dev.bidu.core.policy

import dev.bidu.core.model.Language
import dev.bidu.core.model.RiskLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** What the engine wants to do and why it is asking. [prompt] is already in the user's language. */
data class ConfirmationRequest(
    val id: String,
    val requestId: String,
    val toolName: String,
    val risk: RiskLevel,
    val prompt: String,
    val language: Language,
    val timeoutMillis: Long = 60_000,
)

enum class ConfirmationDecision { APPROVED, REJECTED, TIMEOUT, UNAVAILABLE }

/** Suspends until the user answers (voice, notification action or in-app dialog) or the request times out. */
interface ConfirmationGateway {
    suspend fun confirm(request: ConfirmationRequest): ConfirmationDecision
}

/**
 * The one shared confirmation gateway. The engine suspends in [confirm]; the service notification,
 * the voice yes/no path and the UI dialog all observe [pending] and call [resolve].
 */
class PendingConfirmations : ConfirmationGateway {
    private val lock = Any()
    private val waiters = mutableMapOf<String, CompletableDeferred<ConfirmationDecision>>()
    private val _pending = MutableStateFlow<ConfirmationRequest?>(null)
    val pending: StateFlow<ConfirmationRequest?> = _pending.asStateFlow()

    override suspend fun confirm(request: ConfirmationRequest): ConfirmationDecision {
        val deferred = CompletableDeferred<ConfirmationDecision>()
        synchronized(lock) {
            waiters[request.id] = deferred
            _pending.value = request
        }
        try {
            return withTimeoutOrNull(request.timeoutMillis) { deferred.await() } ?: ConfirmationDecision.TIMEOUT
        } finally {
            synchronized(lock) {
                waiters.remove(request.id)
                if (_pending.value?.id == request.id) _pending.value = null
            }
        }
    }

    /** Returns false when no such request is waiting (already answered, timed out or cancelled). */
    fun resolve(id: String, decision: ConfirmationDecision): Boolean {
        val deferred = synchronized(lock) { waiters[id] } ?: return false
        return deferred.complete(decision)
    }

    fun resolveCurrent(approved: Boolean): Boolean {
        val current = _pending.value ?: return false
        return resolve(current.id, if (approved) ConfirmationDecision.APPROVED else ConfirmationDecision.REJECTED)
    }
}
