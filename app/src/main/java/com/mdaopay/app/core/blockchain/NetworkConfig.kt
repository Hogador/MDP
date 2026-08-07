package com.mdaopay.app.core.blockchain

import com.mdaopay.app.BuildConfig

// ponytail: RPC_URL from BuildConfig — overridable per flavor.
// F-025 fix: single URL is for legacy use; RpcProviderManager uses 3 providers.
// CHAIN_ID: 97 (BSC Testnet) for dev/staging, 56 (BSC Mainnet) for prod.
object NetworkConfig {
    val RPC_URL: String get() = BuildConfig.RPC_URL_1
    val BUNDLER_URL: String get() = BuildConfig.BUNDLER_URL
    val CHAIN_ID: Long get() = BuildConfig.CHAIN_ID
    // H-08: per-flavor token contracts (was mainnet USDT hardcode — broke dev/staging chainId=97)
    val USDT_CONTRACT: String get() = BuildConfig.USDT_CONTRACT
    val MDAO_CONTRACT: String get() = BuildConfig.MDAO_CONTRACT
    val ETHERSCAN_API_KEY: String get() = BuildConfig.ETHERSCAN_API_KEY
    const val ETHERSCAN_API_URL = "https://api.bscscan.com/api"
    const val EXPLORER_URL = "https://bscscan.com"
    const val ENTRY_POINT = "0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789"
    const val SIMPLE_ACCOUNT_FACTORY = "0x9406Cc6185a346906296840746125a0E44976454"
    const val SOCIAL_RECOVERY_MODULE = "0x0000000000000000000000000000000000000000" // Set by deploy script
    val PAYMASTER_CONTRACT: String get() = BuildConfig.PAYMASTER_CONTRACT
    val PROPOSAL_CONTRACT: String get() = BuildConfig.PROPOSAL_CONTRACT

    val activeChainId: Long get() = CHAIN_ID

    fun isConfigured(): Boolean =
        MDAO_CONTRACT != "0x0000000000000000000000000000000000000000" &&
        SOCIAL_RECOVERY_MODULE != "0x0000000000000000000000000000000000000000"

    fun toDeployLog(): String = buildString {
        appendLine("=== MDAOPay BSC Deployment ===")
        appendLine("MDAO_TOKEN:          $MDAO_CONTRACT")
        appendLine("SOCIAL_RECOVERY:     $SOCIAL_RECOVERY_MODULE")
        appendLine("ENTRY_POINT:         $ENTRY_POINT")
        appendLine("SIMPLE_ACCOUNT_FACT: $SIMPLE_ACCOUNT_FACTORY")
        appendLine("USDT:                $USDT_CONTRACT")
        appendLine("RPC:                 $RPC_URL")
        appendLine("BUNDLER:             $BUNDLER_URL")
        appendLine("EXPLORER:            $EXPLORER_URL")
    }
}
