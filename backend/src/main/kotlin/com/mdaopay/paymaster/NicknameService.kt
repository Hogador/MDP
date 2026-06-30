package com.mdaopay.paymaster

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import org.slf4j.LoggerFactory
import com.mdaopay.paymaster.util.LogSanitizer
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

private val nickLog = LoggerFactory.getLogger("NicknameService")

@Serializable
data class RegisterRequest(
    val nickname: String,
    val address: String,
    val signature: String,
    val nonce: Long = System.currentTimeMillis(),
)

@Serializable
data class NicknameEntry(
    val nickname: String,
    val address: String,
    val registeredAt: Long = System.currentTimeMillis(),
)

@Serializable
data class NicknameError(
    val error: String
)

object NicknameService {
    private val nicknameToAddress = ConcurrentHashMap<String, String>()
    private val addressToNickname = ConcurrentHashMap<String, String>()
    private const val NONCE_WINDOW_MS = 300_000L

    private const val REDIS_NICK_HASH = "nicknames"
    private const val REDIS_ADDR_HASH = "addresses"

    var repo: NicknameRepository? = null
    var registryClient: OnChainRegistryClient? = null

    fun loadFromRedis() {
        runBlocking {
            try {
                val nickMap = Redis.hGetAll(REDIS_NICK_HASH)
                if (nickMap != null) {
                    nickMap.forEach { (nickBytes, addrBytes) ->
                        val nick = nickBytes.decodeToString()
                        val addr = addrBytes.decodeToString()
                        nicknameToAddress[nick] = addr
                        addressToNickname[addr] = nick
                    }
                    nickLog.info("Nicknames loaded from Redis count={}", nickMap.size)
                }
            } catch (e: Exception) {
                nickLog.warn("Failed to load nicknames from Redis reason={}", LogSanitizer.sanitizeError(e))
                if (nickLog.isDebugEnabled) nickLog.debug("Failed to load nicknames from Redis details", e)
            }
        }
    }

    fun resolve(nickname: String): NicknameEntry? {
        val key = nickname.lowercase()
        val address = nicknameToAddress[key]
        if (address != null) return NicknameEntry(nickname = key, address = address)
        val dbEntry = repo?.findByNickname(key)
        if (dbEntry != null) {
            nicknameToAddress[key] = dbEntry.address
            addressToNickname[dbEntry.address] = key
        }
        return dbEntry
    }

    fun reverseResolve(address: String): NicknameEntry? {
        val normalized = address.lowercase()
        val nickname = addressToNickname[normalized]
        if (nickname != null) return NicknameEntry(nickname = nickname, address = normalized)
        val dbEntry = repo?.findByAddress(normalized)
        if (dbEntry != null) {
            nicknameToAddress[dbEntry.nickname] = dbEntry.address
            addressToNickname[dbEntry.address] = dbEntry.nickname
        }
        return dbEntry
    }

    fun register(nickname: String, address: String, signature: String, nonce: Long): Result<NicknameEntry> {
        val key = nickname.lowercase()
        val normalizedAddress = address.lowercase()

        when (val result = NicknamePolicy.validate(nickname)) {
            is ValidationResult.Valid -> { }
            is ValidationResult.TooShort -> return Result.failure(
                IllegalArgumentException("Nickname too short: minimum 3 characters")
            )
            is ValidationResult.TooLong -> return Result.failure(
                IllegalArgumentException("Nickname too long: maximum 20 characters")
            )
            is ValidationResult.InvalidChars -> return Result.failure(
                IllegalArgumentException("Nickname contains invalid characters: letters, digits, underscore, hyphen only")
            )
            is ValidationResult.Reserved -> return Result.failure(
                IllegalArgumentException("Nickname '${result.name}' is reserved")
            )
        }

        val now = System.currentTimeMillis()
        if (nonce < now - NONCE_WINDOW_MS || nonce > now + 10_000) {
            return Result.failure(IllegalArgumentException("Nonce expired or invalid"))
        }

        if (signature.isBlank()) {
            return Result.failure(IllegalArgumentException("Signature is required"))
        }

        try {
            val message = "Register nickname $nickname for $normalizedAddress (nonce: $nonce)"
            val messageBytes = message.encodeToByteArray()

            val sigBytes = if (signature.startsWith("0x")) {
                Numeric.hexStringToByteArray(signature.substring(2))
            } else {
                Numeric.hexStringToByteArray(signature)
            }

            val r = Numeric.toBigInt(sigBytes.copyOfRange(0, 32))
            val s = Numeric.toBigInt(sigBytes.copyOfRange(32, 64))
            val v = sigBytes[64].toInt().let { if (it < 0) it + 256 else it }

            val SECP256K1_HALF_ORDER = BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", 16)
            if (s > SECP256K1_HALF_ORDER) {
                return Result.failure(IllegalArgumentException("Invalid signature: s-value too high"))
            }

            val recoveredKey = Sign.signedPrefixedMessageToKey(messageBytes, Sign.SignatureData(v.toByte(), Numeric.toBytesPadded(r, 32), Numeric.toBytesPadded(s, 32)))
            val recoveredHex = Keys.toChecksumAddress(Keys.getAddress(recoveredKey))
            val expectedHex = Keys.toChecksumAddress(normalizedAddress)

            if (!recoveredHex.equals(expectedHex, ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("Signature does not match address"))
            }
        } catch (e: IllegalArgumentException) {
            return Result.failure(IllegalArgumentException("Invalid signature"))
        } catch (e: IndexOutOfBoundsException) {
            return Result.failure(IllegalArgumentException("Invalid signature: malformed input"))
        }

        val rClient = registryClient
        if (rClient != null && !rClient.isIdentityRegistered(normalizedAddress)) {
            return Result.failure(IllegalArgumentException(
                "Address must be registered on-chain in NicknameRegistry first"
            ))
        }

        // F-019: Distributed lock via Redis SETNX (cross-instance atomic claim)
        val lockKey = "nickname:lock:$key"
        val lockAcquired = runBlocking { Redis.setNx(lockKey, normalizedAddress.encodeToByteArray()) }
        if (!lockAcquired) {
            return Result.failure(IllegalArgumentException("Nickname already taken"))
        }
        runBlocking { Redis.expire(lockKey, 30) } // auto-release if crash
        try {
            if (addressToNickname.containsKey(normalizedAddress)) {
                return Result.failure(IllegalArgumentException("Address already has a nickname"))
            }

            val dbRepo = repo
            if (dbRepo != null) {
                if (!dbRepo.insert(key, normalizedAddress)) {
                    return Result.failure(IllegalArgumentException("Nickname already taken"))
                }
            }

            nicknameToAddress[key] = normalizedAddress
            addressToNickname[normalizedAddress] = key

            runBlocking {
                Redis.hSet(REDIS_NICK_HASH, key, normalizedAddress.encodeToByteArray())
                Redis.hSet(REDIS_ADDR_HASH, normalizedAddress, key.encodeToByteArray())
            }
        } finally {
            runBlocking { Redis.del(lockKey) } // release lock
        }

        return Result.success(NicknameEntry(nickname = key, address = normalizedAddress))
    }

    fun contains(nickname: String): Boolean {
        val key = nickname.lowercase()
        if (nicknameToAddress.containsKey(key)) return true
        return repo?.nicknameExists(key) ?: false
    }

    fun getAll(): Map<String, String> {
        return nicknameToAddress.toMap()
    }

    fun getStats(): NicknameStats {
        val dbCount = repo?.count() ?: 0
        return NicknameStats(
            totalNicknames = maxOf(nicknameToAddress.size, dbCount),
            uniqueAddresses = maxOf(addressToNickname.size, dbCount),
        )
    }
}

@Serializable
data class NicknameStats(
    val totalNicknames: Int,
    val uniqueAddresses: Int,
)
