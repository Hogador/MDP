package com.mdaopay.paymaster

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for F-037:
 * MoonPay API key MUST NOT appear in widget URL returned to client.
 */
class FiatOnrampServiceTest {

    @Test
    fun `widget URL does not contain apiKey`() = runTest {
        val provider = MoonPayProvider(
            apiKey = "pk_test_1234567890abcdef",
            secretKey = "sk_test_abcdef1234567890",
        )
        val request = OnrampOrderRequest(
            cryptoCurrency = "BNB",
            fiatAmount = java.math.BigDecimal("100"),
            destinationAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
        )
        val result = provider.createOrder(request)
        assertFalse(result.widgetUrl?.contains("apiKey") ?: true, "Widget URL must NOT contain apiKey parameter")
        assertTrue(result.widgetUrl?.startsWith("/moonpay-proxy") ?: false, "Widget URL must use proxy endpoint")
    }

    @Test
    fun `widget URL is HMAC signed when secretKey present`() = runTest {
        val provider = MoonPayProvider(
            apiKey = "pk_test_123",
            secretKey = "sk_test_456",
        )
        val request = OnrampOrderRequest(
            destinationAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
        )
        val result = provider.createOrder(request)
        val url = result.widgetUrl ?: ""
        assertTrue(url.contains("signature="), "Widget URL should contain HMAC signature when secretKey is set")
    }
}
