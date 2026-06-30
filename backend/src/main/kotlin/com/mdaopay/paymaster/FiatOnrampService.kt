package com.mdaopay.paymaster

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import com.mdaopay.paymaster.util.LogSanitizer
import java.math.BigDecimal
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.*

private val onrampLog = LoggerFactory.getLogger("FiatOnramp")

interface OnrampProvider {
    suspend fun createOrder(request: OnrampOrderRequest): OnrampOrderResult
    suspend fun getOrderStatus(providerRef: String): OnrampStatusResult
}

@Serializable
data class OnrampOrderRequest(
    val fiatCurrency: String = "USD",
    val cryptoCurrency: String = "BNB",
    @Contextual val fiatAmount: BigDecimal? = null,
    @Contextual val cryptoAmount: BigDecimal? = null,
    val destinationAddress: String,
    val redirectUrl: String? = null,
    @Contextual val walletId: UUID? = null,
)

data class OnrampOrderResult(
    val providerRef: String,
    val widgetUrl: String?,
    val quoteId: String?,
    val fiatAmount: BigDecimal,
    val cryptoAmount: BigDecimal,
    val fee: BigDecimal,
    val rate: BigDecimal,
)

data class OnrampStatusResult(
    val status: String,
    val providerRef: String,
    val cryptoAmount: BigDecimal? = null,
    val txHash: String? = null,
)

// ponytail: MoonPay widget URL with apiKey added via proxy endpoint.
// F-037: API key NOT exposed to client. The widget URL returned to
// the client points to our own /moonpay-proxy endpoint, which adds the
// apiKey server-side and redirects to the real MoonPay widget URL.
// The real URL is HMAC-signed with secretKey to prevent tampering.
class MoonPayProvider(
    private val apiKey: String,
    private val secretKey: String,
    private val baseUrl: String = "https://buy.moonpay.com",
    val proxyBaseUrl: String = "/moonpay-proxy",  // relative to backend
) : OnrampProvider {
    private val httpClient = HttpClient(CIO) { expectSuccess = false }

    override suspend fun createOrder(request: OnrampOrderRequest): OnrampOrderResult {
        // Build params WITHOUT apiKey — added by proxy
        val queryParams = buildMap {
            put("currencyCode", request.cryptoCurrency)
            put("walletAddress", request.destinationAddress)
            put("baseCurrencyCode", request.fiatCurrency)
            request.fiatAmount?.let { put("baseCurrencyAmount", it.toPlainString()) }
            request.redirectUrl?.let { put("redirectURL", it) }
        }
        val queryString = queryParams.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        // HMAC-SHA256 signature — secretKey never leaves server
        val signature = if (secretKey.isNotBlank()) hmacSha256(queryString, secretKey) else ""
        val sigSuffix = if (signature.isNotBlank()) "&signature=$signature" else ""
        // Return proxy URL — backend will add apiKey and redirect to real MoonPay widget
        val widgetUrl = "$proxyBaseUrl?$queryString$sigSuffix"

        return OnrampOrderResult(
            providerRef = UUID.randomUUID().toString(),
            widgetUrl = widgetUrl,
            quoteId = null,
            fiatAmount = request.fiatAmount ?: BigDecimal.ZERO,
            cryptoAmount = request.cryptoAmount ?: BigDecimal.ZERO,
            fee = BigDecimal.ZERO,
            rate = BigDecimal.ZERO,
        )
    }

    private fun hmacSha256(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    override suspend fun getOrderStatus(providerRef: String): OnrampStatusResult {
        return OnrampStatusResult(
            status = "pending",
            providerRef = providerRef,
        )
    }
}

class OnrampService(
    private val provider: OnrampProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createOrder(request: OnrampOrderRequest): Result<OnrampOrderResult> {
        return try {
            val result = provider.createOrder(request)
            Result.success(result)
        } catch (e: Exception) {
            onrampLog.warn("Onramp order failed reason={}", LogSanitizer.sanitizeError(e))
            if (onrampLog.isDebugEnabled) onrampLog.debug("Onramp order failed details", e)
            Result.failure(e)
        }
    }

    suspend fun getOrderStatus(providerRef: String): Result<OnrampStatusResult> {
        return try {
            val result = provider.getOrderStatus(providerRef)
            Result.success(result)
        } catch (e: Exception) {
            onrampLog.warn("Onramp status check failed reason={}", LogSanitizer.sanitizeError(e))
            if (onrampLog.isDebugEnabled) onrampLog.debug("Onramp status check failed details", e)
            Result.failure(e)
        }
    }
}
