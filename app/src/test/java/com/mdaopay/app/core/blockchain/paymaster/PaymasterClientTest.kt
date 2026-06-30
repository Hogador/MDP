package com.mdaopay.app.core.blockchain.paymaster

import com.mdaopay.app.core.blockchain.NetworkConfig
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.web3j.utils.Numeric
import java.math.BigInteger

class PaymasterClientTest {

    private val client = PaymasterClient(OkHttpClient.Builder().build())

    @Test
    fun `encodePaymasterAndData builds valid hex without permit`() {
        val result = client.encodePaymasterAndData(
            token = NetworkConfig.MDAO_CONTRACT,
            maxTokenAmount = BigInteger.valueOf(1000),
        )
        val hex = Numeric.toHexString(result)
        assert(hex.startsWith("0x"))
        assertEquals(210, hex.length) // 0x + 208 hex chars
    }

    @Test
    fun `encodePaymasterAndData includes permit data`() {
        val result = client.encodePaymasterAndData(
            token = NetworkConfig.MDAO_CONTRACT,
            maxTokenAmount = BigInteger.valueOf(1000),
            permitDeadline = BigInteger.valueOf(9999999999L),
            permitV = "1b",
            permitR = "0x" + "ab".repeat(32),
            permitS = "0x" + "cd".repeat(32),
        )
        val hex = Numeric.toHexString(result)
        // 104 + 97 = 201 bytes = 402 hex chars + prefix
        assertEquals(404, hex.length) // 0x + 402 hex chars
    }

    @Test
    fun `encodePaymasterAndData normalizes permitV`() {
        // Overpadded v = 32 bytes
        val overpaddedV = "0x" + "00".repeat(31) + "1b"
        val result = client.encodePaymasterAndData(
            token = NetworkConfig.MDAO_CONTRACT,
            maxTokenAmount = BigInteger.valueOf(1000),
            permitDeadline = BigInteger.valueOf(9999999999L),
            permitV = overpaddedV,
            permitR = "0x" + "ab".repeat(32),
            permitS = "0x" + "cd".repeat(32),
        )
        val hex = Numeric.toHexString(result)
        val vHex = hex.substring(274, 276)
        assertEquals("1b", vHex)
    }

    @Test
    fun `encodePaymasterAndData without permit produces correct length`() {
        val result = client.encodePaymasterAndData(
            token = NetworkConfig.MDAO_CONTRACT,
            maxTokenAmount = BigInteger.valueOf(1000),
        )
        // 20 + 20 + 32 + 32 = 104 bytes
        assertEquals(104, result.size)
    }

    @Test
    fun `encodePaymasterAndData with permit produces correct length`() {
        val result = client.encodePaymasterAndData(
            token = NetworkConfig.MDAO_CONTRACT,
            maxTokenAmount = BigInteger.valueOf(1000),
            permitDeadline = BigInteger.valueOf(9999999999L),
            permitV = "1b",
            permitR = "0x" + "ab".repeat(32),
            permitS = "0x" + "cd".repeat(32),
        )
        // 20 + 20 + 32 + 32 + 32 + 1 + 32 + 32 = 201 bytes
        assertEquals(201, result.size)
    }
}
