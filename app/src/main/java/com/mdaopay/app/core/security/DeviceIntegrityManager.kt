package com.mdaopay.app.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIntegrityManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class IntegrityLevel { TRUSTED, WARNING, BLOCKED }

    data class IntegrityResult(val level: IntegrityLevel, val reasons: List<String>)

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    enum class WalletOperation(val riskLevel: RiskLevel) {
        APP_OPEN(RiskLevel.LOW),
        VIEW_BALANCE(RiskLevel.MEDIUM),
        SMALL_TRANSFER(RiskLevel.MEDIUM),
        LARGE_TRANSFER(RiskLevel.HIGH),
        RECOVERY_INITIATE(RiskLevel.HIGH),
        RECOVERY_APPROVE(RiskLevel.HIGH),
        GUARDIAN_MANAGEMENT(RiskLevel.HIGH),
    }

    private val playIntegrityManager: IntegrityManager? = try {
        IntegrityManagerFactory.create(context)
    } catch (_: Exception) {
        null
    }

    suspend fun checkIntegrity(operation: WalletOperation): IntegrityResult {
        if (isRooted()) {
            return IntegrityResult(IntegrityLevel.BLOCKED, listOf("Rooted device"))
        }

        if (isEmulator()) {
            return IntegrityResult(IntegrityLevel.BLOCKED, listOf("Emulator"))
        }

        if (operation.riskLevel == RiskLevel.LOW) {
            return IntegrityResult(IntegrityLevel.TRUSTED, emptyList())
        }

        return checkPlayIntegrity(operation)
    }

    // ponytail: Software-only root detection — easily bypassed by Magisk Hide/KernelSU.
    // F-064: This is a SOFT heuristic only. Primary integrity signal must be
    // server-side Play Integrity API verification (JWT signature checked server-side).
    // DO NOT treat isRooted() == false as "device is secure".
    private fun isRooted(): Boolean {
        val suPaths = listOf(
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
        )
        for (path in suPaths) {
            if (File(path).exists()) return true
        }

        val buildTags = Build.TAGS ?: ""
        if (buildTags.contains("test-keys", ignoreCase = true)) return true

        val dangerousPackages = listOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "com.koushikdutta.superuser",
            "com.zacharee1.systemuituner",
        )
        val pm = context.packageManager
        for (pkg in dangerousPackages) {
            try {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                return true
            } catch (_: Exception) {
            }
        }

        return false
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT ?: ""
        if (fingerprint.contains("generic") || fingerprint.contains("vsemu")) return true

        val model = Build.MODEL ?: ""
        if (model.contains("google_sdk") || model.contains("Emulator") || model.contains("sdk_gphone")) return true

        val manufacturer = Build.MANUFACTURER ?: ""
        if (manufacturer.contains("Genymotion") || manufacturer.contains("unknown")) return true

        val hardware = Build.HARDWARE ?: ""
        if (hardware.contains("goldfish") || hardware.contains("ranchu") || hardware.contains("vbox")) return true

        val brand = Build.BRAND ?: ""
        if (brand.contains("generic") && (Build.DEVICE ?: "").contains("generic")) return true

        val product = Build.PRODUCT ?: ""
        if (product == "sdk" || product == "google_sdk" || product == "sdk_x86" || product == "sdk_gphone64_arm64") return true

        return false
    }

    private suspend fun checkPlayIntegrity(operation: WalletOperation): IntegrityResult {
        val manager = playIntegrityManager
        if (manager == null) {
            return if (operation.riskLevel == RiskLevel.HIGH) {
                IntegrityResult(IntegrityLevel.BLOCKED, listOf("Play Integrity API unavailable"))
            } else {
                IntegrityResult(IntegrityLevel.WARNING, listOf("Play Integrity API unavailable"))
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val nonce = SecureRandom().run {
                    val bytes = ByteArray(32)
                    nextBytes(bytes)
                    bytes.joinToString("") { "%02x".format(it) }
                }

                val request = IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .build()

                val task = manager.requestIntegrityToken(request)
                val response = Tasks.await(task, 10, TimeUnit.SECONDS)
                val token = response.token()

                val payload = verifyIntegrityToken(token, nonce)

                val deviceVerdicts = payload.deviceRecognitionVerdict ?: emptyList()
                val appVerdict = payload.appRecognitionVerdict

                when {
                    deviceVerdicts.contains("MEETS_STRONG_INTEGRITY") ->
                        IntegrityResult(IntegrityLevel.TRUSTED, emptyList())

                    deviceVerdicts.contains("MEETS_DEVICE_INTEGRITY") ->
                        IntegrityResult(IntegrityLevel.TRUSTED, emptyList())

                    deviceVerdicts.contains("MEETS_BASIC_INTEGRITY") -> {
                        if (operation.riskLevel == RiskLevel.HIGH) {
                            IntegrityResult(IntegrityLevel.WARNING, listOf("Basic integrity only"))
                        } else {
                            IntegrityResult(IntegrityLevel.TRUSTED, emptyList())
                        }
                    }

                    else -> {
                        if (operation.riskLevel == RiskLevel.HIGH) {
                            IntegrityResult(IntegrityLevel.BLOCKED, listOf("Device fails integrity checks"))
                        } else {
                            IntegrityResult(IntegrityLevel.WARNING, listOf("Device fails integrity checks"))
                        }
                    }
                }
            } catch (e: SecurityException) {
                IntegrityResult(IntegrityLevel.BLOCKED, listOf("Integrity token verification failed: ${e.localizedMessage ?: "unknown"}"))
            } catch (e: Exception) {
                if (operation.riskLevel == RiskLevel.HIGH) {
                    IntegrityResult(IntegrityLevel.BLOCKED, listOf("Play Integrity error: ${e.localizedMessage ?: "unknown"}"))
                } else {
                    IntegrityResult(IntegrityLevel.WARNING, listOf("Play Integrity error: ${e.localizedMessage ?: "unknown"}"))
                }
            }
        }
    }

    internal data class IntegrityPayload(
        val appRecognitionVerdict: String?,
        val deviceRecognitionVerdict: List<String>?,
    )

    // ── JWT verification ──────────────────────────────────────────────

    data class JwkKey(val kid: String, val n: String, val e: String)

    @Volatile
    private var _cachedJwks: List<JwkKey>? = null
    private var jwksCacheTime: Long = 0
    private val jwksCacheDurationMs = 24 * 60 * 60 * 1000L // 24h
    private val jwksUrl = "https://www.googleapis.com/android-play-integrity/v1/publicKeys"

    /** For testing: inject a known JWKS key set instead of fetching from Google. */
    internal fun setJwksCache(keys: List<JwkKey>?) {
        _cachedJwks = keys
        jwksCacheTime = if (keys != null) System.currentTimeMillis() else 0
    }

    /**
     * Verifies a Play Integrity JWT token.
     * Delegates to [verifyIntegrityToken] with [context.packageName].
     */
    private fun verifyIntegrityToken(token: String, expectedNonce: String): IntegrityPayload {
        return verifyIntegrityToken(token, expectedNonce, context.packageName)
    }

    /**
     * Verifies a Play Integrity JWT token:
     * 1. Parses header.payload.signature
     * 2. Resolves Google public key via kid header → JWKS
     * 3. Verifies SHA256withRSA signature
     * 4. Validates nonce, timestamp (≤5 min), package name
     * 5. Returns parsed IntegrityPayload
     *
     * Throws SecurityException on any verification failure.
     */
    internal fun verifyIntegrityToken(token: String, expectedNonce: String, packageName: String): IntegrityPayload {
        val parts = token.split(".")
        if (parts.size != 3) {
            throw SecurityException("Malformed JWT: expected 3 parts, got ${parts.size}")
        }

        // ── Parse header for kid ──
        val headerJson = JSONObject(
            String(android.util.Base64.decode(parts[0], android.util.Base64.URL_SAFE))
        )
        val kid = headerJson.optString("kid", "")
        if (kid.isEmpty()) throw SecurityException("Missing kid in JWT header")

        // ── Resolve public key from JWKS ──
        val jwk = getJwksKeys().find { it.kid == kid }
            ?: throw SecurityException("Unknown kid: $kid")

        val publicKey = buildRsaPublicKey(jwk.n, jwk.e)

        // ── Verify RSA signature ──
        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8)
        val signatureBytes = android.util.Base64.decode(parts[2], android.util.Base64.URL_SAFE)

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initVerify(publicKey)
        sig.update(signingInput)
        if (!sig.verify(signatureBytes)) {
            throw SecurityException("JWT signature verification failed")
        }

        // ── Parse payload claims ──
        val payloadJson = JSONObject(
            String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
        )

        // Verify nonce
        val actualNonce = payloadJson.optString("nonce", "")
        if (actualNonce != expectedNonce) {
            throw SecurityException("Nonce mismatch")
        }

        // Verify timestamp (≤5 minutes)
        val timestampMs = payloadJson.optLong("timestampMs", 0)
        val nowMs = System.currentTimeMillis()
        if (timestampMs <= 0 || nowMs - timestampMs > 5 * 60 * 1000) {
            throw SecurityException("Token expired or invalid timestamp")
        }
        if (timestampMs > nowMs + 60_000) {
            throw SecurityException("Token timestamp from future")
        }

        // Verify package name
        val actualPackageName = payloadJson.optString("apkPackageName", "")
        if (actualPackageName != packageName) {
            throw SecurityException("Package name mismatch: expected $packageName, got $actualPackageName")
        }

        // ── Parse verdicts ──
        val appVerdict = payloadJson.optJSONObject("appIntegrity")
            ?.optString("appRecognitionVerdict")
        val deviceVerdicts = payloadJson.optJSONObject("deviceIntegrity")
            ?.optJSONArray("deviceRecognitionVerdict")
            ?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }

        return IntegrityPayload(appVerdict, deviceVerdicts)
    }

    /**
     * Fetches Google's public keys from JWKS endpoint.
     * Caches in-memory for 24 hours.
     * On network failure, falls back to cached keys.
     */
    private fun getJwksKeys(): List<JwkKey> {
        val now = System.currentTimeMillis()
        val cached = _cachedJwks
        if (cached != null && (now - jwksCacheTime) < jwksCacheDurationMs) {
            return cached
        }

        return try {
            val url = URL(jwksUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.instanceFollowRedirects = true

            try {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                val json = JSONObject(response)
                val keysArray = json.getJSONArray("keys")
                val keys = (0 until keysArray.length()).map { i ->
                    val keyObj = keysArray.getJSONObject(i)
                    JwkKey(
                        kid = keyObj.getString("kid"),
                        n = keyObj.getString("n"),
                        e = keyObj.getString("e")
                    )
                }
                _cachedJwks = keys
                jwksCacheTime = now
                keys
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            // Fall back to cached keys if available
            _cachedJwks ?: throw SecurityException("Failed to fetch JWKS keys and no cache available", e)
        }
    }

    internal fun buildRsaPublicKey(nBase64: String, eBase64: String): PublicKey {
        val modulus = BigInteger(1, android.util.Base64.decode(nBase64, android.util.Base64.URL_SAFE))
        val exponent = BigInteger(1, android.util.Base64.decode(eBase64, android.util.Base64.URL_SAFE))
        val spec = RSAPublicKeySpec(modulus, exponent)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePublic(spec)
    }

    // ponytail: kept for reference but no longer called — decodeJwtPayload did not verify JWT signature
    private fun decodeJwtPayload(token: String): IntegrityPayload {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return IntegrityPayload(null, null)
            val json = JSONObject(
                String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
            )

            val appVerdict = json.optJSONObject("appIntegrity")
                ?.optString("appRecognitionVerdict")

            val deviceVerdicts = json.optJSONObject("deviceIntegrity")
                ?.optJSONArray("deviceRecognitionVerdict")
                ?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }

            IntegrityPayload(appVerdict, deviceVerdicts)
        } catch (_: Exception) {
            IntegrityPayload(null, null)
        }
    }
}
