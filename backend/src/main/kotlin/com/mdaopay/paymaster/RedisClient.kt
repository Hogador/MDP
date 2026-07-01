package com.mdaopay.paymaster

import io.lettuce.core.RedisClient as LettuceRedisClient
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.codec.ByteArrayCodec
import org.slf4j.LoggerFactory
import com.mdaopay.paymaster.util.LogSanitizer
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("RedisClient")
private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 1000L

object Redis {
    private var client: LettuceRedisClient? = null
    private var connection: RedisCoroutinesCommands<ByteArray, ByteArray>? = null
    private var redisUri: String = "redis://localhost:6379"

    fun connect(uri: String = "redis://localhost:6379") {
        if (client != null) return
        redisUri = uri
        try {
            client = LettuceRedisClient.create(redisUri)
            val conn = client!!.connect(ByteArrayCodec())
            connection = conn.async() as RedisCoroutinesCommands<ByteArray, ByteArray>
            // F-013: Don't log full URI (may contain password)
            val safeUri = redisUri.replaceAfter("://", "***").substringBefore("@")
            log.info("Connected to Redis at $safeUri")
        } catch (e: Exception) {
            log.error("Failed to connect to Redis reason={} Running without Redis.", LogSanitizer.sanitizeError(e))
            if (log.isDebugEnabled) log.debug("Redis connection failed details", e)
        }
    }

    // ponytail: simple reconnect — no exponential backoff, just reset and retry
    private fun reconnect() {
        try { client?.shutdown() } catch (_: Exception) {}
        client = null
        connection = null
        try {
            client = LettuceRedisClient.create(redisUri)
            val conn = client!!.connect(ByteArrayCodec())
            connection = conn.async() as RedisCoroutinesCommands<ByteArray, ByteArray>
            log.info("Redis reconnected")
        } catch (e: Exception) {
            log.warn("Redis reconnection failed: {}", LogSanitizer.sanitizeError(e))
        }
    }

    // ponytail: retry up to 3 times with 1s delay, reconnects on each failure
    private suspend fun <T> retry(block: suspend (RedisCoroutinesCommands<ByteArray, ByteArray>) -> T?): T? {
        repeat(MAX_RETRIES) {
            val c = connection ?: return null
            try { return block(c) } catch (_: Exception) {
                if (it < MAX_RETRIES - 1) { reconnect(); delay(RETRY_DELAY_MS) }
            }
        }
        return null
    }

    suspend fun incr(key: ByteArray): Long? = retry { it.incr(key) }

    suspend fun expire(key: ByteArray, seconds: Long): Boolean = retry { it.expire(key, seconds) ?: false } ?: false
    suspend fun setEx(key: ByteArray, seconds: Long, value: ByteArray): Boolean = retry { it.setex(key, seconds, value); true } ?: false
    suspend fun exists(key: ByteArray): Boolean = retry { (it.exists(key) ?: 0) > 0 } ?: false
    suspend fun getDel(key: ByteArray): ByteArray? = retry { it.getdel(key) }

    suspend fun incr(key: String): Long? = incr(key.encodeToByteArray())
    suspend fun expire(key: String, seconds: Long): Boolean = expire(key.encodeToByteArray(), seconds)
    suspend fun setEx(key: String, seconds: Long, value: ByteArray): Boolean = setEx(key.encodeToByteArray(), seconds, value)
    suspend fun exists(key: String): Boolean = exists(key.encodeToByteArray())
    suspend fun getDel(key: String): ByteArray? = getDel(key.encodeToByteArray())

    suspend fun set(key: ByteArray, value: ByteArray): Boolean = retry { it.set(key, value); true } ?: false

    suspend fun set(key: String, value: ByteArray): Boolean = set(key.encodeToByteArray(), value)

    suspend fun get(key: ByteArray): ByteArray? = retry { it.get(key) }

    suspend fun get(key: String): ByteArray? = get(key.encodeToByteArray())

    /**
     * Atomic SET if Not eXists — distributed lock primitive.
     * Returns true if key was set, false if already exists.
     * ponytail: used for cross-instance nickname claim (F-019).
     */
    suspend fun setNx(key: String, value: ByteArray = byteArrayOf(1)): Boolean =
        retry { it.setnx(key.encodeToByteArray(), value) ?: false } ?: false

    suspend fun del(key: ByteArray): Boolean = retry { it.del(key); true } ?: false
    suspend fun del(key: String): Boolean = del(key.encodeToByteArray())

    suspend fun hSet(key: ByteArray, field: ByteArray, value: ByteArray): Boolean =
        retry { it.hset(key, field, value); true } ?: false

    suspend fun hSet(key: String, field: String, value: ByteArray): Boolean =
        hSet(key.encodeToByteArray(), field.encodeToByteArray(), value)

    suspend fun hGetAll(key: ByteArray): Map<ByteArray, ByteArray>? {
        return retry { c ->
            val map = mutableMapOf<ByteArray, ByteArray>()
            c.hgetall(key).collect { kv ->
                if (kv.value != null) {
                    map[kv.key!!] = kv.value!!
                }
            }
            map
        }
    }

    suspend fun hGetAll(key: String): Map<ByteArray, ByteArray>? = hGetAll(key.encodeToByteArray())

    suspend fun hDel(key: ByteArray, vararg fields: ByteArray): Boolean =
        retry { it.hdel(key, *fields); true } ?: false

    suspend fun hDel(key: String, vararg fields: String): Boolean =
        hDel(key.encodeToByteArray(), *fields.map { it.encodeToByteArray() }.toTypedArray())

    fun close() {
        connection?.let { /* Lettuce coroutines doesn't expose close */ }
        client?.shutdown()
    }
}

class RedisRateLimiter(private val prefix: String = "ratelimit") {
    private val fallbackMap = ConcurrentHashMap<String, RateLimitEntry>()
    private var accessCounter = 0

    private data class RateLimitEntry(val count: Long, val expiresAt: Long)

    private val rateLimitLog = LoggerFactory.getLogger("RedisRateLimiter")

    // F-126: scavenge expired entries every 100th access
    private fun scavenge() {
        if (++accessCounter % 100 != 0) return
        val now = System.currentTimeMillis()
        fallbackMap.entries.removeIf { (_, entry) -> now > entry.expiresAt }
    }

    suspend fun isLimited(key: String, maxRequests: Int, windowSec: Long): Boolean {
        val redisKey = "$prefix:$key"
        val count = Redis.incr(redisKey)
        if (count != null) {
            if (count == 1L) Redis.expire(redisKey, windowSec)
            return count > maxRequests
        }
        // Redis unavailable — in-memory fallback
        return isLimitedInMemory(key, maxRequests, windowSec)
    }

    private fun isLimitedInMemory(key: String, maxRequests: Int, windowSec: Long): Boolean {
        scavenge()
        val now = System.currentTimeMillis()
        val entry = fallbackMap[key]
        if (entry == null || now > entry.expiresAt) {
            fallbackMap[key] = RateLimitEntry(1, now + windowSec * 1000)
            rateLimitLog.warn("Redis unavailable — rate limit fallback for key={}", key)
            return false
        }
        val newCount = entry.count + 1
        fallbackMap[key] = entry.copy(count = newCount)
        return newCount > maxRequests
    }
}

class RedisReplayCache(private val prefix: String = "replay") {
    private val fallbackMap = ConcurrentHashMap<String, Long>()
    private var accessCounter = 0

    private val replayLog = LoggerFactory.getLogger("RedisReplayCache")

    // F-126: scavenge expired entries every 100th access
    private fun scavenge() {
        if (++accessCounter % 100 != 0) return
        val now = System.currentTimeMillis()
        fallbackMap.entries.removeIf { (_, expiresAt) -> now > expiresAt }
    }

    suspend fun isUsed(key: String, ttlSec: Long = 300): Boolean {
        val redisKey = "$prefix:$key"
        if (Redis.exists(redisKey)) return true
        val ok = Redis.setEx(redisKey, ttlSec, byteArrayOf(1))
        if (ok) return false
        // Redis unavailable — in-memory fallback
        return isUsedInMemory(key, ttlSec)
    }

    private fun isUsedInMemory(key: String, ttlSec: Long): Boolean {
        scavenge()
        val now = System.currentTimeMillis()
        val expiresAt = fallbackMap[key]
        if (expiresAt != null && now < expiresAt) return true
        fallbackMap[key] = now + ttlSec * 1000
        replayLog.warn("Redis unavailable — replay cache fallback for key={}", key)
        return false
    }
}
