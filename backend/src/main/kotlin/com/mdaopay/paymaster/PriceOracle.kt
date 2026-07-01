package com.mdaopay.paymaster

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

@Serializable
data class DexPrices(
    val bnbUsd: Double,
    val mdaoUsd: Double,
    val usdtUsd: Double,
) {
    fun isValid(): Boolean =
        bnbUsd in 100.0..10000.0 &&
        usdtUsd in 0.9..1.1 &&
        mdaoUsd in 0.0001..100.0

    companion object {
        private const val FALLBACK_BNB_USD = 600.0
        private const val FALLBACK_MDAO_USD = 0.001
        private const val FALLBACK_USDT_USD = 1.0

        fun fallbackPrices(): DexPrices =
            DexPrices(FALLBACK_BNB_USD, FALLBACK_MDAO_USD, FALLBACK_USDT_USD)

        fun saneBnb(v: Double) = v in 100.0..10000.0
        fun saneUsdt(v: Double) = v in 0.9..1.1
        fun saneMdao(v: Double) = v in 0.0001..100.0
    }
}

@Serializable
data class CachedDexPrices(
    val prices: DexPrices,
    val updatedAt: Long,
)

interface PriceSource {
    val name: String
    val reliability: Double
    suspend fun getPrices(tokenAddresses: Map<String, String>): DexPrices?
}

class DexScreenerSource(
    private val wbnbAddress: String,
    private val usdtAddress: String,
    private val mdaoAddress: String,
) : PriceSource {
    override val name = "dexScreener"
    override val reliability = 1.0
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getPrices(tokenAddresses: Map<String, String>): DexPrices? {
        return try {
            coroutineScope {
                val bnb = async { fetchPrice(wbnbAddress) }
                val usdt = async { fetchPrice(usdtAddress) }
                val mdao = async { fetchPrice(mdaoAddress) }
                DexPrices(bnb.await(), mdao.await(), usdt.await())
            }
        } catch (_: Exception) { null }
    }

    private suspend fun fetchPrice(tokenAddress: String): Double {
        val resp = client.get("https://api.dexscreener.com/latest/dex/tokens/$tokenAddress")
        val body = resp.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        val pairs = root["pairs"]?.jsonArray ?: return 0.0
        for (pair in pairs) {
            val obj = pair.jsonObject
            val price = obj["priceUsd"]?.jsonPrimitive?.content?.toDoubleOrNull()
            if (price != null && price > 0.0) return price
        }
        return 0.0
    }

    fun close() { client.close() }
}

class CoinGeckoSource(
    private val apiKey: String? = null,
) : PriceSource {
    override val name = "coinGecko"
    override val reliability = 0.9
    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getPrices(tokenAddresses: Map<String, String>): DexPrices? {
        return try {
            val baseUrl = "https://api.coingecko.com/api/v3/simple/price?ids=binancecoin,tether&vs_currencies=usd"
            val url = if (apiKey != null) "$baseUrl&x_cg_pro_api_key=$apiKey" else baseUrl
            val resp = client.get(url)
            val body = resp.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val bnbObj = root["binancecoin"]?.jsonObject
            val usdtObj = root["tether"]?.jsonObject
            val bnb = bnbObj?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            val usdt = usdtObj?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
            DexPrices(bnb, 0.0, usdt)
        } catch (_: Exception) { null }
    }

    fun close() { client.close() }
}

class OnChainTWAPSource : PriceSource {
    override val name = "onChainTWAP"
    override val reliability = 0.95
    override suspend fun getPrices(tokenAddresses: Map<String, String>): DexPrices? {
        throw NotImplementedError("TWAP oracle not yet implemented")
    }
}

class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 60000,
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }
    private val state = AtomicReference(State.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val log = LoggerFactory.getLogger(CircuitBreaker::class.java)

    suspend fun <T> execute(block: suspend () -> T): T {
        when (state.get()) {
            State.OPEN -> {
                if (System.currentTimeMillis() - lastFailureTime.get() > resetTimeoutMs) {
                    state.compareAndSet(State.OPEN, State.HALF_OPEN)
                } else {
                    throw CircuitBreakerOpenException("Circuit breaker is OPEN")
                }
            }
            State.HALF_OPEN -> {
                return try {
                    val result = block()
                    state.set(State.CLOSED)
                    failureCount.set(0)
                    result
                } catch (e: Exception) {
                    state.set(State.OPEN)
                    lastFailureTime.set(System.currentTimeMillis())
                    throw e
                }
            }
            State.CLOSED -> { }
        }

        return try {
            val result = block()
            failureCount.set(0)
            result
        } catch (e: Exception) {
            val count = failureCount.incrementAndGet()
            lastFailureTime.set(System.currentTimeMillis())
            if (count >= failureThreshold) {
                state.set(State.OPEN)
                log.warn("Circuit breaker opened after $count failures")
            }
            throw e
        }
    }
}

class CircuitBreakerOpenException(message: String) : Exception(message)

class PriceOracle(
    private val sources: List<PriceSource>,
    private val circuitBreaker: CircuitBreaker = CircuitBreaker(),
    private val isTestnet: Boolean = false,
) {
    private val log = LoggerFactory.getLogger(PriceOracle::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val FRESH_TTL_SEC = 30L
        private const val STALE_TTL_SEC = 120L
        private const val CACHE_KEY = "dex:prices"
    }

    suspend fun getPrices(): DexPrices {
        val now = System.currentTimeMillis()
        val cached = readCache()

        if (cached != null) {
            val age = now - cached.updatedAt
            if (age < FRESH_TTL_SEC * 1000L) {
                return cached.prices
            }
            val fresh = try { fetchFromSources() } catch (_: Exception) {
                if (age < STALE_TTL_SEC * 1000L) return cached.prices
                throw PriceOracleException("Price fetch failed and cache expired")
            }
            writeCache(fresh)
            return fresh
        }

        try {
            val prices = fetchFromSources()
            writeCache(prices)
            return prices
        } catch (e: Exception) {
            throw PriceOracleException("Price fetch failed", e)
        }
    }

    private suspend fun fetchFromSources(): DexPrices {
        val allResults = coroutineScope {
            sources.map { source ->
                async {
                    try {
                        circuitBreaker.execute { source.getPrices(emptyMap()) }
                    } catch (_: Exception) { null }
                }
            }.mapNotNull { it.await() }
        }

        val bnbValues = allResults.mapNotNull { it.bnbUsd.takeIf { v -> v > 0.0 && DexPrices.saneBnb(v) } }
        val usdtValues = allResults.mapNotNull { it.usdtUsd.takeIf { v -> v > 0.0 && DexPrices.saneUsdt(v) } }
        val mdaoValues = allResults.mapNotNull { it.mdaoUsd.takeIf { v -> v > 0.0 && DexPrices.saneMdao(v) } }

        if (bnbValues.isEmpty() || usdtValues.isEmpty() || mdaoValues.isEmpty()) {
            if (isTestnet) {
                log.warn("FALLBACK_PRICES used — sources returned no valid data on testnet (bnb={} usdt={} mdao={})",
                    bnbValues.isEmpty(), usdtValues.isEmpty(), mdaoValues.isEmpty())
                appMetrics.errorsTotal++
                return DexPrices.fallbackPrices()
            }
            val missing = buildList {
                if (bnbValues.isEmpty()) add("BNB")
                if (usdtValues.isEmpty()) add("USDT")
                if (mdaoValues.isEmpty()) add("MDAO")
            }
            throw PriceOracleException("No valid price from any source for: ${missing.joinToString(", ")}")
        }

        val medianBnb = median(bnbValues)
        val medianUsdt = median(usdtValues)
        val medianMdao = median(mdaoValues)

        for (result in allResults) {
            if (result.bnbUsd > 0.0 && deviation(result.bnbUsd, medianBnb) > 0.1) {
                log.warn("BNB price deviation: source={} value={} median={}", result::class.simpleName, result.bnbUsd, medianBnb)
                appMetrics.errorsTotal++
            }
            if (result.usdtUsd > 0.0 && deviation(result.usdtUsd, medianUsdt) > 0.1) {
                log.warn("USDT price deviation: source={} value={} median={}", result::class.simpleName, result.usdtUsd, medianUsdt)
                appMetrics.errorsTotal++
            }
            if (result.mdaoUsd > 0.0 && deviation(result.mdaoUsd, medianMdao) > 0.1) {
                log.warn("MDAO price deviation: source={} value={} median={}", result::class.simpleName, result.mdaoUsd, medianMdao)
                appMetrics.errorsTotal++
            }
        }

        return DexPrices(medianBnb, medianMdao, medianUsdt)
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0
        else sorted[mid]
    }

    private fun deviation(value: Double, reference: Double): Double =
        if (reference == 0.0) Double.MAX_VALUE else abs(value - reference) / reference

    private suspend fun readCache(): CachedDexPrices? {
        val raw = Redis.get(CACHE_KEY) ?: return null
        return try {
            json.decodeFromString(CachedDexPrices.serializer(), raw.decodeToString())
        } catch (_: Exception) { null }
    }

    private suspend fun writeCache(prices: DexPrices) {
        val data = CachedDexPrices(prices, System.currentTimeMillis())
        Redis.setEx(CACHE_KEY, STALE_TTL_SEC, json.encodeToString(CachedDexPrices.serializer(), data).encodeToByteArray())
    }

    // ponytail: close known source types (no interface method to avoid abstraction)
    fun close() {
        sources.forEach { source ->
            when (source) {
                is DexScreenerSource -> source.close()
                is CoinGeckoSource -> source.close()
                is BinancePriceSource -> source.close()
                else -> { /* OnChainTWAPSource has no resources */ }
            }
        }
    }
}

class PriceOracleException(message: String, cause: Throwable? = null) : Exception(message, cause)
