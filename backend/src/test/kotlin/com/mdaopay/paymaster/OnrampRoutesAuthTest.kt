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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Regression tests for F-057:
 * Onramp routes must require JWT authentication.
 *
 * Verifies that /v1/onramp/quote and /v1/onramp/order return HTTP 401
 * when no Bearer token is provided.
 */
class OnrampRoutesAuthTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `onramp quote returns 401 without auth`() = runTest {
        testApplication {
            application {
                installAuthAndRoutes()
            }

            val client = createClient { }
            val resp = client.post("/v1/onramp/quote") {
                contentType(ContentType.Application.Json)
                setBody("""{"fiatCurrency":"USD","cryptoCurrency":"BNB","fiatAmount":100}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, resp.status, "Onramp quote without auth should return 401")
        }
    }

    @Test
    fun `onramp order returns 401 without auth`() = runTest {
        testApplication {
            application {
                installAuthAndRoutes()
            }

            val client = createClient { }
            val resp = client.post("/v1/onramp/order") {
                contentType(ContentType.Application.Json)
                setBody("""{"destinationAddress":"0xabc","fiatAmount":100}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, resp.status, "Onramp order without auth should return 401")
        }
    }

    @Test
    fun `onramp quote returns 401 with invalid Bearer token`() = runTest {
        testApplication {
            application {
                installAuthAndRoutes()
            }

            val client = createClient { }
            val resp = client.post("/v1/onramp/quote") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer invalid-token")
                setBody("""{"fiatCurrency":"USD","cryptoCurrency":"BNB","fiatAmount":100}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, resp.status, "Onramp quote with invalid token should return 401")
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
                        UserIdPrincipal("test-user")
                    } else null
                }
            }
        }

        routing {
            route("/v1") {
                authenticate("auth-jwt") {
                    val mockOnrampService = mockk<OnrampService>(relaxed = true)
                    coEvery { mockOnrampService.createOrder(any()) } returns Result.success(
                        OnrampOrderResult(
                            providerRef = "test-ref",
                            widgetUrl = "https://buy.moonpay.com?apiKey=test",
                            quoteId = "test-quote-id",
                            fiatAmount = java.math.BigDecimal("100"),
                            cryptoAmount = java.math.BigDecimal("0.01"),
                            fee = java.math.BigDecimal("1"),
                            rate = java.math.BigDecimal("10000"),
                        )
                    )
                    onrampRoutes(mockOnrampService)
                }
            }
        }
    }
}
