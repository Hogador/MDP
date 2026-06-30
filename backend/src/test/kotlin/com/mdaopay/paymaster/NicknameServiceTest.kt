package com.mdaopay.paymaster

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
import kotlin.test.assertContains
import kotlin.test.assertTrue

class NicknameServiceTest {

    private val privateKeyHex = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80" // ponytail: Anvil #0 test key — not for production
    private val keyPair = ECKeyPair.create(Numeric.hexStringToByteArray(privateKeyHex))
    private val paymasterAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `valid nickname registration succeeds`() {
        mockkObject(Redis)
        // F-019: Mock Redis SETNX lock + release
        coEvery { Redis.setNx(any<String>(), any()) } returns true
        coEvery { Redis.expire(any<String>(), any()) } returns true
        coEvery { Redis.del(any<String>()) } returns true
        coEvery { Redis.hSet(any<String>(), any<String>(), any()) } returns true

        NicknameService.repo = null
        NicknameService.registryClient = null

        val nickname = "test_" + System.nanoTime().toString().takeLast(6)
        val nonce = System.currentTimeMillis()
        val signature = createNicknameSignature(nickname, paymasterAddress, nonce, keyPair)

        val result = NicknameService.register(nickname, paymasterAddress, signature, nonce)
        assertTrue(result.isSuccess, "register failed: ${result.exceptionOrNull()?.message}")
        val entry = result.getOrThrow()
        assertTrue(entry.nickname == nickname.lowercase())
        assertTrue(entry.address == paymasterAddress.lowercase())
    }

    @Test
    fun `too short nickname returns error`() {
        val result = NicknameService.register("ab", paymasterAddress, "0x", 0L)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertContains(error!!.message!!, "too short")
    }

    @Test
    fun `too long nickname returns error`() {
        val result = NicknameService.register(
            "a".repeat(21), paymasterAddress, "0x", 0L,
        )
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message!!, "too long")
    }

    @Test
    fun `invalid characters return error`() {
        val result = NicknameService.register("user@name", paymasterAddress, "0x", 0L)
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message!!, "invalid characters")
    }

    @Test
    fun `reserved name returns error`() {
        val result = NicknameService.register("admin", paymasterAddress, "0x", 0L)
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message!!, "reserved")
    }

    @Test
    fun `signature replay with same parameters fails`() {
        mockkObject(Redis)
        // F-019: First call acquires lock, second call fails on SETNX
        coEvery { Redis.setNx(any<String>(), any()) } returnsMany listOf(true, false)
        coEvery { Redis.expire(any<String>(), any()) } returns true
        coEvery { Redis.del(any<String>()) } returns true
        coEvery { Redis.hSet(any<String>(), any<String>(), any()) } returns true

        NicknameService.repo = null
        NicknameService.registryClient = null

        val replayKeyPair = Keys.createEcKeyPair()
        val replayAddress = Keys.toChecksumAddress("0x" + Keys.getAddress(replayKeyPair))
        val nickname = "replay_" + System.nanoTime().toString().takeLast(6)
        val nonce = System.currentTimeMillis()
        val signature = createNicknameSignature(nickname, replayAddress, nonce, replayKeyPair)

        val firstResult = NicknameService.register(nickname, replayAddress, signature, nonce)
        assertTrue(firstResult.isSuccess, "first register failed: ${firstResult.exceptionOrNull()?.message}")

        // Second call — SETNX returns false (lock already held)
        val secondResult = NicknameService.register(nickname, replayAddress, signature, nonce)
        assertTrue(secondResult.isFailure)
        assertContains(secondResult.exceptionOrNull()!!.message!!, "already taken")
    }

    @Test
    fun `cross instance race via SETNX returns error`() {
        mockkObject(Redis)
        // Simulate Redis SETNX returning false (lock held by another instance)
        coEvery { Redis.setNx(any<String>(), any()) } returns false

        NicknameService.repo = null
        NicknameService.registryClient = null

        val nickname = "racy_" + System.nanoTime().toString().takeLast(6)
        val nonce = System.currentTimeMillis()
        val signature = createNicknameSignature(nickname, paymasterAddress, nonce, keyPair)

        val result = NicknameService.register(nickname, paymasterAddress, signature, nonce)
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message!!, "already taken")
    }

    @Test
    fun `expired nonce returns error`() {
        val expiredNonce = System.currentTimeMillis() - 600_000L
        val result = NicknameService.register("newnick", paymasterAddress, "0x", expiredNonce)
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message!!, "Nonce expired")
    }

    private fun createNicknameSignature(nickname: String, address: String, nonce: Long, keyPair: ECKeyPair): String {
        val message = "Register nickname $nickname for ${address.lowercase()} (nonce: $nonce)"
        val sigData = Sign.signPrefixedMessage(message.encodeToByteArray(), keyPair)
        val combined = ByteArray(sigData.r.size + sigData.s.size + sigData.v.size).apply {
            sigData.r.copyInto(this, 0)
            sigData.s.copyInto(this, sigData.r.size)
            sigData.v.copyInto(this, sigData.r.size + sigData.s.size)
        }
        return Numeric.toHexString(combined)
    }
}
