package com.mdaopay.paymaster

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IPv6NormalizationTest {

    @Test
    fun `test IPv4 addresses unchanged`() {
        assertEquals("192.168.1.1", normalizeIpAddress("192.168.1.1"))
        assertEquals("10.0.0.1", normalizeIpAddress("10.0.0.1"))
        assertEquals("8.8.8.8", normalizeIpAddress("8.8.8.8"))
    }

    @Test
    fun `test full IPv6 address normalized to 64-bit prefix`() {
        assertEquals(
            "2001:db8:85a3:0:0:0:0:0/64",
            normalizeIpAddress("2001:db8:85a3:0:0:0:0:0")
        )
        assertEquals(
            "2001:db8:85a3:0:0:0:0:0/64",
            normalizeIpAddress("2001:db8:85a3:1234:5678:9abc:def0:1234")
        )
    }

    @Test
    fun `test compressed IPv6 with :: expanded correctly`() {
        // ::1 should expand to 0:0:0:0:0:0:0:1 then mask to 0:0:0:0:0:0:0:0/64
        assertEquals("0:0:0:0:0:0:0:0/64", normalizeIpAddress("::1"))
        
        // 2001:db8::1 should expand and mask
        assertEquals("2001:db8:0:0:0:0:0:0/64", normalizeIpAddress("2001:db8::1"))
    }

    @Test
    fun `test loopback IPv6`() {
        assertEquals("0:0:0:0:0:0:0:0/64", normalizeIpAddress("::1"))
        assertEquals("0:0:0:0:0:0:0:0/64", normalizeIpAddress("::"))
    }

    @Test
    fun `test IPv6 with mixed segments`() {
        assertEquals(
            "fe80:0:0:0:0:0:0:0/64",
            normalizeIpAddress("fe80::1")
        )
        assertEquals(
            "2001:db8:85a3:0:0:0:0:0/64",
            normalizeIpAddress("2001:db8:85a3::8d2:fe12:3456:789a")
        )
    }

    @Test
    fun `test rate limit bypass prevention`() {
        // These should all resolve to the same /64 prefix
        val variants = listOf(
            "2001:db8:85a3::1",
            "2001:db8:85a3::2",
            "2001:db8:85a3::abcd",
            "2001:db8:85a3::ffff"
        )
        
        val normalized = variants.map { normalizeIpAddress(it) }
        
        // All should be identical after normalization
        assertEquals(1, normalized.distinct().size, 
            "All IPv6 addresses in same /64 subnet should normalize to same value")
        assertEquals("2001:db8:85a3:0:0:0:0:0/64", normalized.first())
    }

    @Test
    fun `test different subnets remain different`() {
        val addr1 = normalizeIpAddress("2001:db8:85a3::1")
        val addr2 = normalizeIpAddress("2001:db8:85a4::1")
        
        assert(addr1 != addr2) { "Different /64 subnets should not normalize to same value" }
    }

    @Test
    fun `test invalid IP fails gracefully`() {
        // Should return original string on parse error (fail-safe)
        assertEquals("invalid-ip", normalizeIpAddress("invalid-ip"))
        assertEquals("", normalizeIpAddress(""))
    }
}
