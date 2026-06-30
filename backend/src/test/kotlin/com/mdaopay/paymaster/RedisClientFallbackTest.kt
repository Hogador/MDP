package com.mdaopay.paymaster

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for F-032 and F-033:
 * - RedisRateLimiter fallback when Redis unavailable
 * - RedisReplayCache fallback when Redis unavailable
 *
 * Verifies fail-closed behavior: when Redis returns null/false,
 * the in-memory fallback still enforces rate limits and replay protection.
 */
class RedisClientFallbackTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ── RedisRateLimiter (F-032) ──────────────────────────────────

    @Test
    fun `rate limiter uses Redis when available`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.incr(any<String>()) } returns 1L
        coEvery { Redis.expire(any<String>(), any()) } returns true

        val limiter = RedisRateLimiter("test")
        val limited = limiter.isLimited("key1", maxRequests = 5, windowSec = 60)

        assertFalse(limited, "First request should NOT be limited")
    }

    @Test
    fun `rate limiter falls back to in-memory when Redis incr returns null`() = runTest {
        mockkObject(Redis)
        // Simulate Redis unavailable — incr returns null
        coEvery { Redis.incr(any<String>()) } returns null

        val limiter = RedisRateLimiter("test")

        // First request: not limited
        val r1 = limiter.isLimited("fallback-key", maxRequests = 2, windowSec = 60)
        assertFalse(r1, "First request should not be limited")

        // Second request: not limited (count=2, maxRequests=2)
        val r2 = limiter.isLimited("fallback-key", maxRequests = 2, windowSec = 60)
        assertFalse(r2, "Second request should not be limited (count=2, max=2)")

        // Third request: limited (count=3 > maxRequests=2)
        val r3 = limiter.isLimited("fallback-key", maxRequests = 2, windowSec = 60)
        assertTrue(r3, "Third request SHOULD be limited (count=3 > max=2)")
    }

    @Test
    fun `rate limiter in-memory fallback resets after window expiry`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.incr(any<String>()) } returns null

        val limiter = RedisRateLimiter("test")

        // Use up all requests
        limiter.isLimited("window-key", maxRequests = 1, windowSec = 0) // 0 sec window = already expired

        // Since window is 0 seconds, the entry should be expired
        // The fallback should allow a new request (window expired)
        val result = limiter.isLimited("window-key", maxRequests = 1, windowSec = 0)
        assertFalse(result, "Request after window expiry should not be limited")
    }

    // ── RedisReplayCache (F-033) ──────────────────────────────────

    @Test
    fun `replay cache uses Redis when available`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.exists(any<String>()) } returns false
        coEvery { Redis.setEx(any<String>(), any(), any()) } returns true

        val cache = RedisReplayCache("test")
        val used = cache.isUsed("sig1")

        assertFalse(used, "First use should return false (not used yet)")
    }

    @Test
    fun `replay cache detects used key via Redis`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.exists(any<String>()) } returns true

        val cache = RedisReplayCache("test")
        val used = cache.isUsed("sig-existing")

        assertTrue(used, "Key that exists in Redis should be detected as used")
    }

    @Test
    fun `replay cache falls back to in-memory when Redis exists fails`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.exists(any<String>()) } returns false
        coEvery { Redis.setEx(any<String>(), any(), any()) } returns false // Redis unavailable

        val cache = RedisReplayCache("test")

        // First call with this key: not used
        val r1 = cache.isUsed("replay-fallback-key")
        assertFalse(r1, "First use should not be detected as used")

        // Second call with same key: should be detected as used via in-memory fallback
        val r2 = cache.isUsed("replay-fallback-key")
        assertTrue(r2, "Second use SHOULD be detected as used via in-memory fallback")
    }

    @Test
    fun `replay cache in-memory fallback respects TTL`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.exists(any<String>()) } returns false
        coEvery { Redis.setEx(any<String>(), any(), any()) } returns false

        val cache = RedisReplayCache("test")

        // First use with TTL=0 — entry expires immediately
        cache.isUsed("ttl-key", ttlSec = 0)

        // Second call with TTL=0 — entry has expired, so returns false (not used)
        val result = cache.isUsed("ttl-key", ttlSec = 0)
        assertFalse(result, "Key with TTL=0 should expire immediately")
    }
}
