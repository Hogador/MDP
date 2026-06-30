package com.mdaopay.app.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShamirSecretSharingTest {

    @Test
    fun `split and join 2-of-3 with 16 byte secret`() {
        val secret = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10)
        val shares = ShamirSecretSharing.split(secret, 2, 3)
        assertEquals(3, shares.size)
        val restored = ShamirSecretSharing.join(shares.take(2))
        assertArrayEquals(secret, restored)
    }

    @Test
    fun `split and join 2-of-3 with 32 byte secret`() {
        val secret = ByteArray(32) { it.toByte() }
        val shares = ShamirSecretSharing.split(secret, 2, 3)
        val restored = ShamirSecretSharing.join(shares.takeLast(2))
        assertArrayEquals(secret, restored)
    }

    @Test
    fun `split and join 2-of-3 with 64 byte secret`() {
        val secret = ByteArray(64) { (it * 7 % 256).toByte() }
        val shares = ShamirSecretSharing.split(secret, 2, 3)
        val restored = ShamirSecretSharing.join(listOf(shares[0], shares[2]))
        assertArrayEquals(secret, restored)
    }

    @Test
    fun `split and join 3-of-5 with random secret`() {
        val secret = "Hello MDAO Pay! This is a test secret for recovery.".encodeToByteArray()
        val shares = ShamirSecretSharing.split(secret, 3, 5)
        val restored = ShamirSecretSharing.join(listOf(shares[1], shares[3], shares[4]))
        assertArrayEquals(secret, restored)
    }

    @Test
    fun `split and join 4-of-6`() {
        val secret = "Any 4 of 6 shares should reconstruct".encodeToByteArray()
        val shares = ShamirSecretSharing.split(secret, 4, 6)
        val restored = ShamirSecretSharing.join(listOf(shares[0], shares[2], shares[3], shares[5]))
        assertArrayEquals(secret, restored)
    }

    @Test
    fun `any 2 of 3 shares can reconstruct`() {
        val secret = "test-secret-256-bit-key-material".encodeToByteArray()
        val shares = ShamirSecretSharing.split(secret, 2, 3)

        val allCombinations = listOf(
            listOf(shares[0], shares[1]),
            listOf(shares[0], shares[2]),
            listOf(shares[1], shares[2])
        )

        for (combination in allCombinations) {
            assertArrayEquals(secret, ShamirSecretSharing.join(combination))
        }
    }

    @Test
    fun `different order of shares still reconstructs`() {
        val secret = "order-independent-reconstruction".encodeToByteArray()
        val shares = ShamirSecretSharing.split(secret, 2, 3)
        val restored = ShamirSecretSharing.join(listOf(shares[2], shares[0]))
        assertArrayEquals(secret, restored)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `single share cannot reconstruct`() {
        val secret = "too-few-shares".encodeToByteArray()
        val shares = ShamirSecretSharing.split(secret, 2, 3)
        ShamirSecretSharing.join(listOf(shares[0]))
    }

    @Test
    fun `all 3 shares reconstruct`() {
        val secret = "three-shares-should-work".encodeToByteArray()
        val shares = ShamirSecretSharing.split(secret, 2, 3)
        val restored = ShamirSecretSharing.join(shares)
        assertArrayEquals(secret, restored)
    }

    @Test
    fun `1 byte secret`() {
        val secret = byteArrayOf(0xAB.toByte())
        val shares = ShamirSecretSharing.split(secret, 2, 3)
        val restored = ShamirSecretSharing.join(shares.take(2))
        assertArrayEquals(secret, restored)
    }

    @Test
    fun `single character secret`() {
        val secret = "X".encodeToByteArray()
        val shares = ShamirSecretSharing.split(secret, 2, 3)
        val restored = ShamirSecretSharing.join(shares.take(2))
        assertArrayEquals(secret, restored)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty secret throws`() {
        ShamirSecretSharing.split(ByteArray(0), 2, 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `required less than 2 throws`() {
        ShamirSecretSharing.split("test".encodeToByteArray(), 1, 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `required greater than total throws`() {
        ShamirSecretSharing.split("test".encodeToByteArray(), 4, 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `more than 255 shares throws`() {
        ShamirSecretSharing.split("test".encodeToByteArray(), 2, 256)
    }

    @Test
    fun `different secrets produce different shares`() {
        val secret1 = "secret-one".encodeToByteArray()
        val secret2 = "secret-two".encodeToByteArray()

        val shares1 = ShamirSecretSharing.split(secret1, 2, 3)
        val shares2 = ShamirSecretSharing.split(secret2, 2, 3)

        assertNotEquals(shares1[0].values[0], shares2[0].values[0])
    }

    @Test
    fun `each share has unique x value`() {
        val secret = "unique-x".encodeToByteArray()
        val shares = ShamirSecretSharing.split(secret, 2, 3)

        assertEquals(1, shares[0].x)
        assertEquals(2, shares[1].x)
        assertEquals(3, shares[2].x)
    }

    @Test
    fun `share toByteArray roundtrip`() {
        val secret = "roundtrip-test".encodeToByteArray()
        val shares = ShamirSecretSharing.split(secret, 2, 3)

        for (share in shares) {
            val bytes = share.toByteArray()
            val restored = Share.fromByteArray(bytes)
            assertEquals(share.x, restored.x)
            assertArrayEquals(share.values, restored.values)
        }

        val fromBytes = shares.map { Share.fromByteArray(it.toByteArray()) }
        assertArrayEquals(secret, ShamirSecretSharing.join(fromBytes.take(2)))
    }
}
