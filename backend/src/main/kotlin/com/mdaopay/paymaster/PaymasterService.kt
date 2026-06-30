package com.mdaopay.paymaster

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.ECDSASignature
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

// F-09: reduced from 100 to 20 — limits DoS vector per sender
private const val MAX_NONCE_GAP = 20L

// F-034: EIP-712 typehashes for backend quote signing (must match MDAOPaymaster.sol)
private val QUOTE_TYPEHASH: ByteArray = Numeric.hexStringToByteArray(
    Hash.sha3String("Quote(address sender,address token,uint256 maxTokenAmount,uint256 maxGasPrice,uint256 quoteDeadline,uint256 nonce)")
)
private val EIP712_DOMAIN_TYPEHASH: ByteArray = Numeric.hexStringToByteArray(
    Hash.sha3String("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)")
)
private val MDAOPAY_NAME_HASH: ByteArray = Numeric.hexStringToByteArray(Hash.sha3String("MDAOPay"))
private val MDAOPAY_VERSION_HASH: ByteArray = Numeric.hexStringToByteArray(Hash.sha3String("1"))

@Serializable
data class SignRequest(
    val sender: String,
    val nonce: String,
    val initCode: String = "0x",
    val callData: String,
    val verificationGasLimit: String,
    val callGasLimit: String,
    val preVerificationGas: String,
    val maxPriorityFeePerGas: String,
    val maxFeePerGas: String,
    val paymasterAndData: String = "0x",
    val signature: String = "0x",
    val mdaoMaxAmount: String? = null,
    val usdtMaxAmount: String? = null,
    val permitDeadline: String? = null,
    val permitV: String? = null,
    val permitR: String? = null,
    val permitS: String? = null,
)

@Serializable
data class SignResponse(
    val paymasterAndData: String,
    val userOpHash: String,
    val maxFee: String,
    val token: String,
)

private val ERC20_BALANCE_OF_SELECTOR = FunctionEncoder.encode(
    Function("balanceOf", listOf(Address("0")), listOf(TypeReference.create(Uint256::class.java)))
).take(10).removePrefix("0x")

private val ERC20_ALLOWANCE_SELECTOR = FunctionEncoder.encode(
    Function("allowance", listOf(Address("0"), Address("0")), listOf(TypeReference.create(Uint256::class.java)))
).take(10).removePrefix("0x")

class PaymasterService(
    private val config: AppConfig,
    private val rpcManager: RpcProviderManager,
    private val signer: PaymasterSigner,
    private val priceOracle: PriceOracle,
) {
    private val log = LoggerFactory.getLogger(PaymasterService::class.java)

    private suspend fun <T> withWeb3j(block: suspend (Web3j) -> T): T {
        val result = rpcManager.getBestProvider()
        val web3j = result.getOrElse { throw GasEstimationException("No RPC provider available") }
        return block(web3j)
    }

    suspend fun sign(req: SignRequest): SignResponse {
        val startNanos = System.nanoTime()
        val sender = req.sender
        val nonce = req.nonce

        log.info("sign: sender={} nonce={}", sender, nonce)

        val expectedChainId = config.expectedChainId
        val actualChainId = withWeb3j { it.ethChainId().sendAsync().await().chainId.toLong() }
        log.info("chainId check: expected={} actual={}", expectedChainId, actualChainId)
        if (actualChainId != expectedChainId) {
            throw GasEstimationException(
                "Chain ID mismatch: expected $expectedChainId, got $actualChainId. " +
                "Wrong PAYMASTER_PRIVATE_KEY for this network?"
            )
        }

        val onChainNonce = withWeb3j {
            it.ethGetTransactionCount(sender, DefaultBlockParameterName.PENDING)
                .sendAsync().await().transactionCount
        }
        val reqNonce = nonce.hexToBigInt()
        if (reqNonce < onChainNonce) {
            throw GasEstimationException(
                "Nonce too low: request=$reqNonce onChain=$onChainNonce. " +
                "Transaction may have already been submitted."
            )
        }
        if (reqNonce > onChainNonce.add(BigInteger.valueOf(MAX_NONCE_GAP))) {
            throw GasEstimationException(
                "Nonce too far ahead: request=$reqNonce onChain=$onChainNonce maxGap=$MAX_NONCE_GAP. " +
                "Potential replay or DoS attempt."
            )
        }

        val prices = priceOracle.getPrices()
        val isTestnet = expectedChainId == 97L
        val finalPrices = if (prices.bnbUsd <= 0.0 || prices.mdaoUsd <= 0.0 || prices.usdtUsd <= 0.0) {
            if (isTestnet) {
                // F-10: log fallback usage for monitoring
                log.warn("FALLBACK_PRICES used — oracle returned zero values on testnet (chainId={})", expectedChainId)
                DexPrices.fallbackPrices()
            } else throw GasEstimationException("Price fetch failed. Cannot proceed on mainnet with fallback prices.")
        } else prices

        val totalGas = req.verificationGasLimit.hexToBigInt()
            .add(req.callGasLimit.hexToBigInt())
            .add(req.preVerificationGas.hexToBigInt())
        val gasCostWei = req.maxFeePerGas.hexToBigInt().multiply(totalGas)
        val bnbUsd = BigDecimal(finalPrices.bnbUsd)
        val gasCostUsd = BigDecimal(gasCostWei).multiply(bnbUsd)
            .divide(BigDecimal.TEN.pow(18), 18, RoundingMode.HALF_UP)

        log.info("gas: totalWei={} bnbUsd={} gasUsd={}", gasCostWei, bnbUsd, gasCostUsd)

        val mdaoNeeded = calcTokenAmount(gasCostUsd, finalPrices.mdaoUsd)
        val usdtNeeded = calcTokenAmount(gasCostUsd, finalPrices.usdtUsd)
        val mdaoMax = req.mdaoMaxAmount?.hexToBigInt()
        val usdtMax = req.usdtMaxAmount?.hexToBigInt()
        val usePermit = req.permitV != null

        val rpcStartNanos = System.nanoTime()

        // Fetch all balances and allowances in parallel
        val mdaoBalance: BigInteger
        val usdtBalance: BigInteger
        val mdaoAllowance: BigInteger
        val usdtAllowance: BigInteger
        coroutineScope {
            val b1 = async { callTokenBalance(config.mdaoAddress, sender) }
            val b2 = async { callTokenBalance(config.usdtAddress, sender) }
            val a1 = if (mdaoMax != null && !usePermit)
                async { callTokenAllowance(config.mdaoAddress, sender, config.paymasterAddress) }
            else
                async { BigInteger.ZERO }
            val a2 = async { callTokenAllowance(config.usdtAddress, sender, config.paymasterAddress) }
            awaitAll(b1, b2, a1, a2)
            mdaoBalance = b1.await()
            usdtBalance = b2.await()
            mdaoAllowance = a1.await()
            usdtAllowance = a2.await()
        }

        val rpcElapsed = (System.nanoTime() - rpcStartNanos) / 1_000_000
        log.info("rpc: mdaoBalance={} usdtBalance={} mdaoAllowance={} usdtAllowance={} elapsed={}ms",
            mdaoBalance, usdtBalance, mdaoAllowance, usdtAllowance, rpcElapsed)

        // Try MDAO first
        if (mdaoMax != null && mdaoNeeded <= mdaoMax && mdaoNeeded <= mdaoBalance) {
            if (!usePermit && mdaoAllowance < mdaoNeeded) {
                throw GasEstimationException("MDAO allowance too low: $mdaoAllowance < $mdaoNeeded")
            }
            val resp = signWithToken(req, config.mdaoAddress, mdaoNeeded, usePermit)
            val totalElapsed = (System.nanoTime() - startNanos) / 1_000_000
            log.info("done: token=MDAO sender={} nonce={} totalElapsed={}ms", sender, nonce, totalElapsed)
            return resp
        }

        // Fallback to USDT
        if (usdtMax == null || usdtNeeded > usdtMax || usdtNeeded > usdtBalance) {
            throw GasEstimationException(
                "Cannot afford gas. MDAO: need=$mdaoNeeded have=$mdaoBalance max=$mdaoMax; " +
                "USDT: need=$usdtNeeded have=$usdtBalance max=$usdtMax"
            )
        }

        if (usdtAllowance < usdtNeeded)
            throw GasEstimationException("USDT allowance too low: $usdtAllowance < $usdtNeeded")

        val resp = signWithToken(req, config.usdtAddress, usdtNeeded, false)
        val totalElapsed = (System.nanoTime() - startNanos) / 1_000_000
        log.info("done: token=USDT sender={} nonce={} totalElapsed={}ms", sender, nonce, totalElapsed)
        return resp
    }

    private suspend fun signWithToken(
        req: SignRequest, token: String, amount: BigInteger, usePermit: Boolean
    ): SignResponse {
        // F-034: compute quote deadline once, use in both encoding and signing
        val quoteDeadline = (System.currentTimeMillis() / 1000) + 300
        val unsignedPm = buildPaymasterAndData(
            token, amount, quoteDeadline,
            if (usePermit) req.permitDeadline else null,
            if (usePermit) req.permitV else null,
            if (usePermit) req.permitR else null,
            if (usePermit) req.permitS else null,
        )
        val nonce = getQuoteNonce(req.sender)
        val modReq = req.copy(paymasterAndData = unsignedPm)
        val signedPm = stampPaymasterAndData(modReq, token, amount, BigInteger.valueOf(quoteDeadline), nonce)
        val userOpHash = computeUserOpHash(modReq, signedPm)
        return SignResponse(signedPm, userOpHash, amount.toString(), token)
    }

    private fun buildPaymasterAndData(
        token: String, amount: BigInteger, quoteDeadlineSecs: Long,
        permitDeadline: String? = null, permitV: String? = null,
        permitR: String? = null, permitS: String? = null,
    ): String {
        val pmHex = config.paymasterAddress.removePrefix("0x")
        val tokenHex = token.removePrefix("0x")
        val amtHex = amount.toString(16).padStart(64, '0')
        val quoteDeadlineHex = quoteDeadlineSecs.toString(16).padStart(64, '0')

        val base = "0x$pmHex$tokenHex$amtHex$quoteDeadlineHex"
        if (permitV != null && permitR != null && permitS != null && permitDeadline != null) {
            val permDeadlineHex = permitDeadline.removePrefix("0x").padStart(64, '0')
            val vHex = permitV.removePrefix("0x").takeLast(2).padStart(2, '0')
            val rHex = permitR.removePrefix("0x").padStart(64, '0')
            val sHex = permitS.removePrefix("0x").padStart(64, '0')
            return base + permDeadlineHex + vHex + rHex + sHex
        }
        return base
    }

    // F-034: sign using EIP-712 Quote hash instead of EIP-191 userOpHash
    private fun stampPaymasterAndData(
        req: SignRequest,
        token: String,
        maxTokenAmount: BigInteger,
        quoteDeadline: BigInteger,
        nonce: BigInteger
    ): String {
        val hash = computeEIP712QuoteHash(
            sender = req.sender,
            token = token,
            maxTokenAmount = maxTokenAmount,
            maxGasPrice = req.maxFeePerGas.hexToBigInt(),
            quoteDeadline = quoteDeadline,
            nonce = nonce,
            chainId = config.expectedChainId,
            verifyingContract = config.paymasterAddress
        )
        // F-001: Sign the 32-byte EIP-712 digest using the configured signer.
        // Do NOT use Sign.signMessage() — it applies Hash.sha3() internally even
        // with needToHash=false, producing a signature over sha3(digest) instead of digest,
        // which is incompatible with Solidity's ecrecover.
        val (v, r, s) = signer.signDigest(hash)
        val sigData = Numeric.toHexString(byteArrayOf(v) + r + s).removePrefix("0x")
        val magic = "22e325a297439656"
        val lenHex = Integer.toHexString(sigData.length / 2).padStart(4, '0')
        return addPaymasterSuffix(req.paymasterAndData, sigData, lenHex, magic)
    }

    // F-034: EIP-712 digest = keccak256("\x19\x01" || domainSeparator || structHash)
    private fun computeEIP712QuoteHash(
        sender: String,
        token: String,
        maxTokenAmount: BigInteger,
        maxGasPrice: BigInteger,
        quoteDeadline: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        verifyingContract: String
    ): ByteArray {
        // domainSeparator = keccak256(abi.encode(typeHash, nameHash, versionHash, chainId, verifyingContract))
        val domainSeparator = Hash.sha3(
            EIP712_DOMAIN_TYPEHASH +
            MDAOPAY_NAME_HASH +
            MDAOPAY_VERSION_HASH +
            Numeric.toBytesPadded(BigInteger.valueOf(chainId), 32) +
            Numeric.toBytesPadded(Numeric.toBigInt(verifyingContract), 32)
        )
        // structHash = keccak256(abi.encode(typeHash, sender, token, amount, gasPrice, deadline, nonce))
        val structHash = Hash.sha3(
            QUOTE_TYPEHASH +
            Numeric.toBytesPadded(Numeric.toBigInt(sender), 32) +
            Numeric.toBytesPadded(Numeric.toBigInt(token), 32) +
            Numeric.toBytesPadded(maxTokenAmount, 32) +
            Numeric.toBytesPadded(maxGasPrice, 32) +
            Numeric.toBytesPadded(quoteDeadline, 32) +
            Numeric.toBytesPadded(nonce, 32)
        )
        // abi.encodePacked("\x19\x01", domainSeparator, structHash)
        return Hash.sha3(byteArrayOf(0x19, 0x01) + domainSeparator + structHash)
    }

    // F-001 signing logic moved to LocalPaymasterSigner (PaymasterSigner.kt)

    // F-034: read quoteNonces[sender] from paymaster contract
    private suspend fun getQuoteNonce(sender: String): BigInteger = withWeb3j { web3j ->
        val selector = Hash.sha3String("quoteNonces(address)").substring(0, 10)
        val data = selector + Address(sender).withoutPrefix.padStart(64, '0')
        val result = web3j.ethCall(
            Transaction.createEthCallTransaction(null, config.paymasterAddress, data),
            DefaultBlockParameterName.LATEST
        ).sendAsync().await().value
        val decoded = FunctionReturnDecoder.decode(
            result,
            listOf(TypeReference.create(Uint256::class.java) as TypeReference<org.web3j.abi.datatypes.Type<*>>)
        )
        if (decoded.isEmpty() || decoded.first() !is Uint256) {
            throw GasEstimationException("Failed to decode quote nonce for sender $sender")
        }
        (decoded.first() as Uint256).value
    }

    private fun computeUserOpHash(req: SignRequest, pmAndData: String): String {
        val packed = "0x" +
            req.sender.drop(2) +
            req.nonce.drop(2) +
            Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(req.initCode))).drop(2) +
            Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(req.callData))).drop(2) +
            req.accountGasLimits().drop(2) +
            req.preVerificationGas.drop(2).padStart(64, '0') +
            req.gasFees().drop(2) +
            Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(pmAndData))).drop(2) +
            Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(req.signature))).drop(2)
        return Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(packed)))
    }

    private fun calcTokenAmount(gasCostUsd: BigDecimal, tokenUsd: Double): BigInteger {
        return gasCostUsd.multiply(BigDecimal.TEN.pow(18))
            .divide(BigDecimal(tokenUsd), 0, RoundingMode.HALF_UP)
            .toBigInteger()
    }

    private suspend fun callTokenBalance(token: String, owner: String): BigInteger = withWeb3j { web3j ->
        val data = "0x" + ERC20_BALANCE_OF_SELECTOR + Address(owner).withoutPrefix.padStart(64, '0')
        val result = web3j.ethCall(
            Transaction.createEthCallTransaction(null, token, data),
            DefaultBlockParameterName.LATEST
        ).sendAsync().await().value
        val decoded = FunctionReturnDecoder.decode(result, listOf(TypeReference.create(Uint256::class.java) as TypeReference<org.web3j.abi.datatypes.Type<*>>))
        if (decoded.isEmpty() || decoded.first() !is Uint256) {
            throw GasEstimationException("Failed to decode balance for token $token")
        }
        (decoded.first() as Uint256).value
    }

    private suspend fun callTokenAllowance(token: String, owner: String, spender: String): BigInteger = withWeb3j { web3j ->
        val data = "0x" + ERC20_ALLOWANCE_SELECTOR +
            Address(owner).withoutPrefix.padStart(64, '0') +
            Address(spender).withoutPrefix.padStart(64, '0')
        val result = web3j.ethCall(
            Transaction.createEthCallTransaction(null, token, data),
            DefaultBlockParameterName.LATEST
        ).sendAsync().await().value
        val decoded = FunctionReturnDecoder.decode(result, listOf(TypeReference.create(Uint256::class.java) as TypeReference<org.web3j.abi.datatypes.Type<*>>))
        if (decoded.isEmpty() || decoded.first() !is Uint256) {
            throw GasEstimationException("Failed to decode allowance for token $token")
        }
        (decoded.first() as Uint256).value
    }
}

private val Address.withoutPrefix: String get() = Numeric.cleanHexPrefix(value)

internal fun addPaymasterSuffix(pmAndData: String, sigHex: String, lenHex: String, magic: String): String {
    val clean = pmAndData.removePrefix("0x")
    return "0x$clean$sigHex$lenHex$magic"
}

internal fun SignRequest.accountGasLimits(): String {
    val vgl = verificationGasLimit.hexToBigInt()
    val cgl = callGasLimit.hexToBigInt()
    return vgl.multiply(BigInteger.valueOf(2).pow(128)).or(cgl).toString(16).padStart(64, '0')
}

internal fun SignRequest.gasFees(): String {
    val mpfp = maxPriorityFeePerGas.hexToBigInt()
    val mfp = maxFeePerGas.hexToBigInt()
    return mpfp.multiply(BigInteger.valueOf(2).pow(128)).or(mfp).toString(16).padStart(64, '0')
}

class GasEstimationException(message: String) : Exception(message)
