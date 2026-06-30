package com.mdaopay.app.core.blockchain.erc4337

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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BundlerClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()
    private val requestId = AtomicInteger(1)

    suspend fun sendUserOperation(
        userOp: UserOperation,
        entryPoint: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val params = listOf(userOp.toMap(), entryPoint)
            val response = bundlerCall("eth_sendUserOperation", params)
            val result = response.getString("result")
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun estimateUserOperationGas(
        userOp: UserOperation,
        entryPoint: String
    ): Result<UserOperationGas> = withContext(Dispatchers.IO) {
        try {
            val params = listOf(userOp.toMap(), entryPoint)
            val response = bundlerCall("eth_estimateUserOperationGas", params)
            val result = response.getJSONObject("result")
            Result.success(
                UserOperationGas(
                    callGasLimit = hexToBigInt(result.getString("callGasLimit")),
                    verificationGasLimit = hexToBigInt(result.getString("verificationGasLimit")),
                    preVerificationGas = hexToBigInt(result.getString("preVerificationGas"))
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Throws(Exception::class)
    private fun bundlerCall(method: String, params: List<Any?>): JSONObject {
        val bodyJson = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
            put("id", requestId.getAndIncrement())
        }

        val request = Request.Builder()
            .url(NetworkConfig.BUNDLER_URL)
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()
        if (!response.isSuccessful || body == null) {
            throw BundlerException("HTTP ${response.code}: $body")
        }

        val json = JSONObject(body)
        if (json.has("error")) {
            val err = json.getJSONObject("error")
            throw BundlerException("${err.optString("code", "")} ${err.optString("message", "")}")
        }

        return json
    }

    private fun hexToBigInt(hex: String): BigInteger = Numeric.toBigInt(hex)

    data class UserOperationGas(
        val callGasLimit: BigInteger,
        val verificationGasLimit: BigInteger,
        val preVerificationGas: BigInteger
    )

    class BundlerException(message: String) : Exception(message)
}
