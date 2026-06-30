package com.mdaopay.paymaster

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Regression tests for:
 * - F-110: JWT_SECRET entropy check (min 44 chars)
 * - F-111: ALLOW_LOCAL_SIGNING production guard
 */
class AppConfigValidationTest {

    private val validRpcUrls = listOf("http://localhost:8545")
    private val validPrivateKey = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    private val validAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
    private val validEntryPoint = "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789"
    private val validRedisUrl = "redis://localhost:6379"
    private val validJwtSecret = "test-jwt-secret-at-least-44-chars-base64-encoded-256-bit!!"
    private val validRelaySecret = "test-relay-secret-at-least-32-chars!!"

    @Test
    fun `F-110 rejects JWT_SECRET shorter than 44 chars`() {
        assertThrows<IllegalArgumentException> {
            AppConfig(
                rpcUrls = validRpcUrls,
                privateKey = validPrivateKey,
                paymasterAddress = validAddress,
                mdaoAddress = "0x0000000000000000000000000000000000000001",
                usdtAddress = "0x0000000000000000000000000000000000000002",
                entryPoint = validEntryPoint,
                wbnbAddress = "0x0000000000000000000000000000000000000003",
                expectedChainId = 56L,
                redisUrl = validRedisUrl,
                jwtSecret = "short",  // only 5 chars
                trustedSigner = validAddress,
                isTestnet = true,
                relaySecret = validRelaySecret,
                swapPrivateKey = validPrivateKey,
            )
        }
    }

    @Test
    fun `F-110 accepts JWT_SECRET with 44 or more chars`() {
        val config = AppConfig(
            rpcUrls = validRpcUrls,
            privateKey = validPrivateKey,
            paymasterAddress = validAddress,
            mdaoAddress = "0x0000000000000000000000000000000000000001",
            usdtAddress = "0x0000000000000000000000000000000000000002",
            entryPoint = validEntryPoint,
            wbnbAddress = "0x0000000000000000000000000000000000000003",
            expectedChainId = 56L,
            redisUrl = validRedisUrl,
            jwtSecret = validJwtSecret,
            trustedSigner = validAddress,
            isTestnet = true,
            relaySecret = validRelaySecret,
            swapPrivateKey = validPrivateKey,
        )
        assert(config.jwtSecret == validJwtSecret)
    }

    @Test
    fun `F-111 rejects ALLOW_LOCAL_SIGNING on production`() {
        assertThrows<IllegalStateException> {
            AppConfig(
                rpcUrls = validRpcUrls,
                privateKey = validPrivateKey,
                paymasterAddress = validAddress,
                mdaoAddress = "0x0000000000000000000000000000000000000001",
                usdtAddress = "0x0000000000000000000000000000000000000002",
                entryPoint = validEntryPoint,
                wbnbAddress = "0x0000000000000000000000000000000000000003",
                expectedChainId = 1L,  // mainnet
                redisUrl = validRedisUrl,
                jwtSecret = validJwtSecret,
                trustedSigner = validAddress,
                isTestnet = false,     // production
                relaySecret = validRelaySecret,
                swapPrivateKey = validPrivateKey,
                allowLocalSigning = true,  // forbidden in production
            )
        }
    }

    @Test
    fun `F-111 allows ALLOW_LOCAL_SIGNING on testnet`() {
        val config = AppConfig(
            rpcUrls = validRpcUrls,
            privateKey = validPrivateKey,
            paymasterAddress = validAddress,
            mdaoAddress = "0x0000000000000000000000000000000000000001",
            usdtAddress = "0x0000000000000000000000000000000000000002",
            entryPoint = validEntryPoint,
            wbnbAddress = "0x0000000000000000000000000000000000000003",
            expectedChainId = 97L,  // BSC testnet
            redisUrl = validRedisUrl,
            jwtSecret = validJwtSecret,
            trustedSigner = validAddress,
            isTestnet = true,
            relaySecret = validRelaySecret,
            swapPrivateKey = validPrivateKey,
            allowLocalSigning = true,
        )
        assert(config.allowLocalSigning)
    }
}
