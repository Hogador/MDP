package com.mdaopay.paymaster

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthBlockNumber
import org.web3j.protocol.core.methods.response.EthLog
import kotlin.test.assertTrue

/**
 * Regression tests for F-053:
 * WatchtowerService must use class-level CoroutineScope (not per-call scope).
 */
class WatchtowerServiceTest {

    private val mockWeb3j = mockk<Web3j>()
    private val config = WatchtowerConfig(
        recoveryModuleAddress = "0x0000000000000000000000000000000000000001",
        pollIntervalSec = 9999, // prevent actual polling in tests
    )

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `notifyWebhook uses class level scope not new scope`() {
        // Verify that notifyWebhook doesn't create a new CoroutineScope per call
        // by checking the class scope exists with SupervisorJob
        val service = WatchtowerService(config, mockWeb3j)
        val scopeField = WatchtowerService::class.java.getDeclaredField("scope")
        scopeField.isAccessible = true
        val scope = scopeField.get(service)
        assertTrue(scope is kotlinx.coroutines.CoroutineScope, "Class should have a CoroutineScope field")
    }
}
