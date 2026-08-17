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
    // H-06: token decimals — source of truth is the on-chain token (checked at deploy),
    // mirrored here for backend amount math. Must match contract's TokenConfig registry.
    val mdaoDecimals: Int = 18,
    val usdtDecimals: Int = 18,
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
    // C-3: SEPARATED SECRETS — JWT for auth, HMAC for internal transport
    // CRITICAL: These MUST be different values to prevent Confused Deputy attacks
    val relayJwtSecret: String,
    val relayHmacSecret: String,
    // F-035: separate key for swap operations (falls back to privateKey)
    val swapPrivateKey: String,
    // D-1 / F-129: AWS KMS key ID (key ID, ARN, or alias ARN) for production signing
    // F-134: GCP KMS does NOT support secp256k1 — AWS KMS with ECC_SECG_P256K1 is required
    val kmsKeyId: String? = null,
    // Backward compat: old GCP KMS_KEY_NAME (used as fallback if KMS_KEY_ID not set)
    val kmsKeyName: String? = null,
    // 0.7: deployed contract addresses for testnet
    val insuranceFundAddress: String? = null,
    val deadManSwitchAddress: String? = null,
    val refundVaultAddress: String? = null,
    val sessionKeyModuleAddress: String? = null,
    val attestationLedgerAddress: String? = null,
    val trustProviderRegistryAddress: String? = null,
    val ecdsaVerifierAddress: String? = null,
    val timelockAddress: String? = null,
    val p256VerifierAddress: String? = null,
    // F-104: Event indexer
    val treasuryAddress: String? = null,
    val proposalAddress: String? = null,
    val paymentSplitterFactoryAddress: String? = null,
    val indexerPollIntervalSec: Long = 30,
) {
    // ponytail: JWT validation at construction (no security bypass — config hygiene)
    init {
        val jwtBytes = try { java.util.Base64.getDecoder().decode(jwtSecret) }
            catch (_: IllegalArgumentException) { throw IllegalArgumentException("JWT_SECRET must be valid Base64") }
        require(jwtBytes.size >= 32) { "JWT_SECRET must decode to at least 32 bytes (256-bit key)" }
        if (jwtBytes.distinct().size <= 4) throw IllegalArgumentException("JWT_SECRET has insufficient entropy (unique bytes <= 4)")
        // H-06: decimals must match the contract's TokenConfig registry (6/8/18) — fail fast here.
        val supportedDecimals = setOf(6, 8, 18)
        require(mdaoDecimals in supportedDecimals) {
            "Invalid MDAO_DECIMALS=$mdaoDecimals: must be one of ${supportedDecimals.sorted()}"
        }
        require(usdtDecimals in supportedDecimals) {
            "Invalid USDT_DECIMALS=$usdtDecimals: must be one of ${supportedDecimals.sorted()}"
        }

        // C-3: Validate separated secrets
        require(relayJwtSecret.isNotEmpty()) { "RELAY_JWT_SECRET is required" }
        require(relayHmacSecret.isNotEmpty()) { "RELAY_HMAC_SECRET is required" }
        require(relayJwtSecret != relayHmacSecret) {
            "CRITICAL SECURITY VIOLATION: RELAY_JWT_SECRET and RELAY_HMAC_SECRET must be different values. " +
            "Using the same secret for both purposes allows Confused Deputy attacks."
        }
        require(relayJwtSecret.length >= 32) { "RELAY_JWT_SECRET must be at least 32 characters" }
        require(relayHmacSecret.length >= 64) { "RELAY_HMAC_SECRET must be at least 64 characters for HMAC-SHA256" }
    }

    // F-111: runtime guard — fires on access, not on construction
    // (init-block can be bypassed via try-catch)
    var allowLocalSigning: Boolean = false
        get() {
            if (!isTestnet && field) {
                throw IllegalStateException("ALLOW_LOCAL_SIGNING is forbidden in production. Use KMS or HSM.")
            }
            return field
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
            val mdaoDecimals = env["MDAO_DECIMALS"]?.toIntOrNull() ?: 18
            val usdtDecimals = env["USDT_DECIMALS"]?.toIntOrNull() ?: 18
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

            // ponytail: trustedSigner is validated but never consumed by any service (audit E)
            val trustedSigner = env["TRUSTED_SIGNER"] ?: error("TRUSTED_SIGNER required")
            val isTestnet = env["IS_TESTNET"]?.toBooleanStrictOrNull()
                ?: (expectedChainId !in listOf(1L, 56L))
            
            // C-3: SEPARATED SECRETS — Legacy RELAY_SECRET deprecated
            val relayJwtSecret = env["RELAY_JWT_SECRET"] 
                ?: env["RELAY_SECRET"]?.let { old ->
                    error(
                        "CRITICAL: RELAY_SECRET is deprecated. You must set both:\n" +
                        "  RELAY_JWT_SECRET=<32+ char random string for JWT auth>\n" +
                        "  RELAY_HMAC_SECRET=<64+ char random string for HMAC signatures>\n" +
                        "These MUST be different values. Do NOT reuse the same secret."    
                    )
                }
                ?: error("RELAY_JWT_SECRET required (or legacy RELAY_SECRET, but migration is mandatory)")
            
            val relayHmacSecret = env["RELAY_HMAC_SECRET"]
                ?: env["RELAY_SECRET"]?.let { old ->
                    error(
                        "CRITICAL: RELAY_SECRET is deprecated. You must set both:\n" +
                        "  RELAY_JWT_SECRET=<32+ char random string for JWT auth>\n" +
                        "  RELAY_HMAC_SECRET=<64+ char random string for HMAC signatures>\n" +
                        "These MUST be different values. Do NOT reuse the same secret."    
                    )
                }
                ?: error("RELAY_HMAC_SECRET required (or legacy RELAY_SECRET, but migration is mandatory)")
            
            // Validate lengths early before AppConfig constructor
            require(relayJwtSecret.length >= 32) { "RELAY_JWT_SECRET must be at least 32 characters" }
            require(relayHmacSecret.length >= 64) { "RELAY_HMAC_SECRET must be at least 64 characters" }
            require(relayJwtSecret != relayHmacSecret) {
                "CRITICAL SECURITY VIOLATION: RELAY_JWT_SECRET and RELAY_HMAC_SECRET must be different values."    
            }

            val swapPrivateKey = env["SWAP_PRIVATE_KEY"] ?: error("SWAP_PRIVATE_KEY is required — do not reuse PAYMASTER_PRIVATE_KEY for swap operations")

            val kmsKeyId = env["KMS_KEY_ID"] ?: env["KMS_KEY_NAME"] // fallback to old name
            val kmsKeyName = env["KMS_KEY_NAME"]

            val insuranceFundAddress = env["INSURANCE_FUND_ADDRESS"]
            val deadManSwitchAddress = env["DEAD_MAN_SWITCH_ADDRESS"]
            val refundVaultAddress = env["REFUND_VAULT_ADDRESS"]
            val sessionKeyModuleAddress = env["SESSION_KEY_MODULE_ADDRESS"]
            val attestationLedgerAddress = env["ATTESTATION_LEDGER_ADDRESS"]
            val trustProviderRegistryAddress = env["TRUST_PROVIDER_REGISTRY_ADDRESS"]
            val ecdsaVerifierAddress = env["ECDSA_VERIFIER_ADDRESS"]
            val timelockAddress = env["TIMELOCK_ADDRESS"]
            val p256VerifierAddress = env["P256_VERIFIER_ADDRESS"]
            val treasuryAddress = env["TREASURY_ADDRESS"]
            val proposalAddress = env["PROPOSAL_ADDRESS"]
            val paymentSplitterFactoryAddress = env["PAYMENT_SPLITTER_FACTORY_ADDRESS"]
            val indexerPollIntervalSec = env["INDEXER_POLL_INTERVAL_SEC"]?.toLongOrNull() ?: 30

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

            listOfNotNull(
                insuranceFundAddress, deadManSwitchAddress, refundVaultAddress,
                sessionKeyModuleAddress, attestationLedgerAddress, trustProviderRegistryAddress,
                ecdsaVerifierAddress, timelockAddress, p256VerifierAddress,
                treasuryAddress, proposalAddress, paymentSplitterFactoryAddress,
            ).forEach { addr ->
                if (!ADDRESS_REGEX.matches(addr)) {
                    error("Invalid contract address format: $addr (must be 0x-prefixed 40-char hex)")
                }
            }

            val cfg = AppConfig(
                port = port,
                rpcUrls = rpcUrls,
                privateKey = Numeric.cleanHexPrefix(privateKey),
                paymasterAddress = paymasterAddress,
                mdaoAddress = mdaoAddress,
                usdtAddress = usdtAddress,
                mdaoDecimals = mdaoDecimals,
                usdtDecimals = usdtDecimals,
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
                relayJwtSecret = relayJwtSecret,
                relayHmacSecret = relayHmacSecret,
                swapPrivateKey = Numeric.cleanHexPrefix(swapPrivateKey),
                kmsKeyId = kmsKeyId,
                kmsKeyName = kmsKeyName,
                insuranceFundAddress = insuranceFundAddress,
                deadManSwitchAddress = deadManSwitchAddress,
                refundVaultAddress = refundVaultAddress,
                sessionKeyModuleAddress = sessionKeyModuleAddress,
                attestationLedgerAddress = attestationLedgerAddress,
                trustProviderRegistryAddress = trustProviderRegistryAddress,
                ecdsaVerifierAddress = ecdsaVerifierAddress,
                timelockAddress = timelockAddress,
                p256VerifierAddress = p256VerifierAddress,
                treasuryAddress = treasuryAddress,
                proposalAddress = proposalAddress,
                paymentSplitterFactoryAddress = paymentSplitterFactoryAddress,
                indexerPollIntervalSec = indexerPollIntervalSec,
            )
            cfg.allowLocalSigning = env["ALLOW_LOCAL_SIGNING"]?.toBooleanStrictOrNull() ?: false
            if ((kmsKeyId != null || kmsKeyName != null) && cfg.allowLocalSigning) {
                throw IllegalStateException("KMS_KEY_ID/KMS_KEY_NAME and ALLOW_LOCAL_SIGNING cannot both be set. Choose one.")
            }
            return cfg
        }
    }
}

fun String.fromHex(): ByteArray = Numeric.hexStringToByteArray(this)
fun ByteArray.toHex(): String = Numeric.toHexString(this)
fun String.hexToBigInt(): BigInteger = Numeric.toBigInt(this)
