package com.mdaopay.paymaster.util

import io.ktor.client.plugins.ClientRequestException
import java.sql.SQLException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerializationException

/**
 * Context-aware error sanitizer per FP-LOG-001.
 *
 * Preserves debug-relevant information without leaking secrets/PII.
 * Usage:
 *   log.error("Operation failed reason={}", LogSanitizer.sanitizeError(e))
 *   if (log.isDebugEnabled) log.debug("Operation failed details", e)
 */
object LogSanitizer {

    fun sanitizeError(e: Throwable): String = when (e) {
        is SQLException -> "DB error code=${e.errorCode} state=${e.sqlState}"
        is ConnectException -> "Connection refused"
        is SocketTimeoutException -> "Timeout"
        is SerializationException -> "Invalid serialization: ${e.message?.take(100)}"
        is IllegalArgumentException -> "Invalid input: ${e.message?.take(100)}"
        is ClientRequestException -> {
            "HTTP ${e.response.status}"
        }
        else -> "Internal error type=${e::class.simpleName ?: "Unknown"}"
    }

    /**
     * Sanitize an Ethereum address for logging: show first 6 + last 4 chars.
     * Preserves correlation while hiding the full address.
     */
    fun sanitizeAddress(addr: String): String =
        if (addr.length < 10) "***" else "${addr.take(6)}...${addr.takeLast(4)}"

    /**
     * Sanitize a tx hash for logging: show first 8 chars.
     */
    fun sanitizeHash(hash: String): String = hash.take(8)
}
