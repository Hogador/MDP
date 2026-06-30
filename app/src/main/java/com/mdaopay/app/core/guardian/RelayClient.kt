package com.mdaopay.app.core.guardian

import com.mdaopay.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

// ponytail: generic ApiResponse unwrapper — single generic class for all endpoints
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)

@Singleton
class RelayClient @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        internal const val MAX_ATTEMPTS = 3
        internal const val CIRCUIT_THRESHOLD = 5
        internal const val CIRCUIT_RESET_MS = 30_000L
        internal val failureCount = AtomicInteger(0)
        @Volatile
        internal var circuitOpenUntil = 0L
    }

    // ponytail: simple circuit breaker — global lock, per-endpoint if throughput matters
    internal fun isCircuitOpen(): Boolean {
        val until = circuitOpenUntil
        if (until == 0L) return false
        if (System.currentTimeMillis() > until) {
            circuitOpenUntil = 0L
            return false
        }
        return true
    }

    internal fun recordFailure() {
        if (failureCount.incrementAndGet() >= CIRCUIT_THRESHOLD) {
            circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_RESET_MS
        }
    }

    internal fun recordSuccess() {
        failureCount.set(0)
        circuitOpenUntil = 0L
    }

    // ponytail: retries only 5xx and timeout, never 4xx
    private suspend fun executeWithRetry(request: Request): okhttp3.Response {
        if (isCircuitOpen()) {
            throw Exception("Relay circuit breaker open")
        }

        var lastError: Exception? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (attempt > 0) {
                delay((1000L * (1L shl (attempt - 1))).coerceAtMost(8000L))
            }
            try {
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val code = response.code

                if (code in 400..499) {
                    // ponytail: 4xx never retried — client error
                    return response
                }

                if (!response.isSuccessful) {
                    // 5xx — retry
                    val msg = "Relay error $code: ${response.body?.string()}"
                    response.close()
                    lastError = Exception(msg)
                    continue
                }

                recordSuccess()
                return response
            } catch (e: Exception) {
                // Timeout, IO error — retry
                lastError = e
            }
        }

        recordFailure()
        throw lastError ?: Exception("Relay retry exhausted")
    }

    suspend fun createInvite(request: CreateInviteRequest): Result<GuardianInvite> {
        return post("/guardian/invite", json.encodeToString(request))
    }

    suspend fun getInvite(inviteId: String): Result<GuardianInviteResponse> {
        return get("/guardian/invite/$inviteId")
    }

    // ponytail: relay requires signature over invite acceptance + guardian identity
    suspend fun acceptInvite(inviteId: String, signatureR: String, signatureS: String, guardianIdentityHash: String): Result<Unit> {
        val body = json.encodeToString(mapOf(
            "signatureR" to signatureR,
            "signatureS" to signatureS,
            "guardianIdentityHash" to guardianIdentityHash
        ))
        return post("/guardian/invite/$inviteId/accept", body)
    }

    suspend fun getPendingRecoveries(walletAddress: String): Result<List<PendingRecovery>> {
        return get("/recovery/pending/$walletAddress")
    }

    suspend fun submitApproval(approval: RecoveryApproval): Result<Unit> {
        return post("/recovery/approve", json.encodeToString(approval))
    }

    suspend fun submitVeto(walletAddress: String, guardianIdentityHash: String, signatureR: String, signatureS: String, nonce: Long): Result<Unit> {
        val body = json.encodeToString(mapOf(
            "walletAddress" to walletAddress,
            "guardianIdentityHash" to guardianIdentityHash,
            "signatureR" to signatureR,
            "signatureS" to signatureS,
            "nonce" to nonce
        ))
        return post("/recovery/veto", body)
    }

    suspend fun registerPushToken(walletAddress: String, fcmToken: String): Result<Unit> {
        return post("/push/register", json.encodeToString(
            mapOf("walletAddress" to walletAddress, "fcmToken" to fcmToken)
        ))
    }

    suspend fun notifyRecoveryInitiated(walletAddress: String): Result<Unit> {
        return post("/recovery/notify", json.encodeToString(
            mapOf("walletAddress" to walletAddress)
        ))
    }

    private suspend inline fun <reified T> get(path: String): Result<T> {
        return try {
            val request = Request.Builder()
                .url("${BuildConfig.RELAY_URL}$path")
                .get()
                .build()
            val response = executeWithRetry(request)
            if (!response.isSuccessful) {
                return Result.failure(Exception("Relay error ${response.code}: ${response.body?.string()}"))
            }
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val apiResponse = json.decodeFromString<ApiResponse<T>>(body)
            if (!apiResponse.success) {
                return Result.failure(Exception(apiResponse.error ?: "Relay error"))
            }
            @Suppress("UNCHECKED_CAST")
            if (apiResponse.data == null && T::class == Unit::class) {
                return Result.success(Unit as T)
            }
            Result.success(apiResponse.data ?: error("No data in response"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend inline fun <reified T> post(path: String, body: String): Result<T> {
        return try {
            val request = Request.Builder()
                .url("${BuildConfig.RELAY_URL}$path")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            val response = executeWithRetry(request)
            if (!response.isSuccessful) {
                return Result.failure(Exception("Relay error ${response.code}: ${response.body?.string()}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                @Suppress("UNCHECKED_CAST")
                return Result.success(true as T)
            }
            val apiResponse = json.decodeFromString<ApiResponse<T>>(responseBody)
            if (!apiResponse.success) {
                return Result.failure(Exception(apiResponse.error ?: "Relay error"))
            }
            @Suppress("UNCHECKED_CAST")
            if (apiResponse.data == null && T::class == Unit::class) {
                return Result.success(Unit as T)
            }
            Result.success(apiResponse.data ?: error("No data in response"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
