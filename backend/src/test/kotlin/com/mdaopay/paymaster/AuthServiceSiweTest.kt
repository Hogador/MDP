package com.mdaopay.paymaster

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.time.Instant
import kotlin.test.*

/**
 * Тесты для SIWE (EIP-4361) функциональности в AuthService.
 */
class AuthServiceSiweTest {

    private val mockRepo = mockk<AuthRepository>()
    private val authService = AuthService(mockRepo, "test-jwt-secret")

    // ponytail: Anvil #0 test key — same as NicknameServiceTest
    private val privateKeyHex = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    private val keyPair = ECKeyPair.create(Numeric.hexStringToByteArray(privateKeyHex))
    private val walletAddress = Keys.toChecksumAddress("0x" + Keys.getAddress(keyPair)).lowercase()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ── parseSiweMessage tests ──

    @Test
    fun `parseSiweMessage parses full message with all fields`() {
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine("0x1234567890123456789012345678901234567890")
            appendLine()
            appendLine("Sign in to example.com")
            appendLine()
            appendLine("URI: https://example.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 1")
            appendLine("Nonce: abc123")
            appendLine("Issued At: 2024-01-01T00:00:00Z")
            appendLine("Expiration Time: 2025-01-01T00:00:00Z")
            appendLine("Request ID: req-456")
        }.trimEnd()

        val parsed = authService.parseSiweMessage(message)
        assertNotNull(parsed, "Should parse a valid full SIWE message")
        assertEquals("example.com", parsed!!.domain)
        assertEquals("0x1234567890123456789012345678901234567890", parsed.address)
        assertEquals("Sign in to example.com", parsed.statement)
        assertEquals("https://example.com/login", parsed.uri)
        assertEquals("1", parsed.version)
        assertEquals(1L, parsed.chainId)
        assertEquals("abc123", parsed.nonce)
        assertEquals("2024-01-01T00:00:00Z", parsed.issuedAt)
        assertEquals("2025-01-01T00:00:00Z", parsed.expirationTime)
        assertEquals("req-456", parsed.requestId)
    }

    @Test
    fun `parseSiweMessage parses minimal message without statement`() {
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine("0x1234567890123456789012345678901234567890")
            appendLine()
            appendLine("URI: https://example.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 1")
            appendLine("Nonce: abc123")
            appendLine("Issued At: 2024-01-01T00:00:00Z")
        }.trimEnd()

        val parsed = authService.parseSiweMessage(message)
        assertNotNull(parsed)
        assertEquals("example.com", parsed!!.domain)
        assertEquals("0x1234567890123456789012345678901234567890", parsed.address)
        assertNull(parsed.statement)
        assertEquals("abc123", parsed.nonce)
    }

    @Test
    fun `parseSiweMessage parses message with multi-line statement`() {
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine("0x1234567890123456789012345678901234567890")
            appendLine()
            appendLine("Line one of statement")
            appendLine("Line two of statement")
            appendLine()
            appendLine("URI: https://example.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 1")
            appendLine("Nonce: abc123")
            appendLine("Issued At: 2024-01-01T00:00:00Z")
        }.trimEnd()

        val parsed = authService.parseSiweMessage(message)
        assertNotNull(parsed)
        assertEquals("Line one of statement\nLine two of statement", parsed!!.statement)
    }

    @Test
    fun `parseSiweMessage returns null for empty message`() {
        assertNull(authService.parseSiweMessage(""))
    }

    @Test
    fun `parseSiweMessage returns null for malformed message without domain prefix`() {
        assertNull(authService.parseSiweMessage("garbage data\n0x1234\n\nNonce: abc"))
    }

    @Test
    fun `parseSiweMessage returns null when missing nonce`() {
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine("0x1234567890123456789012345678901234567890")
            appendLine()
            appendLine("URI: https://example.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 1")
            appendLine("Issued At: 2024-01-01T00:00:00Z")
        }.trimEnd()

        assertNull(authService.parseSiweMessage(message))
    }

    @Test
    fun `parseSiweMessage with statement containing colons does not confuse parser`() {
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine("0x1234567890123456789012345678901234567890")
            appendLine()
            appendLine("Note: this URI is http://example.com not URI: field")
            appendLine()
            appendLine("URI: https://example.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 1")
            appendLine("Nonce: abc123")
            appendLine("Issued At: 2024-01-01T00:00:00Z")
        }.trimEnd()

        val parsed = authService.parseSiweMessage(message)
        assertNotNull(parsed)
        assertEquals("Note: this URI is http://example.com not URI: field", parsed!!.statement)
        assertEquals("https://example.com/login", parsed.uri)
    }

    // ── recoverEthereumSigner tests ──

    @Test
    fun `recoverEthereumSigner recovers correct address from valid signature`() {
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine(walletAddress)
            appendLine()
            appendLine("URI: https://example.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 1")
            appendLine("Nonce: test-nonce-123")
            appendLine("Issued At: 2024-01-01T00:00:00Z")
        }.trimEnd()

        val signature = createSiweSignature(message, keyPair)
        val recovered = authService.recoverEthereumSigner(message, signature)

        assertNotNull(recovered, "Should recover address from valid signature")
        assertEquals(walletAddress, recovered)
    }

    @Test
    fun `recoverEthereumSigner returns null for invalid signature`() {
        val message = "test message"
        val result = authService.recoverEthereumSigner(message, "0xdead")
        assertNull(result)
    }

    @Test
    fun `recoverEthereumSigner returns null for empty signature`() {
        val result = authService.recoverEthereumSigner("test", "")
        assertNull(result)
    }

    @Test
    fun `recoverEthereumSigner accepts signature without 0x prefix`() {
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine(walletAddress)
            appendLine()
            appendLine("Nonce: test-nonce-no-prefix")
            appendLine("URI: https://example.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 1")
            appendLine("Issued At: 2024-01-01T00:00:00Z")
        }.trimEnd()

        val sigWithPrefix = createSiweSignature(message, keyPair)
        val sigWithoutPrefix = sigWithPrefix.removePrefix("0x")

        val recovered = authService.recoverEthereumSigner(message, sigWithoutPrefix)
        assertNotNull(recovered)
        assertEquals(walletAddress, recovered)
    }

    @Test
    fun `recoverEthereumSigner recovers address with v in 0-1 range`() {
        val message = "test v normalization"
        // Create a signature with v normalized to 0/1 (manually)
        val sigData = Sign.signPrefixedMessage(message.encodeToByteArray(), keyPair)
        // web3j returns v as byte[], first byte is the v value (27 or 28)
        val originalV = sigData.v[0].toInt().let { if (it < 0) it + 256 else it }
        val v0 = (originalV - 27).toByte()
        val modifiedSigData = Sign.SignatureData(byteArrayOf(v0), sigData.r, sigData.s)
        val combined = ByteArray(65).apply {
            modifiedSigData.r.copyInto(this, 0)
            modifiedSigData.s.copyInto(this, 32)
            modifiedSigData.v.copyInto(this, 64)
        }
        val signature = Numeric.toHexString(combined)

        val recovered = authService.recoverEthereumSigner(message, signature)
        assertNotNull(recovered, "Should recover with v in 0-1 range")
        assertEquals(Keys.toChecksumAddress("0x" + Keys.getAddress(keyPair)).lowercase(), recovered)
    }

    // ── generateNonce tests ──

    @Test
    fun `generateNonce stores nonce and returns it`() {
        val walletAddress = "0x1234567890123456789012345678901234567890"
        val slotNonce = slot<String>()
        val slotWallet = slot<String>()
        val slotExpires = slot<Instant>()

        every { mockRepo.storeSiweNonce(capture(slotNonce), capture(slotWallet), capture(slotExpires)) } returns Unit

        val nonce = authService.generateNonce(walletAddress)

        assertTrue(nonce.isNotBlank(), "Nonce should not be blank")
        assertTrue(nonce.length >= 16, "Nonce should be at least 16 chars")
        assertEquals(walletAddress, slotWallet.captured)
        assertTrue(slotExpires.captured.isAfter(Instant.now()), "Expiry should be in the future")
    }

    // ── verifySiwe tests ──

    @Test
    fun `verifySiwe succeeds with valid message and signature`() {
        val nonce = "verify-siwe-nonce-001"
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine(walletAddress)
            appendLine()
            appendLine("URI: https://example.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 1")
            appendLine("Nonce: $nonce")
            appendLine("Issued At: 2024-01-01T00:00:00Z")
        }.trimEnd()

        val signature = createSiweSignature(message, keyPair)

        // Mock nonce lookup
        every { mockRepo.findAndDeleteSiweNonce(nonce) } returns walletAddress
        // Mock wallet user — not found, then create
        every { mockRepo.findByEmail(walletAddress) } returns null
        every { mockRepo.create(any(), any(), any()) } returns AuthUser(
            id = "siwe-user-id",
            email = walletAddress,
            passwordHash = "",
            passwordSalt = "",
            createdAt = System.currentTimeMillis(),
        )
        every { mockRepo.storeRefreshToken(any(), any(), any()) } returns Unit

        val result = authService.verifySiwe(message, signature)
        assertTrue(result.isSuccess, "verifySiwe should succeed: ${result.exceptionOrNull()?.message}")
        val tokens = result.getOrThrow()
        assertTrue(tokens.accessToken.isNotBlank())
        assertTrue(tokens.refreshToken.isNotBlank())
        assertTrue(tokens.expiresIn > 0)
    }

    @Test
    fun `verifySiwe fails with invalid nonce`() {
        val nonce = "nonexistent-nonce"
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine(walletAddress)
            appendLine()
            appendLine("Nonce: $nonce")
            appendLine("URI: https://example.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 1")
            appendLine("Issued At: 2024-01-01T00:00:00Z")
        }.trimEnd()

        val signature = createSiweSignature(message, keyPair)

        every { mockRepo.findAndDeleteSiweNonce(nonce) } returns null

        val result = authService.verifySiwe(message, signature)
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message!!, "nonce", ignoreCase = true)
    }

    @Test
    fun `verifySiwe fails when signer does not match message address`() {
        val nonce = "wrong-address-nonce"
        // Message has a different address than the signer
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            appendLine()
            appendLine("Nonce: $nonce")
            appendLine("URI: https://example.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 1")
            appendLine("Issued At: 2024-01-01T00:00:00Z")
        }.trimEnd()

        val signature = createSiweSignature(message, keyPair)

        every { mockRepo.findAndDeleteSiweNonce(nonce) } returns walletAddress

        val result = authService.verifySiwe(message, signature)
        assertTrue(result.isFailure)
    }

    @Test
    fun `verifySiwe fails with malformed SIWE message`() {
        val result = authService.verifySiwe("not a siwe message", "0xdeadbeef")
        assertTrue(result.isFailure)
    }

    // ── SIWE full integration flow ──

    @Test
    fun `parse then recover then verify full flow`() {
        val nonce = "integration-flow-nonce"
        val message = buildString {
            appendLine("example.com wants you to sign in with your Ethereum account:")
            appendLine(walletAddress)
            appendLine()
            appendLine("Sign in to use MDAOPay")
            appendLine()
            appendLine("URI: https://app.mdaopay.com/login")
            appendLine("Version: 1")
            appendLine("Chain ID: 56")
            appendLine("Nonce: $nonce")
            appendLine("Issued At: 2024-06-15T12:00:00Z")
        }.trimEnd()

        // 1. Parse
        val parsed = authService.parseSiweMessage(message)
        assertNotNull(parsed)
        assertEquals("example.com", parsed!!.domain)
        assertEquals(walletAddress, parsed.address.lowercase())
        assertEquals("Sign in to use MDAOPay", parsed.statement)
        assertEquals("https://app.mdaopay.com/login", parsed.uri)
        assertEquals(56L, parsed.chainId)
        assertEquals(nonce, parsed.nonce)

        // 2. Sign and recover
        val signature = createSiweSignature(message, keyPair)
        val recovered = authService.recoverEthereumSigner(message, signature)
        assertNotNull(recovered)
        assertEquals(walletAddress, recovered)
        assertEquals(parsed.address.lowercase(), recovered)

        // 3. Verify (mock repo)
        every { mockRepo.findAndDeleteSiweNonce(nonce) } returns walletAddress
        every { mockRepo.findByEmail(walletAddress) } returns null
        every { mockRepo.create(any(), any(), any()) } returns AuthUser(
            id = "integrated-user",
            email = walletAddress,
            passwordHash = "",
            passwordSalt = "",
            createdAt = System.currentTimeMillis(),
        )
        every { mockRepo.storeRefreshToken(any(), any(), any()) } returns Unit

        val result = authService.verifySiwe(message, signature)
        assertTrue(result.isSuccess)
    }

    // ── Helpers ──

    private fun createSiweSignature(message: String, keyPair: ECKeyPair): String {
        val sigData = Sign.signPrefixedMessage(message.encodeToByteArray(), keyPair)
        val combined = ByteArray(65).apply {
            sigData.r.copyInto(this, 0)
            sigData.s.copyInto(this, 32)
            sigData.v.copyInto(this, 64)
        }
        // Ensure v is 27 or 28 for the canonical signature
        return Numeric.toHexString(combined)
    }
}
