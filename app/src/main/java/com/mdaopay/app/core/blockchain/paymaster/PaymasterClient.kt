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
    // H-09: ERC-2612 permit fields — backend uses permitV != null to build permit-based paymasterAndData
    val permitDeadline: String? = null,
    val permitV: String? = null,
    val permitR: String? = null,
    val permitS: String? = null,
)

// F-130: SignResponse matches backend PaymasterService.SignResponse exactly
data class SignResponse(
    val paymasterAndData: String,
    val userOpHash: String,
    val maxFee: String,
    val token: String,
)

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
        permitDeadline: BigInteger? = null,
        permitV: Int? = null,
        permitR: ByteArray? = null,
        permitS: ByteArray? = null,
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
            permitDeadline = permitDeadline?.let { Numeric.toHexStringWithPrefix(it) },
            permitV = permitV?.let { Numeric.toHexStringWithPrefix(BigInteger.valueOf(it.toLong())) },
            permitR = permitR?.let { Numeric.toHexString(it) },
            permitS = permitS?.let { Numeric.toHexString(it) },
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
            req.permitDeadline?.let { put("permitDeadline", it) }
            req.permitV?.let { put("permitV", it) }
            req.permitR?.let { put("permitR", it) }
            req.permitS?.let { put("permitS", it) }
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

    // ponytail: dead code candidate, kept — used only by PaymasterClientTest; prod uses signUserOp()
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
