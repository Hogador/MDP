package com.mdaopay.app.core.blockchain

import com.mdaopay.app.core.blockchain.erc4337.BundlerClient
import com.mdaopay.app.core.blockchain.erc4337.UserOperation
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.domain.usecase.GaslessTransactionOrchestrator
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendRepository @Inject constructor(
    private val walletManager: WalletManager,
    private val ethereumClient: EthereumClient,
    private val bundlerClient: BundlerClient,
    private val gaslessOrchestrator: Lazy<GaslessTransactionOrchestrator>
) {

    // ── Public API ─────────────────────────────────────────

    /**
     * Отправляет USDT через ERC-4337.
     * Сначала пытается gasless (paymaster), при недоступности — fallback на нативный газ.
     */
    suspend fun sendUsdt(
        recipientAddress: String,
        amount: BigInteger
    ): Result<String> {
        val gaslessResult = gaslessOrchestrator.get().sendUsdtGasless(
            recipient = recipientAddress,
            amount = amount,
            fallbackToNativeGas = false  // fallback handled here, not in orchestrator
        )
        return when (gaslessResult) {
            is Result.Success -> Result.Success(gaslessResult.data.txHash)
            is Result.Error -> sendUsdtNative(recipientAddress, amount)
            is Result.Loading -> sendUsdtNative(recipientAddress, amount)
        }
    }

    /**
     * Native-gas only send (no paymaster). Used as fallback when gasless fails.
     */
    suspend fun sendUsdtNative(
        recipientAddress: String,
        amount: BigInteger
    ): Result<String> {
        val userOpResult = buildUserOp(recipientAddress, amount)
        if (userOpResult is Result.Error) return userOpResult
        @Suppress("UNCHECKED_CAST")
        return executeUserOp((userOpResult as Result.Success<UserOperation>).data)
    }

    /**
     * Строит [UserOperation] для USDT transfer через ERC-4337.
     * @param paymasterAndData данные пэймастера (пустой массив = нативный газ)
     */
    suspend fun buildUserOp(
        recipientAddress: String,
        amount: BigInteger,
        paymasterAndData: ByteArray = ByteArray(0)
    ): Result<UserOperation> = withContext(Dispatchers.IO) {
        try {
            val walletData = walletManager.getWalletData()
                ?: return@withContext Result.Error(AppError.Unknown(Exception("Wallet not initialized")))
            val keyPair = walletData.keyPair

            val accountResult = getOrComputeAccountAddress(keyPair)
            if (accountResult is Result.Error) return@withContext accountResult
            val smartAccountAddress = (accountResult as Result.Success).data

            val nonceResult = getNonce(smartAccountAddress)
            if (nonceResult is Result.Error) return@withContext nonceResult
            val nonce = (nonceResult as Result.Success).data

            val usdtTransfer = FunctionEncoder.encode(
                Function("transfer", listOf(Address(recipientAddress), Uint256(amount)), emptyList())
            )
            val executeCallData = FunctionEncoder.encode(
                Function(
                    "execute",
                    listOf(
                        Address(NetworkConfig.USDT_CONTRACT),
                        Uint256(BigInteger.ZERO),
                        DynamicBytes(Numeric.hexStringToByteArray(usdtTransfer))
                    ),
                    emptyList()
                )
            )

            val deployedResult = isContractDeployed(smartAccountAddress)
            val deployed = deployedResult is Result.Success && (deployedResult as Result.Success).data
            val initCode: ByteArray = if (deployed) {
                ByteArray(0)
            } else {
                buildInitCode(keyPair)
            }

            var callGasLimit = BigInteger.valueOf(200_000)
            var verificationGasLimit = BigInteger.valueOf(150_000)
            var preVerificationGas = BigInteger.valueOf(50_000)

            val userOp = UserOperation(
                sender = smartAccountAddress,
                nonce = nonce,
                initCode = initCode,
                callData = Numeric.hexStringToByteArray(executeCallData),
                callGasLimit = callGasLimit,
                verificationGasLimit = verificationGasLimit,
                preVerificationGas = preVerificationGas,
                maxFeePerGas = BigInteger.valueOf(1_500_000_000),
                maxPriorityFeePerGas = BigInteger.valueOf(1_000_000_000),
                paymasterAndData = paymasterAndData
            )

            val gasEstimate = bundlerClient.estimateUserOperationGas(userOp, NetworkConfig.ENTRY_POINT)
            gasEstimate.onSuccess { gas ->
                callGasLimit = gas.callGasLimit
                verificationGasLimit = gas.verificationGasLimit
                preVerificationGas = gas.preVerificationGas
            }

            val finalOp = userOp.copy(
                callGasLimit = callGasLimit,
                verificationGasLimit = verificationGasLimit,
                preVerificationGas = preVerificationGas
            )

            Result.Success(finalOp)
        } catch (e: Exception) {
            Result.Error(AppError.Unknown(e))
        }
    }

    /**
     * Подписывает [UserOperation] и отправляет через Bundler.
     */
    suspend fun executeUserOp(userOp: UserOperation): Result<String> = withContext(Dispatchers.IO) {
        try {
            val walletData = walletManager.getWalletData()
                ?: return@withContext Result.Error(AppError.Unknown(Exception("Wallet not initialized")))
            val keyPair = walletData.keyPair

            val userOpHash = userOp.computeUserOpHash(NetworkConfig.ENTRY_POINT, NetworkConfig.CHAIN_ID)
            val signatureData = Sign.signMessage(userOpHash, keyPair)
            val sigBytes = ByteArray(65).apply {
                signatureData.r.copyInto(this, 0)
                signatureData.s.copyInto(this, 32)
                this[64] = signatureData.v.last()
            }
            // ponytail: F-100 fix — assign signature before sending (was missing in original)
            userOp.signature = sigBytes

            val txResult = bundlerClient.sendUserOperation(userOp, NetworkConfig.ENTRY_POINT)
            val txHash = txResult.getOrNull() ?: return@withContext Result.Error(
                AppError.Unknown(Exception(txResult.exceptionOrNull()))
            )

            Result.Success(txHash)
        } catch (e: Exception) {
            Result.Error(AppError.Unknown(e))
        }
    }

    suspend fun getSmartAccountAddress(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val walletData = walletManager.getWalletData()
                ?: return@withContext Result.Error(AppError.Unknown(Exception("Wallet not initialized")))
            getOrComputeAccountAddress(walletData.keyPair)
        } catch (e: Exception) {
            Result.Error(AppError.Unknown(e))
        }
    }

    private suspend fun getOrComputeAccountAddress(keyPair: ECKeyPair): Result<String> {
        val owner = Keys.getAddress(keyPair)
        val salt = Hash.sha3(Numeric.hexStringToByteArray(owner))

        val createAccountData = FunctionEncoder.encode(
            Function("createAccount", listOf(Address("0x$owner"), Uint256(BigInteger(salt))), emptyList())
        )

        return try {
            val web3j = ethereumClient.getWeb3j()
            val result = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    NetworkConfig.ENTRY_POINT,
                    NetworkConfig.SIMPLE_ACCOUNT_FACTORY,
                    createAccountData
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()

            if (result.hasError()) {
                Result.Error(AppError.Unknown(Exception("eth_call error: ${result.error.message}")))
            } else {
                val address = "0x" + result.value.substring(result.value.length - 40)
                Result.Success(address)
            }
        } catch (e: Exception) {
            Result.Error(AppError.Unknown(e))
        }
    }

    private suspend fun getNonce(address: String): Result<BigInteger> {
        val getNonceData = FunctionEncoder.encode(
            Function("getNonce", listOf(Address(address)), emptyList())
        )

        return try {
            val web3j = ethereumClient.getWeb3j()
            val result = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    NetworkConfig.ENTRY_POINT,
                    NetworkConfig.ENTRY_POINT,
                    getNonceData
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()

            if (result.hasError()) {
                Result.Error(AppError.Unknown(Exception("getNonce error: ${result.error.message}")))
            } else {
                val nonce = Numeric.toBigInt(result.value)
                Result.Success(nonce)
            }
        } catch (e: Exception) {
            Result.Error(AppError.Unknown(e))
        }
    }

    private suspend fun isContractDeployed(address: String): Result<Boolean> {
        return try {
            val web3j = ethereumClient.getWeb3j()
            val code = web3j.ethGetCode(address, org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send()
            val deployed = code.code != null && code.code != "0x" && code.code.isNotEmpty()
            Result.Success(deployed)
        } catch (e: Exception) {
            Result.Error(AppError.Unknown(e))
        }
    }

    private fun buildInitCode(keyPair: ECKeyPair): ByteArray {
        val owner = Keys.getAddress(keyPair)
        val salt = Hash.sha3(Numeric.hexStringToByteArray(owner))

        val createAccountData = FunctionEncoder.encode(
            Function("createAccount", listOf(Address("0x$owner"), Uint256(BigInteger(salt))), emptyList())
        )

        val factoryAddress = Numeric.hexStringToByteArray(NetworkConfig.SIMPLE_ACCOUNT_FACTORY)
        val createAccountBytes = Numeric.hexStringToByteArray(createAccountData)
        val result = ByteArray(factoryAddress.size + createAccountBytes.size)
        factoryAddress.copyInto(result, 0)
        createAccountBytes.copyInto(result, factoryAddress.size)
        return result
    }
}
