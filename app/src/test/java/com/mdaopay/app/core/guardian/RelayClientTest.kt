package com.mdaopay.app.core.guardian

import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * F-104 regression: RelayClient circuit breaker + retry.
 *
 * Tests circuit breaker state machine in isolation.
 * Full HTTP retry tests (4xx vs 5xx) require MockWebServer — see androidTest.
 */
class RelayClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var client: RelayClient

    @Before
    fun setUp() {
        // Reset circuit breaker state before each test
        RelayClient.failureCount.set(0)
        RelayClient.circuitOpenUntil = 0L
        client = RelayClient()
    }

    @After
    fun tearDown() {
        // Reset state for next test
        RelayClient.failureCount.set(0)
        RelayClient.circuitOpenUntil = 0L
    }

    // ── Circuit breaker constants ──

    @Test
    fun `circuit breaker constants are reasonable`() {
        assertTrue(RelayClient.MAX_ATTEMPTS >= 1)
        assertTrue(RelayClient.CIRCUIT_THRESHOLD >= 1)
        assertTrue(RelayClient.CIRCUIT_RESET_MS >= 1_000L)
    }

    // ── Circuit breaker state machine ──

    @Test
    fun `circuit breaker starts closed`() {
        assertFalse("Circuit should start closed", client.isCircuitOpen())
    }

    @Test
    fun `circuit opens after threshold failures`() {
        val threshold = RelayClient.CIRCUIT_THRESHOLD

        // Fail repeatedly
        for (i in 1 until threshold) {
            client.recordFailure()
            assertFalse("Circuit should stay closed after $i failures", client.isCircuitOpen())
        }

        // One more triggers open
        client.recordFailure()
        assertTrue("Circuit should open after $threshold failures", client.isCircuitOpen())
    }

    @Test
    fun `success resets circuit breaker`() {
        val threshold = RelayClient.CIRCUIT_THRESHOLD

        // Open the circuit
        repeat(threshold) { client.recordFailure() }
        assertTrue("Circuit should open", client.isCircuitOpen())

        // Success resets
        client.recordSuccess()
        assertFalse("Circuit should close on success", client.isCircuitOpen())
        assertEquals("Failure count should be 0", 0, RelayClient.failureCount.get())
    }

    @Test
    fun `circuit auto-recovers after reset period`() {
        val threshold = RelayClient.CIRCUIT_THRESHOLD

        repeat(threshold) { client.recordFailure() }
        assertTrue("Circuit should open", client.isCircuitOpen())

        // Simulate time passing beyond CIRCUIT_RESET_MS
        RelayClient.circuitOpenUntil = System.currentTimeMillis() - 1L

        assertFalse("Circuit should auto-close after timeout", client.isCircuitOpen())
    }

    @Test
    fun `partial failures don't open circuit`() {
        val threshold = RelayClient.CIRCUIT_THRESHOLD

        repeat(threshold - 1) { client.recordFailure() }

        // Not open yet
        assertFalse("Circuit should not open before threshold", client.isCircuitOpen())
        assertEquals(threshold - 1, RelayClient.failureCount.get())
    }

    // ── Success tracking ──

    @Test
    fun `recordSuccess after failures resets to zero`() {
        repeat(3) { client.recordFailure() }
        assertEquals(3, RelayClient.failureCount.get())

        client.recordSuccess()
        assertEquals(0, RelayClient.failureCount.get())
        assertEquals(0L, RelayClient.circuitOpenUntil)
    }

    // ── ApiResponse serialization ──

    @Test
    fun `ApiResponse deserializes success with data`() {
        val jsonStr = """{"success":true,"data":"hello"}"""
        val result = json.decodeFromString<ApiResponse<String>>(jsonStr)
        assertTrue(result.success)
        assertEquals("hello", result.data)
        assertNull(result.error)
    }

    @Test
    fun `ApiResponse deserializes success with Unit`() {
        val jsonStr = """{"success":true}"""
        val result = json.decodeFromString<ApiResponse<Unit>>(jsonStr)
        assertTrue(result.success)
        assertNull(result.data)
        assertNull(result.error)
    }

    @Test
    fun `ApiResponse deserializes failure with error`() {
        val jsonStr = """{"success":false,"error":"bad request"}"""
        val result = json.decodeFromString<ApiResponse<String>>(jsonStr)
        assertFalse(result.success)
        assertEquals("bad request", result.error)
        assertNull(result.data)
    }

    @Test
    fun `ApiResponse deserializes success with null data`() {
        val jsonStr = """{"success":true,"data":null}"""
        val result = json.decodeFromString<ApiResponse<String>>(jsonStr)
        assertTrue(result.success)
        assertNull(result.data)
    }
}
