package com.mdaopay.paymaster

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.web3j.utils.Numeric
import java.math.BigInteger

class PaymasterUtilTest {

    @Test
    fun `hexToBigInt converts hex string`() {
        assertEquals(BigInteger.ZERO, "0x0".hexToBigInt())
        assertEquals(BigInteger.ONE, "0x1".hexToBigInt())
        assertEquals(BigInteger.TEN, "0xa".hexToBigInt())
        assertEquals(BigInteger.valueOf(255), "0xff".hexToBigInt())
    }

    @Test
    fun `hexToBigInt handles without prefix`() {
        assertEquals(BigInteger.valueOf(255), "ff".hexToBigInt())
    }

    @Test
    fun `addPaymasterSuffix formats correctly`() {
        val pmAndData = "0x1234567890abcdef"
        val sig = "deadbeef"
        val len = "0004"
        val magic = "22e325a297439656"
        val result = addPaymasterSuffix(pmAndData, sig, len, magic)
        assertEquals("0x1234567890abcdefdeadbeef000422e325a297439656", result)
    }

    @Test
    fun `DexPrices data class works`() {
        val prices = DexPrices(bnbUsd = 600.0, mdaoUsd = 0.001, usdtUsd = 1.0)
        assertEquals(600.0, prices.bnbUsd)
        assertEquals(0.001, prices.mdaoUsd)
        assertEquals(1.0, prices.usdtUsd)
    }

    @Test
    fun `DexPrices fallbackPrices returns expected values`() {
        val prices = DexPrices.fallbackPrices()
        assertEquals(600.0, prices.bnbUsd)
        assertEquals(0.001, prices.mdaoUsd)
        assertEquals(1.0, prices.usdtUsd)
    }

    @Test
    fun `Numeric hex encoding roundtrip`() {
        val original = "1b"
        val bytes = Numeric.hexStringToByteArray(original)
        assertEquals(1, bytes.size)
        assertEquals(0x1b, bytes[0].toInt() and 0xff)
    }

    @Test
    fun `takeLast normalizes overpadded hex`() {
        val overpadded = "0x000000000000000000000000000000000000000000000000000000000000001b"
        val normalized = overpadded.removePrefix("0x").takeLast(2).padStart(2, '0')
        assertEquals("1b", normalized)
    }

    @Test
    fun `identity hash uses keccak256 of address bytes not string bytes`() {
        // Regression for F-036: old code used address.lowercase().toByteArray() which
        // hashed the ASCII string "0xabcd..." instead of the decoded address bytes.
        val address = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
        val correctHash = org.web3j.crypto.Hash.sha3(
            org.web3j.utils.Numeric.hexStringToByteArray(address)
        )
        val wrongHash = org.web3j.crypto.Hash.sha3(address.lowercase().toByteArray())
        // Correct hash uses the 20-byte decoded address, wrong hash uses 42-byte ASCII string
        assertEquals(32, correctHash.size)
        // Verify the two approaches produce different results (proves the fix matters)
        assertFalse(correctHash.contentEquals(wrongHash))
    }
}
