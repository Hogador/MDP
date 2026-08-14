package com.mdaopay.app.core.guardian

import com.mdaopay.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-signs relay requests (see relay/src/auth.ts).
 * X-Signature = hex(HmacSHA256(secret, "$ts.$nonce.$body")) — lowercase, 64 chars.
 * Fail-fast: empty secret = misconfigured build; relay 401s every unsigned POST.
 */
class RelayHmacInterceptor(
    private val secret: String = BuildConfig.RELAY_HMAC_SECRET
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // fail-fast at first request, not construction — unit tests build RelayClient without a secret
        require(secret.isNotBlank()) {
            "RELAY_HMAC_SECRET is not configured — relay rejects unsigned requests with 401"
        }
        val original = chain.request()
        val ts = (System.currentTimeMillis() / 1000).toString()
        val nonce = randomNonce()
        val body = bodyString(original.body)
        val signed = original.newBuilder()
            .header("X-Timestamp", ts)
            .header("X-Nonce", nonce)
            .header("X-Signature", hmacHex("$ts.$nonce.$body"))
            .build()
        return chain.proceed(signed)
    }

    // ponytail: writeTo does not consume the body — okhttp re-writes buffered bodies on retry
    private fun bodyString(body: okhttp3.RequestBody?): String {
        if (body == null) return ""
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16)
        RANDOM.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hmacHex(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val RANDOM = SecureRandom()
    }
}