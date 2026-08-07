package com.mdaopay.paymaster

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
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

data class SiweMessage(
    val domain: String,
    val address: String,
    val statement: String? = null,
    val uri: String? = null,
    val version: String? = null,
    val chainId: Long? = null,
    val nonce: String,
    val issuedAt: String? = null,
    val expirationTime: String? = null,
    val requestId: String? = null,
)

class AuthService(
    private val repo: AuthRepository,
    private val jwtSecret: String,
    private val accessTtlMin: Long = 15,
    private val refreshTtlDays: Long = 30,
    private val expectedChainId: Long? = null,
    private val expectedDomain: String? = null,
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    // ponytail: shared Mac would be thread-unsafe — create per call instead
    private fun hmac(): Mac = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(jwtSecret.toByteArray(), "HmacSHA256"))
    }

    // ── SIWE (EIP-4361) ──

    /**
     * Parses an EIP-4361 Sign-In with Ethereum message.
     * Returns null if the message is malformed or required fields (address, nonce) are missing.
     */
    fun parseSiweMessage(message: String): SiweMessage? {
        if (message.isBlank()) return null

        val lines = message.split("\n")
        if (lines.size < 3) return null

        // First line: "{domain} wants you to sign in with your Ethereum account:"
        val domainPrefix = " wants you to sign in with your Ethereum account:"
        val firstLine = lines[0]
        if (!firstLine.endsWith(domainPrefix)) return null
        val domain = firstLine.removeSuffix(domainPrefix)

        // Second line: Ethereum address
        val address = lines[1].trim()
        if (!address.startsWith("0x") || address.length != 42) return null

        // Third line should be blank
        if (lines[2].isNotBlank()) return null

        // Parse remaining lines — collect statement and key-value pairs
        var i = 3
        val statementLines = mutableListOf<String>()
        var uri: String? = null
        var version: String? = null
        var chainId: Long? = null
        var nonce: String? = null
        var issuedAt: String? = null
        var expirationTime: String? = null
        var requestId: String? = null

        // Collect statement (multi-line text between two blank lines)
        // Statement is everything before a blank line followed by a header
        var inStatement = true
        val headerPrefixes = setOf(
            "URI:", "Version:", "Chain ID:", "Nonce:",
            "Issued At:", "Expiration Time:", "Request ID:"
        )

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (inStatement) {
                // Check if this line starts a header
                if (headerPrefixes.any { trimmed.startsWith(it) }) {
                    inStatement = false
                    // Parse this line as a header
                    parseSiweHeader(trimmed)?.let { (key, value) ->
                        when (key) {
                            "URI" -> uri = value
                            "Version" -> version = value
                            "Chain ID" -> chainId = value.toLongOrNull()
                            "Nonce" -> nonce = value
                            "Issued At" -> issuedAt = value
                            "Expiration Time" -> expirationTime = value
                            "Request ID" -> requestId = value
                        }
                    }
                    i++
                    continue
                }
                // Blank line while in statement — could mark end of statement
                if (line.isEmpty()) {
                    // Peek at next line — if it's a header, statement is over
                    if (i + 1 < lines.size && headerPrefixes.any { lines[i + 1].trimStart().startsWith(it) }) {
                        inStatement = false
                        i++
                        continue
                    }
                    // Otherwise, blank line is part of statement (preserve it)
                    statementLines.add(line)
                    i++
                    continue
                }
                statementLines.add(line)
                i++
                continue
            }

            // We're past the statement — parsing headers
            if (trimmed.isEmpty()) {
                i++
                continue
            }
            parseSiweHeader(trimmed)?.let { (key, value) ->
                when (key) {
                    "URI" -> uri = value
                    "Version" -> version = value
                    "Chain ID" -> chainId = value.toLongOrNull()
                    "Nonce" -> nonce = value
                    "Issued At" -> issuedAt = value
                    "Expiration Time" -> expirationTime = value
                    "Request ID" -> requestId = value
                }
            }
            i++
        }

        // nonce is required
        val finalNonce = nonce ?: return null

        val statement = statementLines.joinToString("\n").ifBlank { null }

        return SiweMessage(
            domain = domain,
            address = address,
            statement = statement,
            uri = uri,
            version = version,
            chainId = chainId,
            nonce = finalNonce,
            issuedAt = issuedAt,
            expirationTime = expirationTime,
            requestId = requestId,
        )
    }

    /**
     * Recovers the Ethereum address that signed a message using EIP-191 (personal_sign).
     * Supports v values of 27/28 and 0/1.
     */
    fun recoverEthereumSigner(message: String, signature: String): String? {
        if (message.isBlank() || signature.isBlank()) return null
        return try {
            val sigHex = if (signature.startsWith("0x")) signature.substring(2) else signature
            val sigBytes = Numeric.hexStringToByteArray(sigHex)
            if (sigBytes.size != 65) return null

            val r = sigBytes.copyOfRange(0, 32)
            val s = sigBytes.copyOfRange(32, 64)
            var v = sigBytes[64].toInt()
            if (v < 0) v += 256

            // Normalize v to 27/28 for web3j
            if (v < 27) v += 27

            val sigData = Sign.SignatureData(v.toByte(), r, s)

            // Prepare EIP-191 message: "\x19Ethereum Signed Message:\n" + len(message) + message
            // signedMessageToKey internally applies Hash.sha3, so pass raw prefixed bytes
            val prefix = "\u0019Ethereum Signed Message:\n"
            val messageBytes = (prefix + message.length + message).toByteArray()

            val key = Sign.signedMessageToKey(messageBytes, sigData)
            "0x${Keys.getAddress(key).lowercase()}"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generates a cryptographically secure random nonce for SIWE and stores it.
     */
    fun generateNonce(walletAddress: String): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES)
        repo.storeSiweNonce(nonce, walletAddress, expiresAt)
        return nonce
    }

    /**
     * Verifies an SIWE message + signature pair.
     * On success, issues a TokenPair for the wallet's associated user.
     */
    fun verifySiwe(message: String, signature: String): Result<TokenPair> {
        val siwe = parseSiweMessage(message)
            ?: return Result.failure(IllegalArgumentException("Invalid SIWE message format"))

        // Verify nonce exists and hasn't been consumed/expired
        val storedWallet = repo.findAndDeleteSiweNonce(siwe.nonce)
            ?: return Result.failure(IllegalArgumentException("Nonce not found or expired"))

        // H-04: validate SIWE domain + chainId against expected values (anti cross-domain replay)
        expectedDomain?.let { expected ->
            if (!siwe.domain.equals(expected, ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("SIWE domain mismatch: expected $expected, got ${siwe.domain}"))
            }
        }
        expectedChainId?.let { expected ->
            val msgChainId = siwe.chainId ?: return Result.failure(IllegalArgumentException("SIWE Chain ID missing"))
            if (msgChainId != expected) {
                return Result.failure(IllegalArgumentException("SIWE Chain ID mismatch: expected $expected, got $msgChainId"))
            }
        }

        // Recover signer from signature
        val signer = recoverEthereumSigner(message, signature)
            ?: return Result.failure(IllegalArgumentException("Failed to recover signer from signature"))

        // Signer must match the address in the message
        if (!signer.equals(siwe.address.lowercase(), ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Signer does not match message address"))
        }

        // Signer must match the wallet that requested the nonce
        if (!signer.equals(storedWallet.lowercase(), ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Signer does not match nonce wallet"))
        }

        // Find or create user for this wallet address
        val walletEmail = signer.lowercase()
        val user = repo.findByEmail(walletEmail) ?: repo.create(
            email = walletEmail,
            passwordHash = "",
            passwordSalt = "",
        )

        return Result.success(issueTokens(user.id, wallet = signer))
    }

    private fun parseSiweHeader(line: String): Pair<String, String>? {
        val trimmed = line.trim()
        val colonIdx = trimmed.indexOf(':')
        if (colonIdx <= 0) return null
        val key = trimmed.substring(0, colonIdx).trim()
        val value = trimmed.substring(colonIdx + 1).trim()
        return Pair(key, value)
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
