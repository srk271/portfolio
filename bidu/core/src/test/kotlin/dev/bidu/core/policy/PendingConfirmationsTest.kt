package dev.bidu.core.policy

import dev.bidu.core.model.Language
import dev.bidu.core.model.RiskLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingConfirmationsTest {

    private fun request(id: String, timeout: Long = 5_000) = ConfirmationRequest(
        id = id,
        requestId = "req-1",
        toolName = "delete_note",
        risk = RiskLevel.HIGH,
        prompt = "Note delete kar doon?",
        language = Language.HINGLISH,
        timeoutMillis = timeout,
    )

    @Test
    fun `resolve answers the waiting request and clears pending`() = runTest {
        val gateway = PendingConfirmations()
        val decision = async { gateway.confirm(request("c1")) }
        yield()
        assertEquals("c1", gateway.pending.value?.id)
        assertTrue(gateway.resolveCurrent(approved = true))
        assertEquals(ConfirmationDecision.APPROVED, decision.await())
        assertNull(gateway.pending.value)
        assertFalse(gateway.resolve("c1", ConfirmationDecision.REJECTED))
    }

    @Test
    fun `unanswered request times out`() = runTest {
        val gateway = PendingConfirmations()
        val decision = async { gateway.confirm(request("c2", timeout = 1_000)) }
        advanceTimeBy(1_500)
        assertEquals(ConfirmationDecision.TIMEOUT, decision.await())
        assertNull(gateway.pending.value)
    }
}
