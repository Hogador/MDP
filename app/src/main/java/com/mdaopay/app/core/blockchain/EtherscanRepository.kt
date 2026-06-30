package com.mdaopay.app.core.blockchain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class EtherscanApiResponse(
    val status: String,
    val message: String,
    val result: List<EtherscanTx> = emptyList()
)

@Serializable
data class EtherscanTx(
    val hash: String,
    @SerialName("timeStamp") val timeStamp: String,
    val from: String,
    val to: String,
    val value: String = "0",
    @SerialName("contractAddress") val contractAddress: String = "",
    @SerialName("tokenSymbol") val tokenSymbol: String = "",
    @SerialName("tokenDecimal") val tokenDecimal: String = "0",
    @SerialName("isError") val isError: String = "0",
    @SerialName("txreceipt_status") val txReceiptStatus: String = ""
)

data class RemoteTx(
    val txHash: String,
    val amount: BigDecimal,
    val tokenSymbol: String,
    val timestamp: Long,
    val from: String,
    val to: String,
    val isError: Boolean,
    val isConfirmed: Boolean
)

@Singleton
class EtherscanRepository @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchTransactions(address: String): List<RemoteTx> {
        if (NetworkConfig.ETHERSCAN_API_KEY.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            val ethTxs = fetchTxList(address)
            val tokenTxs = fetchTokenTxList(address)
            (ethTxs + tokenTxs).sortedByDescending { it.timestamp }
        }
    }

    private fun fetchTxList(address: String): List<RemoteTx> {
        return try {
            val url = buildUrl("account", "txlist", address)
            val body = client.newCall(Request.Builder().url(url).get().build()).execute()
                .body?.string() ?: return emptyList()
            val response = json.decodeFromString<EtherscanApiResponse>(body)
            if (response.status != "1") return emptyList()
            response.result.map { it.toRemoteTx(address, "ETH") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchTokenTxList(address: String): List<RemoteTx> {
        return try {
            val url = buildUrl("account", "tokentx", address)
            val body = client.newCall(Request.Builder().url(url).get().build()).execute()
                .body?.string() ?: return emptyList()
            val response = json.decodeFromString<EtherscanApiResponse>(body)
            if (response.status != "1") return emptyList()
            response.result.map { it.toRemoteTx(address, it.tokenSymbol.ifBlank { "TOKEN" }) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildUrl(module: String, action: String, address: String): String {
        val base = NetworkConfig.ETHERSCAN_API_URL
        val key = NetworkConfig.ETHERSCAN_API_KEY
        return "$base?module=$module&action=$action&address=$address&sort=desc&offset=20&apikey=$key"
    }

    private fun EtherscanTx.toRemoteTx(userAddress: String, symbol: String): RemoteTx {
        val decimals = tokenDecimal.toIntOrNull() ?: 18
        val rawAmount = value.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
        val isIncoming = to.equals(userAddress, ignoreCase = true)
        val amount = BigDecimal(rawAmount, decimals)

        return RemoteTx(
            txHash = hash,
            amount = amount,
            tokenSymbol = symbol,
            timestamp = timeStamp.toLongOrNull() ?: 0L,
            from = from,
            to = to,
            isError = isError != "0",
            isConfirmed = txReceiptStatus == "1"
        )
    }
}
