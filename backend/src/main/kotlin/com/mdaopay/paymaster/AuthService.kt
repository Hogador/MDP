package com.mdaopay.paymaster

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

class AuthService(
    private val repo: AuthRepository,
    private val jwtSecret: String,
    private val accessTtlMin: Long = 15,
    private val refreshTtlDays: Long = 30,
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    // ponytail: shared Mac would be thread-unsafe — create per call instead
    private fun hmac(): Mac = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(jwtSecret.toByteArray(), "HmacSHA256"))
    }

    companion object {
        /** F-055: Minimum password complexity rules. */
        private val passwordRegex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")
        private const val MIN_PASSWORD_LENGTH = 8
    }

    fun register(email: String, password: String): Result<TokenPair> {
        if (repo.findByEmail(email) != null) {
            return Result.failure(IllegalArgumentException("Email already registered"))
        }
        // F-055: Min 8 chars, at least 1 uppercase, 1 lowercase, 1 digit
        if (password.length < MIN_PASSWORD_LENGTH || !passwordRegex.matches(password)) {
            return Result.failure(IllegalArgumentException(
                "Password must be at least 8 characters with uppercase, lowercase, and digit"
            ))
        }
        val salt = randomSalt()
        val hash = hashPassword(password, salt)
        val user = repo.create(email, hash, salt)
        return Result.success(issueTokens(user.id))
    }

    fun login(email: String, password: String): Result<TokenPair> {
        val user = repo.findByEmail(email)
            ?: return Result.failure(IllegalArgumentException("Invalid credentials"))
        val computedHash = hashPassword(password, user.passwordSalt)
        val expectedHash = Base64.getDecoder().decode(user.passwordHash)
        if (!MessageDigest.isEqual(
                Base64.getDecoder().decode(computedHash),
                expectedHash
            )) {
            return Result.failure(IllegalArgumentException("Invalid credentials"))
        }
        return Result.success(issueTokens(user.id))
    }

    fun refresh(refreshToken: String): Result<TokenPair> {
        val payload = decodeToken(refreshToken) ?: return Result.failure(IllegalArgumentException("Invalid refresh token"))
        if (payload["type"]?.jsonPrimitive?.content != "refresh") {
            return Result.failure(IllegalArgumentException("Not a refresh token"))
        }
        val jti = payload["jti"]?.jsonPrimitive?.content
            ?: return Result.failure(IllegalArgumentException("Invalid refresh token"))
        val stored = repo.findRefreshToken(jti)
            ?: return Result.failure(IllegalArgumentException("Refresh token revoked"))
        if (Instant.now().isAfter(stored.second)) {
            repo.deleteRefreshToken(jti)
            return Result.failure(IllegalArgumentException("Refresh token expired"))
        }
        repo.deleteRefreshToken(jti)
        return Result.success(issueTokens(stored.first))
    }

    fun validateAccessToken(token: String): String? {
        val payload = decodeToken(token) ?: return null
        if (payload["type"]?.jsonPrimitive?.content != "access") return null
        val exp = payload["exp"]?.jsonPrimitive?.longOrNull ?: return null
        if (Instant.now().epochSecond > exp) return null
        return payload["sub"]?.jsonPrimitive?.content
    }

    fun getUser(userId: String): AuthUser? = repo.findById(userId)

    private fun issueTokens(
        userId: String,
        wallet: String? = null,
        nickname: String? = null,
        scope: String? = null,
    ): TokenPair {
        val access = createToken(userId, "access", accessTtlMin, ChronoUnit.MINUTES, wallet = wallet, nickname = nickname, scope = scope)
        val jti = UUID.randomUUID().toString()
        val refreshExpiry = Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS)
        val refresh = createToken(userId, "refresh", refreshTtlDays, ChronoUnit.DAYS, jti)
        repo.storeRefreshToken(jti, userId, refreshExpiry)
        return TokenPair(accessToken = access, refreshToken = refresh, expiresIn = accessTtlMin * 60)
    }

    private fun createToken(
        sub: String,
        type: String,
        ttl: Long,
        unit: java.time.temporal.TemporalUnit,
        jti: String? = null,
        wallet: String? = null,
        nickname: String? = null,
        scope: String? = null,
    ): String {
        val now = Instant.now()
        val payload = buildJsonObject {
            put("sub", sub)
            put("type", type)
            put("iat", now.epochSecond)
            put("exp", now.plus(ttl, unit).epochSecond)
            jti?.let { put("jti", it) }
            if (type == "access") {
                wallet?.let { put("wallet", it) }
                nickname?.let { put("nickname", it) }
                scope?.let { put("scope", it) }
            }
        }
        return sign(payload.toString())
    }

    private fun sign(payloadJson: String): String {
        val mac = hmac()
        val header = base64Url("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val body = base64Url(payloadJson.toByteArray())
        val signature = base64Url(mac.doFinal("$header.$body".toByteArray()))
        return "$header.$body.$signature"
    }

    private fun decodeToken(token: String): JsonObject? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        val (header, body, sig) = parts
        val mac = hmac()
        val expected = base64Url(mac.doFinal("$header.$body".toByteArray()))
        if (!MessageDigest.isEqual(sig.toByteArray(), expected.toByteArray())) return null
        return try {
            Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(body))).jsonObject
        } catch (e: Exception) {
            null
        }
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun hashPassword(password: String, salt: String): String {
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(
            password.toCharArray(),
            Base64.getDecoder().decode(salt),
            600_000,
            256
        )
        val tmp = factory.generateSecret(spec)
        return Base64.getEncoder().encodeToString(tmp.encoded)
    }

    private fun base64Url(data: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(data)
}
