package com.mdaopay.app.core.blockchain.erc4337

import com.mdaopay.app.core.blockchain.EthereumClient
import com.mdaopay.app.core.blockchain.NetworkConfig
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.blockchain.paymaster.PaymasterClient
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecoveryUserOpBuilder @Inject constructor(
    private val walletManager: WalletManager,
    private val bundlerClient: BundlerClient,
    private val paymasterClient: PaymasterClient,
    private val ethereumClient: EthereumClient
) {

    suspend fun buildRecoveryExecutionUserOp(
        smartAccountAddress: String
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val walletData = walletManager.getWalletData()
                ?: return@withContext Result.failure(Exception("Wallet not initialized"))
            val keyPair = walletData.keyPair

            val nonce = getNonce(smartAccountAddress).getOrElse {
                return@withContext Result.failure(it)
            }
            val deployed = isDeployed(smartAccountAddress)

            val executeCalldata = FunctionEncoder.encode(
                Function(
                    "execute",
                    listOf(
                        Address(NetworkConfig.SOCIAL_RECOVERY_MODULE),
                        org.web3j.abi.datatypes.generated.Uint256(BigInteger.ZERO),
                        org.web3j.abi.datatypes.DynamicBytes(
                            Numeric.hexStringToByteArray(
                                FunctionEncoder.encode(
                                    Function(
                                        "executeRecovery",
                                        listOf(Address(smartAccountAddress)),
                                        emptyList()
                                    )
                                )
                            )
                        )
                    ),
                    emptyList()
                )
            )

            val initCode: ByteArray = if (deployed) ByteArray(0) else buildInitCode(keyPair)

            var callGasLimit = BigInteger.valueOf(200_000)
            var verificationGasLimit = BigInteger.valueOf(150_000)
            var preVerificationGas = BigInteger.valueOf(50_000)

            val userOp = UserOperation(
                sender = smartAccountAddress,
                nonce = nonce,
                initCode = initCode,
                callData = Numeric.hexStringToByteArray(executeCalldata),
                callGasLimit = callGasLimit,
                verificationGasLimit = verificationGasLimit,
                preVerificationGas = preVerificationGas,
                maxFeePerGas = BigInteger.valueOf(1_500_000_000),
                maxPriorityFeePerGas = BigInteger.valueOf(1_000_000_000),
                paymasterAndData = ByteArray(0)
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

            val userOpHash = finalOp.computeUserOpHash(NetworkConfig.ENTRY_POINT, NetworkConfig.CHAIN_ID)
            val signatureData = Sign.signMessage(userOpHash, keyPair)
            val sigBytes = ByteArray(65).apply {
                signatureData.r.copyInto(this, 0)
                signatureData.s.copyInto(this, 32)
                this[64] = signatureData.v.last()
            }

            finalOp.signature = sigBytes
            bundlerClient.sendUserOperation(finalOp, NetworkConfig.ENTRY_POINT)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun buildRecoveryWithPaymaster(
        smartAccountAddress: String,
        token: String,
        maxTokenAmount: BigInteger
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val walletData = walletManager.getWalletData()
                ?: return@withContext Result.failure(Exception("Wallet not initialized"))
            val keyPair = walletData.keyPair

            val nonce = getNonce(smartAccountAddress).getOrElse {
                return@withContext Result.failure(it)
            }
            val deployed = isDeployed(smartAccountAddress)

            val executeCalldata = FunctionEncoder.encode(
                Function(
                    "execute",
                    listOf(
                        Address(NetworkConfig.SOCIAL_RECOVERY_MODULE),
                        org.web3j.abi.datatypes.generated.Uint256(BigInteger.ZERO),
                        org.web3j.abi.datatypes.DynamicBytes(
                            Numeric.hexStringToByteArray(
                                FunctionEncoder.encode(
                                    Function(
                                        "executeRecovery",
                                        listOf(Address(smartAccountAddress)),
                                        emptyList()
                                    )
                                )
                            )
                        )
                    ),
                    emptyList()
                )
            )

            val initCode: ByteArray = if (deployed) ByteArray(0) else buildInitCode(keyPair)

            val paymasterAndData = paymasterClient.encodePaymasterAndData(
                token = token,
                maxTokenAmount = maxTokenAmount
            )

            var callGasLimit = BigInteger.valueOf(200_000)
            var verificationGasLimit = BigInteger.valueOf(150_000)
            var preVerificationGas = BigInteger.valueOf(50_000)

            val userOp = UserOperation(
                sender = smartAccountAddress,
                nonce = nonce,
                initCode = initCode,
                callData = Numeric.hexStringToByteArray(executeCalldata),
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
                preVerificationGas = preVerificationGas,
                paymasterAndData = paymasterAndData
            )

            val userOpHash = finalOp.computeUserOpHash(NetworkConfig.ENTRY_POINT, NetworkConfig.CHAIN_ID)
            val signatureData = Sign.signMessage(userOpHash, keyPair)
            val sigBytes = ByteArray(65).apply {
                signatureData.r.copyInto(this, 0)
                signatureData.s.copyInto(this, 32)
                this[64] = signatureData.v.last()
            }

            finalOp.signature = sigBytes
            bundlerClient.sendUserOperation(finalOp, NetworkConfig.ENTRY_POINT)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSmartAccountAddress(): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val walletData = walletManager.getWalletData()
                ?: return@withContext Result.failure(Exception("Wallet not initialized"))
            getOrComputeAddress(walletData.keyPair)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getOrComputeAddress(keyPair: ECKeyPair): Result<String> {
        val owner = Keys.getAddress(keyPair)
        val salt = Hash.sha3(Numeric.hexStringToByteArray(owner))
        val callData = FunctionEncoder.encode(
            Function("createAccount", listOf(Address("0x$owner"), org.web3j.abi.datatypes.generated.Uint256(BigInteger(salt))), emptyList())
        )
        return try {
            val web3j = ethereumClient.getWeb3j()
            val result = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    NetworkConfig.ENTRY_POINT,
                    NetworkConfig.SIMPLE_ACCOUNT_FACTORY,
                    callData
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            if (result.hasError()) {
                Result.failure(Exception("eth_call error: ${result.error.message}"))
            } else {
                val address = "0x" + result.value.substring(result.value.length - 40)
                Result.success(address)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getNonce(address: String): Result<BigInteger> {
        val callData = FunctionEncoder.encode(
            Function("getNonce", listOf(Address(address)), emptyList())
        )
        return try {
            val web3j = ethereumClient.getWeb3j()
            val result = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    NetworkConfig.ENTRY_POINT,
                    NetworkConfig.ENTRY_POINT,
                    callData
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            if (result.hasError()) {
                Result.failure(Exception("getNonce error: ${result.error.message}"))
            } else {
                Result.success(Numeric.toBigInt(result.value))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun isDeployed(address: String): Boolean {
        return try {
            val web3j = ethereumClient.getWeb3j()
            val code = web3j.ethGetCode(address, org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send()
            code.code != null && code.code != "0x" && code.code.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun buildInitCode(keyPair: ECKeyPair): ByteArray {
        val owner = Keys.getAddress(keyPair)
        val salt = Hash.sha3(Numeric.hexStringToByteArray(owner))
        val createAccountData = FunctionEncoder.encode(
            Function("createAccount", listOf(Address("0x$owner"), org.web3j.abi.datatypes.generated.Uint256(BigInteger(salt))), emptyList())
        )
        val factoryAddress = Numeric.hexStringToByteArray(NetworkConfig.SIMPLE_ACCOUNT_FACTORY)
        val createAccountBytes = Numeric.hexStringToByteArray(createAccountData)
        return factoryAddress + createAccountBytes
    }
}
