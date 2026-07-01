package com.mdaopay.paymaster

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PriceOracleTest {

    @Test
    fun `DexPrices isValid returns true for valid prices`() {
        val prices = DexPrices(bnbUsd = 600.0, mdaoUsd = 0.001, usdtUsd = 1.0)
        assertTrue(prices.isValid())
    }

    @Test
    fun `DexPrices isValid rejects BNB below 100`() {
        val prices = DexPrices(bnbUsd = 50.0, mdaoUsd = 0.001, usdtUsd = 1.0)
        assertEquals(false, prices.isValid())
    }

    @Test
    fun `DexPrices isValid rejects BNB above 10000`() {
        val prices = DexPrices(bnbUsd = 20000.0, mdaoUsd = 0.001, usdtUsd = 1.0)
        assertEquals(false, prices.isValid())
    }

    @Test
    fun `DexPrices isValid rejects USDT below 0_9`() {
        val prices = DexPrices(bnbUsd = 600.0, mdaoUsd = 0.001, usdtUsd = 0.5)
        assertEquals(false, prices.isValid())
    }

    @Test
    fun `DexPrices isValid rejects USDT above 1_1`() {
        val prices = DexPrices(bnbUsd = 600.0, mdaoUsd = 0.001, usdtUsd = 2.0)
        assertEquals(false, prices.isValid())
    }

    @Test
    fun `DexPrices isValid rejects MDAO below 0_0001`() {
        val prices = DexPrices(bnbUsd = 600.0, mdaoUsd = 0.00001, usdtUsd = 1.0)
        assertEquals(false, prices.isValid())
    }

    @Test
    fun `DexPrices isValid rejects MDAO above 100`() {
        val prices = DexPrices(bnbUsd = 600.0, mdaoUsd = 200.0, usdtUsd = 1.0)
        assertEquals(false, prices.isValid())
    }

    @Test
    fun `CircuitBreaker starts CLOSED and opens after threshold failures`() {
        val cb = CircuitBreaker(failureThreshold = 2, resetTimeoutMs = 60000)
        val failingBlock: suspend () -> String = { throw RuntimeException("fail") }

        val ex1 = assertFailsWith<RuntimeException> {
            runBlocking { cb.execute(failingBlock) }
        }
        assertEquals("fail", ex1.message)

        val ex2 = assertFailsWith<RuntimeException> {
            runBlocking { cb.execute(failingBlock) }
        }
        assertEquals("fail", ex2.message)

        val ex3 = assertFailsWith<CircuitBreakerOpenException> {
            runBlocking { cb.execute(failingBlock) }
        }
        assertEquals("Circuit breaker is OPEN", ex3.message)
    }

    @Test
    fun `CircuitBreaker CLOSED allows successful executions`() {
        val cb = CircuitBreaker(failureThreshold = 2, resetTimeoutMs = 60000)
        val result = runBlocking {
            cb.execute<Int> { 42 }
        }
        assertEquals(42, result)
    }

    @Test
    fun `CircuitBreaker successful execution resets failure count`() {
        val cb = CircuitBreaker(failureThreshold = 2, resetTimeoutMs = 60000)
        val failing: suspend () -> String = { throw RuntimeException("fail") }
        val succeeding: suspend () -> String = { "ok" }

        runBlocking {
            assertFailsWith<RuntimeException> { cb.execute(failing) }
            val result = cb.execute(succeeding)
            assertEquals("ok", result)
        }
    }

    @Test
    fun `median with odd number of values returns middle value`() {
        val oracle = PriceOracle(emptyList())
        val method = oracle::class.java.getDeclaredMethod("median", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(oracle, listOf(1.0, 3.0, 2.0)) as Double
        assertEquals(2.0, result, 0.001)
    }

    @Test
    fun `median with even number of values returns average of middle two`() {
        val oracle = PriceOracle(emptyList())
        val method = oracle::class.java.getDeclaredMethod("median", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(oracle, listOf(1.0, 4.0, 2.0, 3.0)) as Double
        assertEquals(2.5, result, 0.001)
    }

    @Test
    fun `median with single value returns that value`() {
        val oracle = PriceOracle(emptyList())
        val method = oracle::class.java.getDeclaredMethod("median", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(oracle, listOf(42.0)) as Double
        assertEquals(42.0, result, 0.001)
    }

    @Test
    fun `median with empty list returns zero`() {
        val oracle = PriceOracle(emptyList())
        val method = oracle::class.java.getDeclaredMethod("median", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(oracle, emptyList<Double>()) as Double
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `DexPrices fallbackPrices returns expected values`() {
        val prices = DexPrices.fallbackPrices()
        assertEquals(600.0, prices.bnbUsd)
        assertEquals(0.001, prices.mdaoUsd)
        assertEquals(1.0, prices.usdtUsd)
    }

    @Test
    fun `BinancePriceSource has correct name and reliability`() {
        val source = BinancePriceSource()
        assertEquals("binance", source.name)
        assertEquals(0.85, source.reliability)
    }

    @Test
    fun `getPrices returns fallback on testnet when all sources fail`() = runBlocking {
        val oracle = PriceOracle(listOf(FailingPriceSource()), isTestnet = true)
        val result = oracle.getPrices()
        assertEquals(DexPrices.fallbackPrices().bnbUsd, result.bnbUsd, 0.001)
        assertEquals(DexPrices.fallbackPrices().mdaoUsd, result.mdaoUsd, 0.001)
        assertEquals(DexPrices.fallbackPrices().usdtUsd, result.usdtUsd, 0.001)
    }

    @Test
    fun `getPrices throws on mainnet when all sources fail`() = runBlocking {
        val oracle = PriceOracle(listOf(FailingPriceSource()), isTestnet = false)
        assertFailsWith<PriceOracleException> {
            oracle.getPrices()
        }
    }
}

/** Source that always returns null to simulate total failure. */
class FailingPriceSource : PriceSource {
    override val name = "failing"
    override val reliability = 0.0
    override suspend fun getPrices(tokenAddresses: Map<String, String>): DexPrices? = null
}
