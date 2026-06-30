package com.mdaopay.paymaster

import org.slf4j.LoggerFactory
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class RpcProvider(
    val name: String,
    val url: String,
    val priority: Int,
)

class RpcProviderManager(private val urls: List<String>) {
    private val log = LoggerFactory.getLogger(RpcProviderManager::class.java)

    private val providers: List<RpcProvider> = urls.mapIndexed { i, url ->
        RpcProvider("provider-$i", url, i + 1)
    }

    private val currentIndex = AtomicInteger(0)
    private val web3jCache = ConcurrentHashMap<String, Web3j>()
    private val errorCounts = ConcurrentHashMap<String, Int>()
    private val cooldowns = ConcurrentHashMap<String, Long>()
    private val maxConsecutiveErrors = 3
    private val cooldownMs = 30_000L

    fun getBestProvider(): Result<Web3j> {
        for (i in 0 until providers.size) {
            val index = (currentIndex.get() + i) % providers.size
            val provider = providers[index]

            val errors = errorCounts.getOrDefault(provider.url, 0)
            val cooldownUntil = cooldowns.getOrDefault(provider.url, 0L)
            if (errors >= maxConsecutiveErrors && System.currentTimeMillis() < cooldownUntil) {
                continue
            }

            val web3j = getWeb3j(provider)
            if (web3j != null) {
                currentIndex.set(index)
                errorCounts.remove(provider.url)
                return Result.success(web3j)
            } else {
                val newErrors = errorCounts.merge(provider.url, 1, Int::plus) ?: 1
                if (newErrors >= maxConsecutiveErrors) {
                    cooldowns[provider.url] = System.currentTimeMillis() + cooldownMs
                    log.warn("RPC provider {} ({}) cooling down for {}ms after {} errors",
                        provider.name, provider.url, cooldownMs, newErrors)
                }
            }
        }
        return Result.failure(Exception("No available RPC providers"))
    }

    fun refreshHealth(): List<Pair<RpcProvider, Boolean>> {
        return providers.map { provider ->
            val healthy = try {
                val web3j = getWeb3j(provider) ?: return@map provider to false
                !web3j.ethBlockNumber().send().hasError()
            } catch (e: Exception) {
                false
            }
            provider to healthy
        }
    }

    private fun getWeb3j(provider: RpcProvider): Web3j? {
        return try {
            web3jCache.getOrPut(provider.url) {
                val httpService = HttpService(provider.url, false)
                Web3j.build(httpService)
            }
        } catch (e: Exception) {
            log.warn("RPC provider {} ({}) unavailable: {}", provider.name, provider.url, e.message)
            null
        }
    }

    fun close() {
        web3jCache.values.forEach { it.shutdown() }
        web3jCache.clear()
    }
}
