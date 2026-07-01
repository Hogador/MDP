package com.mdaopay.paymaster

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.crypto.ECDSASignature
import org.web3j.crypto.Sign
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.protocol.core.methods.response.EthChainId
import org.web3j.protocol.core.methods.response.EthGetTransactionCount
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.util.concurrent.CompletableFuture

class PaymasterServiceTest {

    private val config = AppConfig(
        rpcUrls = listOf("http://localhost:8545"),
        privateKey = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80", // ponytail: Anvil #0 test key — not for production
        paymasterAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
        mdaoAddress = "0x0000000000000000000000000000000000000001",
        usdtAddress = "0x0000000000000000000000000000000000000002",
        entryPoint = "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789",
        wbnbAddress = "0x0000000000000000000000000000000000000003",
        expectedChainId = 56L,
        redisUrl = "redis://localhost:6379",
        jwtSecret = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        trustedSigner = "0x0000000000000000000000000000000000000000",
        isTestnet = true,
        relaySecret = "test-relay-secret-at-least-32-chars!!",
        swapPrivateKey = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
    )
    init { config.allowLocalSigning = true } // testnet — allowed

    private val web3j: Web3j = mockk()
    private val rpcManager: RpcProviderManager = mockk()
    private val priceOracle: PriceOracle = mockk()
    private val key: ECKeyPair = ECKeyPair.create(Numeric.hexStringToByteArray(config.privateKey))
    private val signer: PaymasterSigner = LocalPaymasterSigner(key)
    private val service = PaymasterService(config, rpcManager, signer, priceOracle)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun mockChainId(chainId: Long) {
        val response: EthChainId = mockk()
        every { response.chainId } returns BigInteger.valueOf(chainId)
        @Suppress("UNCHECKED_CAST")
        val request: Request<*, EthChainId> = mockk()
        every { web3j.ethChainId() } returns request
        every { request.sendAsync() } returns CompletableFuture.completedFuture(response)
    }

    private fun mockNonce(onChainNonce: Long) {
        val response: EthGetTransactionCount = mockk()
        every { response.transactionCount } returns BigInteger.valueOf(onChainNonce)
        @Suppress("UNCHECKED_CAST")
        val request: Request<*, EthGetTransactionCount> = mockk()
        every { web3j.ethGetTransactionCount(any(), any()) } returns request
        every { request.sendAsync() } returns CompletableFuture.completedFuture(response)
    }

    private fun mockBalances(balance: BigInteger = BigInteger.TEN.pow(24)) {
        val response: EthCall = mockk()
        every { response.value } returns abiEncodeUint256(balance)
        @Suppress("UNCHECKED_CAST")
        val request: Request<*, EthCall> = mockk()
        every { web3j.ethCall(any(), any()) } returns request
        every { request.sendAsync() } returns CompletableFuture.completedFuture(response)
    }

    // F-034: mock quoteNonces(sender) → nonce
    private fun mockQuoteNonce(nonce: BigInteger) {
        val selector = Hash.sha3String("quoteNonces(address)").substring(0, 10)
        val response: EthCall = mockk()
        every { response.value } returns abiEncodeUint256(nonce)
        @Suppress("UNCHECKED_CAST")
        val request: Request<*, EthCall> = mockk()
        every { web3j.ethCall(match { it.data.startsWith(selector) }, any()) } returns request
        every { request.sendAsync() } returns CompletableFuture.completedFuture(response)
    }

    private fun abiEncodeUint256(value: BigInteger): String {
        return "0x" + value.toString(16).padStart(64, '0')
    }

    private fun baseSignRequest() = SignRequest(
        sender = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
        nonce = "0x0b",
        callData = "0x",
        verificationGasLimit = "0x100000",
        callGasLimit = "0x100000",
        preVerificationGas = "0x10000",
        maxPriorityFeePerGas = "0x59682f00",
        maxFeePerGas = "0x59682f00",
    )

    @Test
    fun `sign returns valid paymasterAndData on happy path`() = runTest {
        mockChainId(56L)
        mockNonce(10L)
        mockBalances()
        every { rpcManager.getBestProvider() } returns Result.success(web3j)
        coEvery { priceOracle.getPrices() } returns DexPrices(600.0, 0.001, 1.0)

        val req = baseSignRequest().copy(
            mdaoMaxAmount = "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
        )

        val result = service.sign(req)
        assertTrue(result.paymasterAndData.startsWith("0x"))
        assertTrue(result.paymasterAndData.length > 10)
        assertTrue(result.userOpHash.isNotBlank())
        assertTrue(result.maxFee.isNotBlank())
        assertTrue(result.token == config.mdaoAddress)
    }

    @Test
    fun `sign throws GasEstimationException on chain ID mismatch`() = runTest {
        mockChainId(1L)
        every { rpcManager.getBestProvider() } returns Result.success(web3j)

        val ex = assertThrows<GasEstimationException> {
            service.sign(baseSignRequest())
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Chain ID mismatch")) { "Expected 'Chain ID mismatch' in: $msg" }
    }

    @Test
    fun `sign throws GasEstimationException on nonce gap greater than 20`() = runTest {
        mockChainId(56L)
        mockNonce(10L)
        every { rpcManager.getBestProvider() } returns Result.success(web3j)

        val req = baseSignRequest().copy(nonce = "0x100")

        val ex = assertThrows<GasEstimationException> {
            service.sign(req)
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Nonce too far ahead")) { "Expected 'Nonce too far ahead' in: $msg" }
    }

    @Test
    fun `sign throws when price oracle fails`() = runTest {
        mockChainId(56L)
        mockNonce(10L)
        every { rpcManager.getBestProvider() } returns Result.success(web3j)
        coEvery { priceOracle.getPrices() } throws PriceOracleException("Rate limited by DEX API")

        assertThrows<PriceOracleException> {
            service.sign(baseSignRequest())
        }
    }

    @Test
    fun `sign throws GasEstimationException on insufficient balance`() = runTest {
        mockChainId(56L)
        mockNonce(10L)
        mockBalances()
        every { rpcManager.getBestProvider() } returns Result.success(web3j)
        coEvery { priceOracle.getPrices() } returns DexPrices(600.0, 0.001, 1.0)

        val req = baseSignRequest().copy(
            mdaoMaxAmount = null,
            usdtMaxAmount = null,
        )

        val ex = assertThrows<GasEstimationException> {
            service.sign(req)
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Cannot afford gas")) { "Expected 'Cannot afford gas' in: $msg" }
    }

    // F-034: verify EIP-712 Quote signature matches contract-side verification
    @Test
    fun `testQuoteSignedWithEIP712MatchesContractVerification`() = runTest {
        mockChainId(56L)
        mockNonce(10L)
        mockBalances()                        // generic: balanceOf / allowance
        mockQuoteNonce(BigInteger.ZERO)       // specific override: quoteNonces[sender] = 0
        every { rpcManager.getBestProvider() } returns Result.success(web3j)
        coEvery { priceOracle.getPrices() } returns DexPrices(600.0, 0.001, 1.0)

        val req = baseSignRequest().copy(
            mdaoMaxAmount = "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
        )
        val result = service.sign(req)

        // Parse paymasterAndData: pmAddr(20) + token(20) + amount(32) + deadline(32) + sig(65) + lenHex(2) + magic(8)
        val pm = result.paymasterAndData.removePrefix("0x")
        val magic = pm.substring(pm.length - 16)
        assertEquals("22e325a297439656", magic, "Suffix magic mismatch")

        // Extract raw signature (65 bytes = v(1) + r(32) + s(32)) before lenHex(4) + magic(16)
        val sigHex = pm.substring(pm.length - 130 - 4 - 16, pm.length - 4 - 16)
        val sigBytes = Numeric.hexStringToByteArray(sigHex)
        val v = sigBytes[0].toInt() and 0xFF
        val r = BigInteger(1, sigBytes.copyOfRange(1, 33))
        val s = BigInteger(1, sigBytes.copyOfRange(33, 65))

        // Extract quote fields: pmAddr is 20 bytes (40 hex), then token(20), amount(32), deadline(32)
        val tokenHex = pm.substring(40, 80)
        val amountHex = pm.substring(80, 144)
        val deadlineHex = pm.substring(144, 208)
        val token = Numeric.toBigInt("0x$tokenHex")
        val maxTokenAmount = Numeric.toBigInt(amountHex)
        val quoteDeadline = Numeric.toBigInt(deadlineHex)
        val maxGasPrice = req.maxFeePerGas.hexToBigInt()
        val chainId = 56L
        val verifyingContract = Numeric.toBigInt(config.paymasterAddress)
        val nonce = BigInteger.ZERO

        // Recompute EIP-712 digest exactly as contract does in _verifyQuoteSignature
        val QUOTE_TYPEHASH = Hash.sha3(
            "Quote(address sender,address token,uint256 maxTokenAmount,uint256 maxGasPrice,uint256 quoteDeadline,uint256 nonce)".toByteArray()
        )
        val EIP712_DOMAIN_TYPEHASH = Hash.sha3(
            "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)".toByteArray()
        )
        val nameHash = Hash.sha3("MDAOPay".toByteArray())
        val versionHash = Hash.sha3("1".toByteArray())

        val domainSeparator = Hash.sha3(
            EIP712_DOMAIN_TYPEHASH +
            nameHash + versionHash +
            Numeric.toBytesPadded(BigInteger.valueOf(chainId), 32) +
            Numeric.toBytesPadded(verifyingContract, 32)
        )
        val structHash = Hash.sha3(
            QUOTE_TYPEHASH +
            Numeric.toBytesPadded(Numeric.toBigInt(req.sender), 32) +
            Numeric.toBytesPadded(token, 32) +
            Numeric.toBytesPadded(maxTokenAmount, 32) +
            Numeric.toBytesPadded(maxGasPrice, 32) +
            Numeric.toBytesPadded(quoteDeadline, 32) +
            Numeric.toBytesPadded(nonce, 32)
        )
        val digest = Hash.sha3(byteArrayOf(0x19, 0x01) + domainSeparator + structHash)

        // Recover signer from EIP-712 digest + signature
        val recId = v - 27
        val recoveredKey = Sign.recoverFromSignature(recId, ECDSASignature(r, s), digest)
        val recoveredAddress = "0x" + Keys.getAddress(recoveredKey)

        val expectedAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
        assertEquals(expectedAddress.lowercase(), recoveredAddress.lowercase(), "EIP-712 signature must recover to trusted signer")
    }

    // F-022: Verify that LocalPaymasterSigner.signDigest + Sign.recoverFromSignature are consistent
    @Test
    fun `testSignAndVerifyHashConsistency`() {
        val digest = Hash.sha3("test data for hash consistency check".toByteArray())
        val (v, r, s) = signer.signDigest(digest)
        val recoveredKey = Sign.recoverFromSignature(v.toInt() - 27, ECDSASignature(BigInteger(1, r), BigInteger(1, s)), digest)
        assertEquals(key.publicKey, recoveredKey, "Sign.recoverFromSignature should return the original public key")
    }
}
