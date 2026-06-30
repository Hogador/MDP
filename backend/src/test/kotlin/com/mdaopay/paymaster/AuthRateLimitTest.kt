package com.mdaopay.paymaster

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Regression tests for F-054:
 * Auth endpoint rate limiting via RedisRateLimiter.
 *
 * Verifies that /auth/login (5/min/IP), /auth/register (3/min/IP),
 * and /auth/refresh (10/min/IP) return HTTP 429 when the limit is exceeded.
 *
 * Uses Ktor testApplication engine with mocked Redis to control
 * the rate limiter counter without real Redis dependency.
 */
class AuthRateLimitTest {

    private val AUTH_LOGIN_LIMIT = 5
    private val AUTH_REGISTER_LIMIT = 3
    private val AUTH_REFRESH_LIMIT = 10
    private val AUTH_WINDOW_SEC = 60L

    /** Instance mirroring the production authIpRateLimiter in Application.kt. */
    private val authIpRateLimiter = RedisRateLimiter("ratelimit:auth-ip")

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `login returns 429 after 5 attempts from same IP`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.incr(any<String>()) } returnsMany (1L..6L).toList()
        coEvery { Redis.expire(any<String>(), any()) } returns true

        testApplication {
            application {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                routing { authLoginRoute() }
            }

            val client = createClient { }

            (1..5).forEach { i ->
                val resp = client.post("/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"test@test.com","password":"pass"}""")
                }
                assertEquals(HttpStatusCode.OK, resp.status, "Login request $i should succeed (rate limit not exceeded)")
            }

            val resp6 = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"test@test.com","password":"pass"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, resp6.status, "6th login request should be rate limited (429)")
        }
    }

    @Test
    fun `register returns 429 after 3 attempts from same IP`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.incr(any<String>()) } returnsMany (1L..4L).toList()
        coEvery { Redis.expire(any<String>(), any()) } returns true

        testApplication {
            application {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                routing { authRegisterRoute() }
            }

            val client = createClient { }

            (1..3).forEach { i ->
                val resp = client.post("/auth/register") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"test@test.com","password":"pass"}""")
                }
                assertEquals(HttpStatusCode.OK, resp.status, "Register request $i should succeed")
            }

            val resp4 = client.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"test@test.com","password":"pass"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, resp4.status, "4th register request should be rate limited (429)")
        }
    }

    @Test
    fun `refresh returns 429 after 10 attempts from same IP`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.incr(any<String>()) } returnsMany (1L..11L).toList()
        coEvery { Redis.expire(any<String>(), any()) } returns true

        testApplication {
            application {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                routing { authRefreshRoute() }
            }

            val client = createClient { }

            (1..10).forEach { i ->
                val resp = client.post("/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"refreshToken":"tok_$i"}""")
                }
                assertEquals(HttpStatusCode.OK, resp.status, "Refresh request $i should succeed")
            }

            val resp11 = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refreshToken":"tok_11"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, resp11.status, "11th refresh request should be rate limited (429)")
        }
    }

    @Test
    fun `rate limit resets after window expiry`() = runTest {
        mockkObject(Redis)
        // Simulate two windows: first window 5 OK + 1 limited, then Redis key expires
        // and incr returns 1 again (new window)
        coEvery { Redis.incr(any<String>()) } returnsMany listOf(
            1L, 2L, 3L, 4L, 5L,  // first window — 5 successful
            6L,                    // 6th — limited
            1L                     // new window (simulating Redis key expiry) — allowed
        )
        coEvery { Redis.expire(any<String>(), any()) } returns true

        testApplication {
            application {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                routing { authLoginRoute() }
            }

            val client = createClient { }

            // First window: 5 OK
            (1..5).forEach { i ->
                val resp = client.post("/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"test@test.com","password":"pass"}""")
                }
                assertEquals(HttpStatusCode.OK, resp.status)
            }

            // 6th: limited
            val respLimited = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"test@test.com","password":"pass"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, respLimited.status, "6th request should be limited")

            // After window expiry (simulated via incr returning 1), request should succeed
            val respAfterReset = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"test@test.com","password":"pass"}""")
            }
            assertEquals(HttpStatusCode.OK, respAfterReset.status, "Request after window reset should succeed")
        }
    }

    // ── Route helpers (mirror Application.kt auth routes) ─────────

    private fun Route.authLoginRoute() {
        post("/auth/login") {
            val ip = call.request.local.remoteHost
            if (authIpRateLimiter.isLimited("login:$ip", AUTH_LOGIN_LIMIT, AUTH_WINDOW_SEC)) {
                call.response.status(HttpStatusCode.TooManyRequests)
                call.respond(mapOf("error" to "Too many login attempts. Try again later."))
                return@post
            }
            call.respond(mapOf("message" to "ok"))
        }
    }

    private fun Route.authRegisterRoute() {
        post("/auth/register") {
            val ip = call.request.local.remoteHost
            if (authIpRateLimiter.isLimited("register:$ip", AUTH_REGISTER_LIMIT, AUTH_WINDOW_SEC)) {
                call.response.status(HttpStatusCode.TooManyRequests)
                call.respond(mapOf("error" to "Too many registration attempts. Try again later."))
                return@post
            }
            call.respond(mapOf("message" to "ok"))
        }
    }

    private fun Route.authRefreshRoute() {
        post("/auth/refresh") {
            val ip = call.request.local.remoteHost
            if (authIpRateLimiter.isLimited("refresh:$ip", AUTH_REFRESH_LIMIT, AUTH_WINDOW_SEC)) {
                call.response.status(HttpStatusCode.TooManyRequests)
                call.respond(mapOf("error" to "Too many refresh attempts. Try again later."))
                return@post
            }
            call.respond(mapOf("message" to "ok"))
        }
    }
}
