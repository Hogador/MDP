package com.mdaopay.app.domain.usecase

import org.junit.Assert.*
import org.junit.Test

// ponytail: orchestrator — thin delegation; core logic tested via PaymasterClientTest / SendRepository
class GaslessTransactionOrchestratorTest {

    @Test
    fun `GaslessSendResult default usedFallback is false`() {
        val result = GaslessSendResult(txHash = "0xabc")
        assertFalse(result.usedFallback)
        assertEquals("0xabc", result.txHash)
    }

    @Test
    fun `GaslessSendResult usedFallback true`() {
        val result = GaslessSendResult(txHash = "0xabc", usedFallback = true)
        assertTrue(result.usedFallback)
    }
}
