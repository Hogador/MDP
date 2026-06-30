package com.mdaopay.paymaster

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import com.mdaopay.paymaster.util.LogSanitizer
import org.web3j.abi.*
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.*
import org.web3j.crypto.*
import org.web3j.protocol.Web3j
import org.web3j.tx.ChainIdLong
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.utils.Numeric
import kotlinx.coroutines.future.await
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

private val swapLog = LoggerFactory.getLogger("SwapService")

@Serializable
data class SwapQuoteRequest(
    val tokenIn: String,
    val tokenOut: String = "0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c",
    val amountIn: String,
    val slippageBps: Int = 50,
)

@Serializable
data class SwapQuoteResponse(
    val tokenIn: String,
    val tokenOut: String,
    val amountIn: String,
    val amountOut: String,
    val priceImpactBps: Int,
    val route: List<String>,
)

@Serializable
data class SwapExecuteRequest(
    val tokenIn: String,
    val tokenOut: String = "0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c",
    val amountIn: String,
    val minAmountOut: String,
    val recipient: String,
    val deadlineSec: Long = 600,
)

class SwapService(
    private val web3j: Web3j,
    private val routerAddress: String,
    private val signer: ECKeyPair,
) {
    private val credentials = Credentials.create(signer)
    private val routerAbi = """
        [{"constant":true,"inputs":[{"name":"amountOut","type":"uint256"},{"name":"path","type":"address[]"}],"name":"getAmountsIn","outputs":[{"name":"amounts","type":"uint256[]"}],"type":"function"},
         {"constant":true,"inputs":[{"name":"amountIn","type":"uint256"},{"name":"path","type":"address[]"}],"name":"getAmountsOut","outputs":[{"name":"amounts","type":"uint256[]"}],"type":"function"},
         {"constant":false,"inputs":[{"name":"amountOutMin","type":"uint256"},{"name":"path","type":"address[]"},{"name":"to","type":"address"},{"name":"deadline","type":"uint256"}],"name":"swapExactTokensForETH","outputs":[],"type":"function"},
         {"constant":false,"inputs":[{"name":"amountOutMin","type":"uint256"},{"name":"path","type":"address[]"},{"name":"to","type":"address"},{"name":"deadline","type":"uint256"}],"name":"swapExactTokensForTokens","outputs":[],"type":"function"}]
    """.trimIndent()

    suspend fun getQuote(request: SwapQuoteRequest): Result<SwapQuoteResponse> {
        return try {
            val amountIn = Numeric.toBigInt(request.amountIn)
            val path = listOf(request.tokenIn, request.tokenOut)
            val amounts = callGetAmountsOut(amountIn, path)
            if (amounts.size < 2) return Result.failure(Exception("Swap path not supported"))

            val amountOut = amounts[1]
            val priceImpact = calculatePriceImpact(amountIn, amountOut)

            Result.success(SwapQuoteResponse(
                tokenIn = request.tokenIn,
                tokenOut = request.tokenOut,
                amountIn = amountIn.toString(),
                amountOut = amountOut.toString(),
                priceImpactBps = priceImpact,
                route = path,
            ))
        } catch (e: Exception) {
            swapLog.warn("Quote failed reason={}", LogSanitizer.sanitizeError(e))
            if (swapLog.isDebugEnabled) swapLog.debug("Quote failed details", e)
            Result.failure(e)
        }
    }

    suspend fun executeSwap(request: SwapExecuteRequest): Result<TransactionReceipt> {
        return try {
            val path = listOf(request.tokenIn, request.tokenOut)
            val amountIn = Numeric.toBigInt(request.amountIn)
            val minAmountOut = Numeric.toBigInt(request.minAmountOut)
            val deadline = BigInteger.valueOf(System.currentTimeMillis() / 1000 + request.deadlineSec)

            swapLog.info("Swapping ${amountIn} wei of $request.tokenIn → $request.tokenOut, min=$minAmountOut")

            val function = Function(
                if (request.tokenOut == "0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c")
                    "swapExactTokensForETH" else "swapExactTokensForTokens",
                listOf(
                    Uint256(amountIn),
                    DynamicArray(Address::class.java, path.map { Address(it) }),
                    Address(request.recipient),
                    Uint256(deadline),
                ),
                emptyList(),
            )

            val data = FunctionEncoder.encode(function)
            val nonce = web3j.ethGetTransactionCount(credentials.address, DefaultBlockParameterName.LATEST)
                .sendAsync().await().transactionCount
            val gasPrice = web3j.ethGasPrice().sendAsync().await().gasPrice
            val chainId = web3j.ethChainId().sendAsync().await().chainId.toLong()

            val rawTx = RawTransaction.createTransaction(
                nonce, gasPrice, BigInteger.valueOf(500_000),
                routerAddress, BigInteger.ZERO, data,
            )
            val signedTx = TransactionEncoder.signMessage(rawTx, chainId, credentials)
            val hexTx = Numeric.toHexString(signedTx)

            val txHash = web3j.ethSendRawTransaction(hexTx).sendAsync().await().transactionHash
            swapLog.info("Swap tx submitted hash={}", LogSanitizer.sanitizeHash(txHash))
            if (swapLog.isDebugEnabled) swapLog.debug("Swap tx submitted fullHash={}", txHash)

            val receipt = waitForReceipt(txHash)
            Result.success(receipt)
        } catch (e: Exception) {
            swapLog.warn("Swap execution failed reason={}", LogSanitizer.sanitizeError(e))
            if (swapLog.isDebugEnabled) swapLog.debug("Swap execution failed details", e)
            Result.failure(e)
        }
    }

    private suspend fun callGetAmountsOut(amountIn: BigInteger, path: List<String>): List<BigInteger> {
        val function = Function(
            "getAmountsOut",
            listOf(Uint256(amountIn), DynamicArray(Address::class.java, path.map { Address(it) })),
            listOf(TypeReference.create(DynamicArray::class.java) as TypeReference<Type<*>>),
        )
        val data = FunctionEncoder.encode(function)
        val call = Transaction.createEthCallTransaction(null, routerAddress, data)
        val response = web3j.ethCall(call, DefaultBlockParameterName.LATEST).sendAsync().await()

        return if (response.hasError()) {
            emptyList()
        } else {
            val result = FunctionReturnDecoder.decode(response.value, function.outputParameters)
            if (result.isNotEmpty()) {
                (result[0] as DynamicArray<Uint256>).value.map { it.value }
            } else emptyList()
        }
    }

    private fun calculatePriceImpact(amountIn: BigInteger, amountOut: BigInteger): Int {
        if (amountIn == BigInteger.ZERO) return 0
        val inDecimal = BigDecimal(amountIn)
        val outDecimal = BigDecimal(amountOut)
        val ratio = outDecimal.divide(inDecimal, 18, RoundingMode.HALF_UP)
        val impact = BigDecimal.ONE.subtract(ratio).multiply(BigDecimal.TEN.pow(4))
        return impact.setScale(0, RoundingMode.HALF_UP).toInt().coerceAtLeast(0)
    }

    private suspend fun waitForReceipt(txHash: String): TransactionReceipt {
        var attempts = 0
        while (attempts < 30) {
            val receipt = web3j.ethGetTransactionReceipt(txHash).sendAsync().await().transactionReceipt.orElse(null)
            if (receipt != null) return receipt
            kotlinx.coroutines.delay(2000)
            attempts++
        }
        throw Exception("Transaction not confirmed after 60s: $txHash")
    }
}
