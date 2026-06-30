package com.mdaopay.app.core.blockchain

import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.common.onSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EthereumClient @Inject constructor(
    private val rpcProviderManager: RpcProviderManager,
    private val okHttpClient: OkHttpClient
) {
    @Volatile
    private var web3j: Web3j? = null

    /** Backward-compatible sync getter with lazy init and fallback */
    fun getWeb3j(): Web3j {
        return web3j ?: synchronized(this) {
            web3j ?: initializeWithFallback()
        }
    }

    /** Async getter with fallback for new code */
    suspend fun getWeb3jAsync(): Result<Web3j> = withContext(Dispatchers.IO) {
        val result = rpcProviderManager.getBestProvider()
        result.onSuccess { provider ->
            web3j = provider
        }
        result
    }

    /** Refresh to next available provider */
    suspend fun refreshProvider(): Result<Web3j> = withContext(Dispatchers.IO) {
        val result = rpcProviderManager.getBestProvider()
        result.onSuccess { provider ->
            web3j = provider
        }
        result
    }

    /** Initialize with fallback - tries each provider until one works */
    private fun initializeWithFallback(): Web3j {
        val providers = rpcProviderManager.getAllProviders()
        for (provider in providers) {
            val candidate = getWeb3j(provider)
            if (candidate != null) {
                rpcProviderManager.setCurrentIndex(providers.indexOf(provider))
                web3j = candidate
                return candidate
            }
        }
        // Last resort: try the hardcoded URL
        val httpService = HttpService(
            NetworkConfig.RPC_URL,
            okHttpClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        )
        return Web3j.build(httpService).also { web3j = it }
    }

    private fun getWeb3j(provider: RpcProviderManager.RpcProvider): Web3j? {
        return try {
            val httpService = HttpService(
                provider.url,
                okHttpClient.newBuilder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build()
            )
            Web3j.build(httpService)
        } catch (e: Exception) {
            null
        }
    }
}
