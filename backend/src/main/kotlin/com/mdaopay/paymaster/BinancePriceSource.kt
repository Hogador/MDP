package com.mdaopay.paymaster

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Price source using Binance public API (no API key required).
 * Provides BNB/USD price via the [BNBUSDT] ticker (1 USDT ≈ 1 USD).
 * MDAO is not listed on Binance — returns 0.0 for that pair.
 */
class BinancePriceSource : PriceSource {
    override val name = "binance"
    override val reliability = 0.85
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getPrices(tokenAddresses: Map<String, String>): DexPrices? {
        return try {
            val resp = client.get("https://api.binance.com/api/v3/ticker/price?symbol=BNBUSDT")
            val body = resp.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val bnbUsdtPrice = root["price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            // BNBUSDT = BNB price in USDT; assume 1 USDT ≈ 1 USD
            // MDAO not listed on Binance, so 0.0 (other sources fill it)
            DexPrices(bnbUsdtPrice, 0.0, 1.0)
        } catch (_: Exception) { null }
    }

    fun close() { client.close() }
}
