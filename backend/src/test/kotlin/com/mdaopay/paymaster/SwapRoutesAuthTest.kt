package com.mdaopay.paymaster

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigInteger
import kotlin.test.assertEquals

/**
 * Regression tests for F-035:
 * Swap routes must require JWT authentication.
 *
 * Verifies that /v1/swap/quote and /v1/swap/execute return HTTP 401
 * when no Bearer token is provided.
 */
class SwapRoutesAuthTest {

    // Shared mocks configurable per-test via swapRoutesSetup
    private var rateLimiterIsLimited: Boolean = false
    private val mockRateLimiter = mockk<RedisRateLimiter>(relaxed = true)
    private val mockSwapService = mockk<SwapService>(relaxed = true)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        rateLimiterIsLimited = false
        unmockkObject(Redis)
    }

    @Test
    fun `swap quote returns 401 without auth`() = runTest {
        testApplication {
            application {
                installAuthAndRoutes()
            }

            val client = createClient { }
            val resp = client.post("/v1/swap/quote") {
                contentType(ContentType.Application.Json)
                setBody("""{"tokenIn":"0xabc","amountIn":"1000000000000000000"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, resp.status, "Swap quote without auth should return 401")
        }
    }

    @Test
    fun `swap execute returns 401 without auth`() = runTest {
        testApplication {
            application {
                installAuthAndRoutes()
            }

            val client = createClient { }
            val resp = client.post("/v1/swap/execute") {
                contentType(ContentType.Application.Json)
                setBody("""{"tokenIn":"0xabc","amountIn":"1000000000000000000","minAmountOut":"0"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, resp.status, "Swap execute without auth should return 401")
        }
    }

    @Test
    fun `swap quote returns 401 with invalid Bearer token`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.incr(any<String>()) } returns 1L
        coEvery { Redis.expire(any<String>(), any()) } returns true

        testApplication {
            application {
                installAuthAndRoutes()
            }

            val client = createClient { }
            val resp = client.post("/v1/swap/quote") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer invalid-token")
                setBody("""{"tokenIn":"0xabc","amountIn":"1000000000000000000"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, resp.status, "Swap quote with invalid token should return 401")
        }
    }

    @Test
    fun `swap quote returns 429 after rate limit exceeded`() = runTest {
        rateLimiterIsLimited = true

        testApplication {
            application {
                installAuthAndRoutes()
            }

            val client = createClient { }
            val resp = client.post("/v1/swap/quote") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer valid-token")
                setBody("""{"tokenIn":"0xabc","amountIn":"1000000000000000000"}""")
            }
            assertEquals(HttpStatusCode.TooManyRequests, resp.status, "Swap quote over rate limit should return 429")
        }
    }

    @Test
    fun `swap execute uses authenticated wallet as recipient`() = runTest {
        mockkObject(Redis)
        coEvery { Redis.incr(any<String>()) } returns 1L
        coEvery { Redis.expire(any<String>(), any()) } returns true

        val receipt = mockk<TransactionReceipt>(relaxed = true)
        every { receipt.transactionHash } returns "0xhash"
        every { receipt.blockNumber } returns BigInteger.ONE
        every { receipt.isStatusOK } returns true
        coEvery { mockSwapService.executeSwap(any(), "0xprincipal-wallet") } returns Result.success(receipt)

        testApplication {
            application {
                installAuthAndRoutes()
            }

            val client = createClient { }
            val resp = client.post("/v1/swap/execute") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer valid-token")
                // recipient in the body must be ignored — swap goes to the principal's wallet
                setBody("""{"tokenIn":"0xabc","amountIn":"1000000000000000000","minAmountOut":"0","recipient":"0xattacker-wallet"}""")
            }
            assertEquals(HttpStatusCode.OK, resp.status, "Swap execute with valid auth should succeed")
            coVerify { mockSwapService.executeSwap(any(), "0xprincipal-wallet") }
        }
    }

    // ── Test application setup ──

    private fun Application.installAuthAndRoutes() {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

        // Mock auth — accept any Bearer token as valid for test
        install(Authentication) {
            bearer("auth-jwt") {
                realm = "MDAOPay"
                authenticate { tokenCredential ->
                    if (tokenCredential.token == "valid-token") {
                        WalletPrincipal("test-user", "0xprincipal-wallet")
                    } else null
                }
            }
        }

        coEvery { mockRateLimiter.isLimited(any(), any(), any()) } returns rateLimiterIsLimited

        routing {
            route("/v1") {
                authenticate("auth-jwt") {
                    swapRoutes(mockSwapService, mockRateLimiter)
                }
            }
        }
    }
}
