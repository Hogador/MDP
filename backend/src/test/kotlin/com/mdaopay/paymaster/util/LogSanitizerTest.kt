package com.mdaopay.paymaster.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.sql.SQLException
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerializationException

class LogSanitizerTest {

    // --- sanitizeError ---

    @Test
    fun `sanitizeError SQLException returns DB error with code and state`() {
        val sqlEx = SQLException("mock msg", "42P01", 7)
        val msg = LogSanitizer.sanitizeError(sqlEx)
        assertTrue(msg.contains("DB error"), "Should indicate DB error: $msg")
        assertTrue(msg.contains("code=7"), "Should contain error code: $msg")
        assertTrue(msg.contains("state=42P01"), "Should contain SQL state: $msg")
        // Must NOT leak the original message "mock msg"
        assertFalse(msg.contains("mock"), "Should NOT leak SQL message: $msg")
    }

    @Test
    fun `sanitizeError ConnectException returns Connection refused`() {
        val ex = ConnectException("Connection refused to db.example.com")
        val msg = LogSanitizer.sanitizeError(ex)
        assertEquals("Connection refused", msg, "Should return generic message")
    }

    @Test
    fun `sanitizeError SocketTimeoutException returns Timeout`() {
        val ex = SocketTimeoutException("Read timed out after 30s")
        val msg = LogSanitizer.sanitizeError(ex)
        assertEquals("Timeout", msg, "Should return generic timeout message")
    }

    @Test
    fun `sanitizeError SerializationException returns sanitized message`() {
        val ex = SerializationException("Unexpected character at line 42")
        val msg = LogSanitizer.sanitizeError(ex)
        assertTrue(msg.contains("Invalid serialization"), "Should indicate serialization error: $msg")
    }

    @Test
    fun `sanitizeError IllegalArgumentException returns first 100 chars of message`() {
        val ex = IllegalArgumentException("Very long error message with potential sensitive data: abcdefghijklmnopqrstuvwxyz1234567890!@#$%^&*()_+")
        val msg = LogSanitizer.sanitizeError(ex)
        assertTrue(msg.startsWith("Invalid input: "), "Should prefix with 'Invalid input: '")
        assertTrue(msg.length <= "Invalid input: ".length + 100, "Should truncate to 100 chars")
    }

    @Test
    fun `sanitizeError unknown exception returns Internal error with class name`() {
        val ex = RuntimeException("Sensitive data here")
        val msg = LogSanitizer.sanitizeError(ex)
        assertTrue(msg.startsWith("Internal error"), "Should indicate internal error: $msg")
        assertTrue(msg.contains("RuntimeException"), "Should contain class name: $msg")
        // Must NOT leak the original message
        assertFalse(msg.contains("Sensitive"), "Should NOT leak original message: $msg")
    }

    // --- sanitizeAddress ---

    @Test
    fun `sanitizeAddress preserves correlation`() {
        val addr = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
        val sanitized = LogSanitizer.sanitizeAddress(addr)
        assertEquals("0xf39F...2266", sanitized, "Should show first 6 + last 4 chars")
        // Same address always produces same sanitized form (correlation preserved)
        val sanitized2 = LogSanitizer.sanitizeAddress(addr)
        assertEquals(sanitized, sanitized2, "Should be deterministic for same address")
    }

    @Test
    fun `sanitizeAddress handles short strings`() {
        assertEquals("***", LogSanitizer.sanitizeAddress("0x1234"), "Short addr should be masked")
        assertEquals("***", LogSanitizer.sanitizeAddress(""), "Empty string should be masked")
    }

    @Test
    fun `sanitizeAddress different addresses produce different output`() {
        val addr1 = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
        val addr2 = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"
        val s1 = LogSanitizer.sanitizeAddress(addr1)
        val s2 = LogSanitizer.sanitizeAddress(addr2)
        assertFalse(s1 == s2, "Different addresses should produce different sanitized forms")
    }

    // --- sanitizeHash ---

    @Test
    fun `sanitizeHash returns first 8 chars`() {
        val hash = "0xabcdef1234567890deadbeefcafebabe12345678"
        assertEquals("0xabcdef", LogSanitizer.sanitizeHash(hash), "Should show first 8 chars")
    }

    @Test
    fun `sanitizeHash deterministic correlation`() {
        val hash = "0xdeadbeefcafebabedeadbeefcafebabedeadbeef"
        assertEquals(
            LogSanitizer.sanitizeHash(hash),
            LogSanitizer.sanitizeHash(hash),
            "Should be deterministic"
        )
    }

    // --- Regression: FP-LOG-001 patterns ---

    @Test
    fun `test error id pattern logs safely`() {
        // This simulates the approved pattern:
        //   log.error("Operation failed reason={}", LogSanitizer.sanitizeError(e))
        //   if (log.isDebugEnabled) log.debug("Operation failed details", e)
        val ex = SQLException("DROP TABLE users; -- leak", "42601", 123)
        val reason = LogSanitizer.sanitizeError(ex)
        // Safe: no SQL injection in log output
        assertFalse(reason.contains("DROP"), "Should not contain SQL from message")
        assertFalse(reason.contains("users"), "Should not contain table names")
        // Useful: error code and state are preserved
        assertTrue(reason.contains("code=123"), "Should preserve error code for diagnostics")
        assertTrue(reason.contains("state=42601"), "Should preserve SQL state for diagnostics")
    }

    @Test
    fun `test SQLException does not leak schema`() {
        val ex = SQLException("Cannot drop table 'users' - schema 'public'", "42601", 7)
        val msg = LogSanitizer.sanitizeError(ex)
        assertFalse(msg.contains("users"), "Should not leak table name")
        assertFalse(msg.contains("public"), "Should not leak schema name")
        assertFalse(msg.contains("drop"), "Should not leak SQL verb")
        assertTrue(msg.contains("DB error"), "Should indicate DB error category")
    }
}
