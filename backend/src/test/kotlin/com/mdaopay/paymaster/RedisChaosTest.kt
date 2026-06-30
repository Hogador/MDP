package com.mdaopay.paymaster

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

/**
 * Chaos test: Redis failure scenario.
 *
 * Simulates Redis being completely down (all calls return null/false).
 * Verifies that the system degrades gracefully via in-memory fallback
 * instead of crashing or returning errors to the user.
 *
 * Related findings: F-032 (RedisRateLimiter fallback)
 */
class RedisChaosTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `rate limiting falls back when Redis down`() = runTest {
        // Simulate complete Redis outage — all calls return null/false
        mockkObject(Redis)
        coEvery { Redis.incr(any<String>()) } returns null
        coEvery { Redis.expire(any<String>(), any()) } returns false
        coEvery { Redis.setEx(any<String>(), any(), any()) } returns false
        coEvery { Redis.exists(any<String>()) } returns false

        val limiter = RedisRateLimiter("chaos-test")

        // First request — should NOT be limited (in-memory fallback)
        assertFalse(
            limiter.isLimited("test", maxRequests = 5, windowSec = 60),
            "Should not limit on first request when Redis is down"
        )
    }

    @Test
    fun `rate limiting respects maxRequests during Redis outage`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.incr(any<String>()) } returns null
        coEvery { Redis.expire(any<String>(), any()) } returns false

        val limiter = RedisRateLimiter("chaos-test")
        val key = "burst-test"

        // Fire 5 requests with max=3 — request #4 should be limited
        for (i in 1..3) {
            val limited = limiter.isLimited(key, maxRequests = 3, windowSec = 60)
            assertFalse(limited, "Request $i should not be limited (max=3)")
        }

        // 4th request — should be limited
        val limited = limiter.isLimited(key, maxRequests = 3, windowSec = 60)
        assert(limited) { "4th request SHOULD be limited (max=3, in-memory fallback)" }
    }

    @Test
    fun `replay cache falls back when Redis down`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.exists(any<String>()) } returns false
        coEvery { Redis.setEx(any<String>(), any(), any()) } returns false

        val cache = RedisReplayCache("chaos-test")

        // First use — not used yet
        val r1 = cache.isUsed("tx-hash-1")
        assertFalse(r1, "First use should not be detected as used")

        // Second use with same key — should detect replay via in-memory fallback
        val r2 = cache.isUsed("tx-hash-1")
        assert(r2) { "Second use SHOULD be detected as replay via in-memory fallback" }
    }
}
