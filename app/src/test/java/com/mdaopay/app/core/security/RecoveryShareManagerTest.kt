package com.mdaopay.app.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryShareManagerTest {

    @Test
    fun `split produces 4 shares`() {
        val secret = "test-recovery-secret-32-bytes!!".encodeToByteArray()
        val shares = RecoveryShareManager.split(secret)
        assertEquals(4, shares.size)
        assertEquals(1, shares[0].x)
        assertEquals(2, shares[1].x)
        assertEquals(3, shares[2].x)
        assertEquals(4, shares[3].x)
        assertEquals(secret.size, shares[0].values.size)
    }

    @Test
    fun `recover from any 3 of 4 shares`() {
        val secret = "recovery-secret-256-bit".encodeToByteArray()
        val shares = RecoveryShareManager.split(secret)

        val combo1 = RecoveryShareManager.join(listOf(shares[0], shares[1], shares[2]))
        val combo2 = RecoveryShareManager.join(listOf(shares[0], shares[1], shares[3]))
        val combo3 = RecoveryShareManager.join(listOf(shares[0], shares[2], shares[3]))
        val combo4 = RecoveryShareManager.join(listOf(shares[1], shares[2], shares[3]))

        assertArrayEquals(secret, combo1)
        assertArrayEquals(secret, combo2)
        assertArrayEquals(secret, combo3)
        assertArrayEquals(secret, combo4)
    }

    @Test
    fun `recover from all 4 shares`() {
        val secret = "all-four-shares".encodeToByteArray()
        val shares = RecoveryShareManager.split(secret)
        val restored = RecoveryShareManager.join(shares)
        assertArrayEquals(secret, restored)
    }

    @Test
    fun `recover with insufficient shares returns null`() {
        val secret = "needs-three-shares".encodeToByteArray()
        val shares = RecoveryShareManager.split(secret)
        val restored = RecoveryShareManager.join(listOf(shares[0], shares[1]))
        assertEquals(null, restored)
    }

    @Test
    fun `recover with wrong shares returns unexpected result`() {
        val secret1 = "eighteenchars1!!".encodeToByteArray()
        val secret2 = "eighteenchars2!!".encodeToByteArray()
        val shares1 = RecoveryShareManager.split(secret1)
        val shares2 = RecoveryShareManager.split(secret2)

        val wrong = RecoveryShareManager.join(listOf(shares1[0], shares2[1], shares1[2]))
        assertNotNull(wrong)
        assertTrue(!secret1.contentEquals(wrong!!) && !secret2.contentEquals(wrong))
    }

    @Test
    fun `split with 3-of-5 still works`() {
        val secret = "three-of-five-test".encodeToByteArray()
        val shares = ShamirSecretSharing.split(secret, 3, 5)
        assertEquals(5, shares.size)

        val restored = ShamirSecretSharing.join(listOf(shares[0], shares[2], shares[4]))
        assertArrayEquals(secret, restored)
    }

    @Test
    fun `full roundtrip end to end`() {
        val originalSecret = "mdao-pay-secret-phrase-for-testing".encodeToByteArray()

        val shares = RecoveryShareManager.split(originalSecret)
        assertEquals(4, shares.size)

        val recovered = RecoveryShareManager.join(listOf(shares[0], shares[2], shares[3]))
        assertArrayEquals(originalSecret, recovered!!)
    }
}
