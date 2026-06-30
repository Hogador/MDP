package com.mdaopay.paymaster

import org.web3j.utils.Numeric
import java.math.BigInteger

data class AppConfig(
    val port: Int = 8080,
    val rpcUrls: List<String>,
    val privateKey: String,
    val paymasterAddress: String,
    val mdaoAddress: String,
    val usdtAddress: String,
    val entryPoint: String,
    val wbnbAddress: String,
    val expectedChainId: Long,
    val redisUrl: String,
    val databaseUrl: String? = null,
    val metricsToken: String? = null,
    val nicknameRegistryAddress: String? = null,
    val recoveryModuleAddress: String? = null,
    val watchtowerWebhookUrl: String? = null,
    val watchtowerPollIntervalSec: Long = 60,
    val moonpayApiKey: String? = null,
    val moonpaySecretKey: String? = null,
    val swapRouterAddress: String? = null,
    val apiKey: String? = null,
    val etherscanApiKey: String? = null,
    val jwtSecret: String,
    // C-1: EIP-712 quote verification signer
    val trustedSigner: String,
    // Q4: explicit testnet flag
    val isTestnet: Boolean,
    // Q2: relay HMAC transport auth
    val relaySecret: String,
    // F-035: separate key for swap operations (falls back to privateKey)
    val swapPrivateKey: String,
    // F-111: dev-only flag; production must use KMS
    val allowLocalSigning: Boolean = false,
) {
    init {
        // F-110: enforce Base64-encoded 256-bit key (44 chars minimum)
        require(jwtSecret.length >= 44) { "JWT_SECRET must be Base64-encoded 256-bit key (min 44 chars)" }
        // F-111: production guard — KMS/HSM required on mainnet
        if (!isTestnet && allowLocalSigning) {
            throw IllegalStateException("ALLOW_LOCAL_SIGNING is forbidden in production. Use KMS or HSM.")
        }
    }
    companion object {
        private val ADDRESS_REGEX = Regex("^0x[a-fA-F0-9]{40}$")
        private val PRIVATE_KEY_REGEX = Regex("^(0x)?[a-fA-F0-9]{64}$")
        private val URL_REGEX = Regex("^https?://.+")

        fun fromEnv(): AppConfig {
            val env = System.getenv()
            val port = env["PORT"]?.toIntOrNull() ?: 8080

            val rpcUrls = (env["RPC_URLS"] ?: env["RPC_URL"])
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: error("RPC_URLS or RPC_URL required")
            // C-12: validate RPC URLs are well-formed
            rpcUrls.forEach { url ->
                if (!URL_REGEX.matches(url)) error("Invalid RPC URL format: $url")
            }
            val privateKey = env["PAYMASTER_PRIVATE_KEY"] ?: error("PAYMASTER_PRIVATE_KEY required")
            val paymasterAddress = env["PAYMASTER_ADDRESS"] ?: error("PAYMASTER_ADDRESS required")
            val mdaoAddress = env["MDAO_ADDRESS"] ?: error("MDAO_ADDRESS required")
            val usdtAddress = env["USDT_ADDRESS"] ?: error("USDT_ADDRESS required")
            val wbnbAddress = env["WBNB_ADDRESS"] ?: error("WBNB_ADDRESS required")
            val entryPoint = env["ENTRY_POINT"] ?: "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789"
            val expectedChainId = env["EXPECTED_CHAIN_ID"]?.toLongOrNull()
                ?: error("EXPECTED_CHAIN_ID required")
            val redisUrl = env["REDIS_URL"] ?: error("REDIS_URL required")
            val databaseUrl = env["DATABASE_URL"]
            val nicknameRegistryAddress = env["NICKNAME_REGISTRY_ADDRESS"]
            val recoveryModuleAddress = env["RECOVERY_MODULE_ADDRESS"]
            val watchtowerWebhookUrl = env["WATCHTOWER_WEBHOOK_URL"]
            val watchtowerPollIntervalSec = env["WATCHTOWER_POLL_INTERVAL_SEC"]?.toLongOrNull() ?: 60
            val moonpayApiKey = env["MOONPAY_API_KEY"]
            val moonpaySecretKey = env["MOONPAY_SECRET_KEY"]
            val swapRouterAddress = env["SWAP_ROUTER_ADDRESS"]
            val metricsToken = env["METRICS_TOKEN"]
            val apiKey = env["API_KEY"]
            val etherscanApiKey = env["ETHERSCAN_API_KEY"]
            val jwtSecret = env["JWT_SECRET"] ?: error("JWT_SECRET is required")
            // F-110: Base64-encoded 256-bit key = 44 chars minimum
            if (jwtSecret.length < 44) error("JWT_SECRET must be Base64-encoded 256-bit key (min 44 chars)")

            val trustedSigner = env["TRUSTED_SIGNER"] ?: error("TRUSTED_SIGNER required")
            val isTestnet = env["IS_TESTNET"]?.toBooleanStrictOrNull()
                ?: (expectedChainId !in listOf(1L, 56L))
            val relaySecret = env["RELAY_SECRET"] ?: error("RELAY_SECRET required")
            if (relaySecret.length < 32) error("RELAY_SECRET must be at least 32 characters")

            val swapPrivateKey = env["SWAP_PRIVATE_KEY"] ?: error("SWAP_PRIVATE_KEY is required — do not reuse PAYMASTER_PRIVATE_KEY for swap operations")

            val allowLocalSigning = env["ALLOW_LOCAL_SIGNING"]?.toBooleanStrictOrNull() ?: false

            if (trustedSigner.isNotBlank() && !ADDRESS_REGEX.matches(trustedSigner)) {
                error("Invalid TRUSTED_SIGNER format: must be 0x-prefixed 40-char hex")
            }

            if (paymasterAddress.isNotBlank() && !ADDRESS_REGEX.matches(paymasterAddress)) {
                error("Invalid PAYMASTER_ADDRESS format: must be 0x-prefixed 40-char hex")
            }
            if (!ADDRESS_REGEX.matches(mdaoAddress)) {
                error("Invalid MDAO_ADDRESS format")
            }
            if (!ADDRESS_REGEX.matches(usdtAddress)) {
                error("Invalid USDT_ADDRESS format")
            }
            if (!ADDRESS_REGEX.matches(entryPoint)) {
                error("Invalid ENTRY_POINT format")
            }
            if (!ADDRESS_REGEX.matches(wbnbAddress)) {
                error("Invalid WBNB_ADDRESS format")
            }
            if (!PRIVATE_KEY_REGEX.matches(privateKey)) {
                error("Invalid PAYMASTER_PRIVATE_KEY format: must be 64-char hex (with or without 0x prefix)")
            }

            return AppConfig(
                port = port,
                rpcUrls = rpcUrls,
                privateKey = Numeric.cleanHexPrefix(privateKey),
                paymasterAddress = paymasterAddress,
                mdaoAddress = mdaoAddress,
                usdtAddress = usdtAddress,
                entryPoint = entryPoint,
                wbnbAddress = wbnbAddress,
                expectedChainId = expectedChainId,
                redisUrl = redisUrl,
                databaseUrl = databaseUrl,
                nicknameRegistryAddress = nicknameRegistryAddress,
                recoveryModuleAddress = recoveryModuleAddress,
                watchtowerWebhookUrl = watchtowerWebhookUrl,
                watchtowerPollIntervalSec = watchtowerPollIntervalSec,
                moonpayApiKey = moonpayApiKey,
                moonpaySecretKey = moonpaySecretKey,
                swapRouterAddress = swapRouterAddress,
                apiKey = apiKey,
                etherscanApiKey = etherscanApiKey,
                jwtSecret = jwtSecret,
                trustedSigner = trustedSigner,
                isTestnet = isTestnet,
                relaySecret = relaySecret,
                swapPrivateKey = Numeric.cleanHexPrefix(swapPrivateKey),
                allowLocalSigning = allowLocalSigning,
            )
        }
    }
}

fun String.fromHex(): ByteArray = Numeric.hexStringToByteArray(this)
fun ByteArray.toHex(): String = Numeric.toHexString(this)
fun String.hexToBigInt(): BigInteger = Numeric.toBigInt(this)
