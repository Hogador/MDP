package com.mdaopay.app.core.blockchain

import com.mdaopay.app.BuildConfig
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

// ponytail: RPC URLs from BuildConfig — overridable per flavor via project properties
@Singleton
class RpcProviderManager @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val providers = listOf(
        RpcProvider("rpc-1", BuildConfig.RPC_URL_1, priority = 1),
        RpcProvider("rpc-2", BuildConfig.RPC_URL_2, priority = 2),
        RpcProvider("rpc-3", BuildConfig.RPC_URL_3, priority = 3),
    ).sortedBy { it.priority }

    private val currentIndex = AtomicInteger(0)

    private val web3jCache = mutableMapOf<String, Web3j>()

    data class RpcProvider(
        val name: String,
        val url: String,
        val priority: Int
    )

    fun getBestProvider(): Result<Web3j> {
        for (i in 0 until providers.size) {
            val index = (currentIndex.get() + i) % providers.size
            val provider = providers[index]
            val web3j = getWeb3j(provider)
            if (web3j != null) {
                currentIndex.set(index)
                return Result.Success(web3j)
            }
        }
        return Result.Error(AppError.Unknown(Exception("No available RPC providers")))
    }

    fun getAllProviders(): List<RpcProvider> = providers

    fun setCurrentIndex(index: Int) {
        currentIndex.set(index)
    }

    private fun getWeb3j(provider: RpcProvider): Web3j? {
        return try {
            web3jCache.getOrPut(provider.url) {
                val httpService = HttpService(
                    provider.url,
                    okHttpClient.newBuilder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .build()
                )
                Web3j.build(httpService)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun healthCheck(provider: RpcProvider): Boolean = withContext(Dispatchers.IO) {
        try {
            val web3j = getWeb3j(provider) ?: return@withContext false
            val blockNumber = web3j.ethBlockNumber().send()
            !blockNumber.hasError()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun refreshHealth(): List<Pair<RpcProvider, Boolean>> = withContext(Dispatchers.IO) {
        providers.map { provider ->
            provider to healthCheck(provider)
        }
    }
}
