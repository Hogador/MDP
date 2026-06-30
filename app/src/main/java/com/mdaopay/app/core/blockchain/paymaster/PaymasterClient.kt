package com.mdaopay.app.core.blockchain.paymaster

import com.mdaopay.app.BuildConfig
import com.mdaopay.app.core.blockchain.NetworkConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

// ponytail: reuses OkHttp from NetworkModule, no new dependencies
sealed class PaymasterError(cause: Throwable? = null) : Exception(cause) {
    data object RateLimited : PaymasterError()
    data object InvalidRequest : PaymasterError()
    data object NetworkTimeout : PaymasterError()
    data object ServerError : PaymasterError()
    data class Unknown(override val cause: Throwable? = null) : PaymasterError(cause)
}

// F-130: SignRequest matches backend PaymasterService.SignRequest exactly
data class SignRequest(
    val sender: String,
    val nonce: String,
    val initCode: String,
    val callData: String,
    val verificationGasLimit: String,
    val callGasLimit: String,
    val preVerificationGas: String,
    val maxPriorityFeePerGas: String,
    val maxFeePerGas: String,
    val paymasterAndData: String = "0x",
    val signature: String = "0x",
    val mdaoMaxAmount: String? = null,
    val usdtMaxAmount: String? = null,
)

// F-130: SignResponse matches backend PaymasterService.SignResponse exactly
data class SignResponse(
    val paymasterAndData: String,
    val userOpHash: String,
    val maxFee: String,
    val token: String,
)

// ponytail: kept for backward compat with deprecated getQuote()
data class Quote(val paymasterAndData: ByteArray)

@Singleton
// ponytail: no default — Hilt provides OkHttpClient from NetworkModule
class PaymasterClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * F-130: Sign a UserOp with the backend paymaster.
     * Sends full UserOp fields as [SignRequest] to POST /v1/sign.
     * Returns [SignResponse] with signed paymasterAndData, userOpHash, maxFee, token.
     */
    suspend fun signUserOp(
        sender: String,
        nonce: BigInteger,
        initCode: ByteArray,
        callData: ByteArray,
        verificationGasLimit: BigInteger,
        callGasLimit: BigInteger,
        preVerificationGas: BigInteger,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
        mdaoMaxAmount: BigInteger? = null,
        usdtMaxAmount: BigInteger? = null,
    ): SignResponse = withContext(Dispatchers.IO) {
        val req = SignRequest(
            sender = sender,
            nonce = Numeric.toHexStringWithPrefix(nonce),
            initCode = Numeric.toHexString(initCode),
            callData = Numeric.toHexString(callData),
            verificationGasLimit = Numeric.toHexStringWithPrefix(verificationGasLimit),
            callGasLimit = Numeric.toHexStringWithPrefix(callGasLimit),
            preVerificationGas = Numeric.toHexStringWithPrefix(preVerificationGas),
            maxPriorityFeePerGas = Numeric.toHexStringWithPrefix(maxPriorityFeePerGas),
            maxFeePerGas = Numeric.toHexStringWithPrefix(maxFeePerGas),
            mdaoMaxAmount = mdaoMaxAmount?.let { Numeric.toHexStringWithPrefix(it) },
            usdtMaxAmount = usdtMaxAmount?.let { Numeric.toHexStringWithPrefix(it) },
        )

        val bodyJson = JSONObject().apply {
            put("sender", req.sender)
            put("nonce", req.nonce)
            put("initCode", req.initCode)
            put("callData", req.callData)
            put("verificationGasLimit", req.verificationGasLimit)
            put("callGasLimit", req.callGasLimit)
            put("preVerificationGas", req.preVerificationGas)
            put("maxPriorityFeePerGas", req.maxPriorityFeePerGas)
            put("maxFeePerGas", req.maxFeePerGas)
            put("paymasterAndData", req.paymasterAndData)
            put("signature", req.signature)
            req.mdaoMaxAmount?.let { put("mdaoMaxAmount", it) }
            req.usdtMaxAmount?.let { put("usdtMaxAmount", it) }
        }

        val request = Request.Builder()
            .url("${BuildConfig.BACKEND_URL}/v1/sign")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()

            when (response.code) {
                429 -> throw PaymasterError.RateLimited
                400 -> throw PaymasterError.InvalidRequest
                in 500..599 -> throw PaymasterError.ServerError
            }

            if (!response.isSuccessful || body == null) {
                throw PaymasterError.Unknown(Exception("HTTP ${response.code}: $body"))
            }

            val json = JSONObject(body)
            SignResponse(
                paymasterAndData = json.getString("paymasterAndData"),
                userOpHash = json.getString("userOpHash"),
                maxFee = json.getString("maxFee"),
                token = json.getString("token"),
            )
        } catch (e: java.net.SocketTimeoutException) {
            throw PaymasterError.NetworkTimeout
        } catch (e: java.net.UnknownHostException) {
            throw PaymasterError.NetworkTimeout
        } catch (e: PaymasterError) {
            throw e
        } catch (e: Exception) {
            throw PaymasterError.Unknown(e)
        }
    }

    /**
     * F-130: deprecated — use [signUserOp] instead.
     * Old API sent {sender, token, maxTokenAmount} which doesn't match backend SignRequest.
     * Kept for backward compatibility only.
     */
    @Deprecated(
        "Use signUserOp() which sends full UserOp fields matching backend SignRequest",
        ReplaceWith(
            "signUserOp(sender, nonce, initCode, callData, verificationGasLimit, callGasLimit, preVerificationGas, maxPriorityFeePerGas, maxFeePerGas, mdaoMaxAmount, usdtMaxAmount)"
        )
    )
    suspend fun getQuote(
        sender: String,
        token: String,
        maxTokenAmount: BigInteger
    ): Quote = withContext(Dispatchers.IO) {
        val bodyJson = JSONObject().apply {
            put("sender", sender)
            put("token", token)
            put("maxTokenAmount", Numeric.toHexStringWithPrefix(maxTokenAmount))
        }

        val request = Request.Builder()
            .url("${BuildConfig.BACKEND_URL}/v1/sign")
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()

            when (response.code) {
                429 -> throw PaymasterError.RateLimited
                400 -> throw PaymasterError.InvalidRequest
                in 500..599 -> throw PaymasterError.ServerError
            }

            if (!response.isSuccessful || body == null) {
                throw PaymasterError.Unknown(Exception("HTTP ${response.code}: $body"))
            }

            val json = JSONObject(body)
            val paymasterAndDataHex = json.getString("paymasterAndData")
            Quote(paymasterAndData = Numeric.hexStringToByteArray(paymasterAndDataHex))
        } catch (e: java.net.SocketTimeoutException) {
            throw PaymasterError.NetworkTimeout
        } catch (e: java.net.UnknownHostException) {
            throw PaymasterError.NetworkTimeout
        } catch (e: PaymasterError) {
            throw e
        } catch (e: Exception) {
            throw PaymasterError.Unknown(e)
        }
    }

    fun encodePaymasterAndData(
        token: String,
        maxTokenAmount: BigInteger,
        permitDeadline: BigInteger? = null,
        permitV: String? = null,
        permitR: String? = null,
        permitS: String? = null,
    ): ByteArray {
        val hasPermit = permitDeadline != null && permitV != null && permitR != null && permitS != null
        val paymasterAddress = Numeric.hexStringToByteArray(NetworkConfig.PAYMASTER_CONTRACT)
        val tokenBytes = Numeric.hexStringToByteArray(token)
        val maxAmountBytes = Numeric.toBytesPadded(maxTokenAmount, 32)

        val quoteDeadline = BigInteger.valueOf(System.currentTimeMillis() / 1000 + 300)
        val quoteDeadlineBytes = Numeric.toBytesPadded(quoteDeadline, 32)

        if (!hasPermit) {
            return paymasterAddress + tokenBytes + maxAmountBytes + quoteDeadlineBytes
        }

        val permitDeadlineBytes = Numeric.toBytesPadded(permitDeadline, 32)
        val vBytes = Numeric.hexStringToByteArray(permitV)
        val rBytes = Numeric.hexStringToByteArray(permitR)
        val sBytes = Numeric.hexStringToByteArray(permitS)
        val normalizedV = if (vBytes.size > 1) byteArrayOf(vBytes.last()) else vBytes

        return paymasterAddress + tokenBytes + maxAmountBytes + quoteDeadlineBytes +
            permitDeadlineBytes + normalizedV + rBytes + sBytes
    }
}
