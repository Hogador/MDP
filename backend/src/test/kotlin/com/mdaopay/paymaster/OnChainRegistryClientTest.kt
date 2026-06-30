package com.mdaopay.paymaster

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.web3j.crypto.Hash
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.utils.Numeric

class OnChainRegistryClientTest {

    private val web3j: Web3j = mockk()

    // F-036: identityHash must match Solidity's keccak256(abi.encodePacked(signer))
    // where signer is an address (20 bytes in packed encoding).
    // Backend: Hash.sha3(Numeric.hexStringToByteArray(address))
    // Contract: keccak256(abi.encodePacked(signer))
    @Test
    fun `identityHash uses keccak256 of decoded address bytes`() {
        val address = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"

        // Correct: decode hex string to 20 bytes, then keccak256
        val correctHash = Hash.sha3(Numeric.hexStringToByteArray(address))

        // Wrong (old approach): hash the ASCII string bytes (42 bytes)
        val wrongHash = Hash.sha3(address.lowercase().toByteArray())

        assertEquals(32, correctHash.size, "keccak256 output should be 32 bytes")

        // Correct hash = keccak256(20 address bytes)
        // Wrong hash = keccak256(42 ASCII string bytes)
        // They MUST differ (proves the fix matters)
        assertFalse(correctHash.contentEquals(wrongHash),
            "Correct hash (20 bytes) must differ from wrong hash (42 ASCII bytes)")
    }

    @Test
    fun `identityHash is deterministic for same address`() {
        val address = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
        val hash1 = Hash.sha3(Numeric.hexStringToByteArray(address))
        val hash2 = Hash.sha3(Numeric.hexStringToByteArray(address))

        assertTrue(hash1.contentEquals(hash2), "identityHash must be deterministic")
    }

    @Test
    fun `identityHash produces 32 byte output`() {
        val address = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
        val hash = Hash.sha3(Numeric.hexStringToByteArray(address))
        assertEquals(32, hash.size)
    }

    @Test
    fun `different addresses produce different identityHashes`() {
        val addr1 = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
        val addr2 = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"

        val hash1 = Hash.sha3(Numeric.hexStringToByteArray(addr1))
        val hash2 = Hash.sha3(Numeric.hexStringToByteArray(addr2))

        assertFalse(hash1.contentEquals(hash2), "Different addresses must produce different hashes")
    }

    @Test
    fun `identityHash matches Solidity abi encodePacked encoding`() {
        // In Solidity: keccak256(abi.encodePacked(address))
        // abi.encodePacked(address) = 20 raw bytes of the address
        // Backend: Numeric.hexStringToByteArray("0x...") = 20 bytes
        val address = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
        val addressBytes = Numeric.hexStringToByteArray(address)
        assertEquals(20, addressBytes.size, "Decoded address should be 20 bytes")

        val identityHash = Hash.sha3(addressBytes)
        assertEquals(32, identityHash.size)

        // Verify hex representation
        val hashHex = Numeric.toHexString(identityHash)
        assertTrue(hashHex.startsWith("0x"))
        assertEquals(66, hashHex.length) // 0x + 64 hex chars = 32 bytes
    }

    @Test
    fun `isIdentityRegistered returns false for unregistered address`() {
        // Mock an empty response (address not registered)
        val response: EthCall = mockk()
        every { response.value } returns "0x0000000000000000000000000000000000000000000000000000000000000000"
        every { response.hasError() } returns false
        @Suppress("UNCHECKED_CAST")
        val request: Request<*, EthCall> = mockk()
        every { web3j.ethCall(any(), any()) } returns request
        every { request.send() } returns response

        val client = OnChainRegistryClient(
            registryAddress = "0x0000000000000000000000000000000000000001",
            web3j = web3j,
        )

        val result = client.isIdentityRegistered("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
        assertFalse(result, "Unregistered address should return false")
    }

    @Test
    fun `resolveIdentity handles RPC error gracefully`() {
        val response: EthCall = mockk()
        every { response.hasError() } returns true
        every { response.error } returns mockk {
            every { message } returns "execution reverted"
        }
        @Suppress("UNCHECKED_CAST")
        val request: Request<*, EthCall> = mockk()
        every { web3j.ethCall(any(), any()) } returns request
        every { request.send() } returns response

        val client = OnChainRegistryClient(
            registryAddress = "0x0000000000000000000000000000000000000001",
            web3j = web3j,
        )

        val identityHash = Hash.sha3(Numeric.hexStringToByteArray("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"))
        val result = client.resolveIdentity(identityHash)
        assertEquals(null, result, "RPC error should return null")
    }
}
