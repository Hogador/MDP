package com.mdaopay.app.feature.proposal.domain

import com.mdaopay.app.core.blockchain.EthereumClient
import com.mdaopay.app.core.blockchain.NetworkConfig
import com.mdaopay.app.core.blockchain.SendRepository
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.blockchain.erc4337.BundlerClient
import com.mdaopay.app.core.blockchain.erc4337.UserOperation
import com.mdaopay.app.core.blockchain.paymaster.PaymasterClient
import com.mdaopay.app.core.blockchain.paymaster.PaymasterError
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.datastore.TransactionHistory
import com.mdaopay.app.core.datastore.TransactionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction as EthTransaction
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoteRepository @Inject constructor(
    private val walletManager: WalletManager,
    private val sendRepository: SendRepository,
    private val bundlerClient: BundlerClient,
    private val paymasterClient: PaymasterClient,
    private val ethereumClient: EthereumClient,
    private val transactionHistory: TransactionHistory
) {

    suspend fun castVote(proposalId: Long, support: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val walletData = walletManager.getWalletData()
                ?: return@withContext Result.Error(AppError.Unknown(Exception("Wallet not initialized")))
            val keyPair = walletData.keyPair

            // 1. SmartAccount address
            val senderResult = sendRepository.getSmartAccountAddress()
            if (senderResult is Result.Error) return@withContext senderResult
            val sender = (senderResult as Result.Success).data

            // 2. Encode vote(proposalId, support) → execute(target, 0, data)
            val voteCallData = FunctionEncoder.encode(
                Function("vote", listOf(Uint256(BigInteger.valueOf(proposalId)), Uint8(support.toBigInteger())), emptyList())
            )
            val executeCallData = FunctionEncoder.encode(
                Function(
                    "execute",
                    listOf(Address(NetworkConfig.PROPOSAL_CONTRACT), Uint256(BigInteger.ZERO), DynamicBytes(Numeric.hexStringToByteArray(voteCallData))),
                    emptyList()
                )
            )
            val callDataBytes = Numeric.hexStringToByteArray(executeCallData)

            // 3. Nonce from EntryPoint
            val nonce = fetchNonce(sender).getOrElse {
                return@withContext Result.Error(AppError.Unknown(Exception("Failed to get nonce: $it")))
            }

            // 4. Build UserOp skeleton (empty paymaster, initial gas)
            var callGasLimit = BigInteger.valueOf(200_000)
            var verificationGasLimit = BigInteger.valueOf(150_000)
            var preVerificationGas = BigInteger.valueOf(50_000)

            val userOp = UserOperation(
                sender = sender,
                nonce = nonce,
                initCode = ByteArray(0),
                callData = callDataBytes,
                callGasLimit = callGasLimit,
                verificationGasLimit = verificationGasLimit,
                preVerificationGas = preVerificationGas,
                maxFeePerGas = BigInteger.valueOf(1_500_000_000),
                maxPriorityFeePerGas = BigInteger.valueOf(1_000_000_000),
                paymasterAndData = ByteArray(0)
            )

            // 5. Gas estimation
            bundlerClient.estimateUserOperationGas(userOp, NetworkConfig.ENTRY_POINT)
                .onSuccess { gas ->
                    callGasLimit = gas.callGasLimit
                    verificationGasLimit = gas.verificationGasLimit
                    preVerificationGas = gas.preVerificationGas
                }

            val estimatedOp = userOp.copy(
                callGasLimit = callGasLimit,
                verificationGasLimit = verificationGasLimit,
                preVerificationGas = preVerificationGas
            )

            // 6. Paymaster signature
            val signResponse = paymasterClient.signUserOp(
                sender = estimatedOp.sender,
                nonce = estimatedOp.nonce,
                initCode = estimatedOp.initCode,
                callData = estimatedOp.callData,
                verificationGasLimit = estimatedOp.verificationGasLimit,
                callGasLimit = estimatedOp.callGasLimit,
                preVerificationGas = estimatedOp.preVerificationGas,
                maxPriorityFeePerGas = estimatedOp.maxPriorityFeePerGas,
                maxFeePerGas = estimatedOp.maxFeePerGas
            )
            val pmOp = estimatedOp.copy(
                paymasterAndData = Numeric.hexStringToByteArray(signResponse.paymasterAndData)
            )

            // 7. Sign & send via SendRepository
            val txResult = sendRepository.executeUserOp(pmOp)
            if (txResult is Result.Error) return@withContext txResult
            val txHash = (txResult as Result.Success).data

            transactionHistory.addTransaction(
                TransactionRecord(
                    id = txHash,
                    nickname = "Proposal #$proposalId",
                    address = NetworkConfig.PROPOSAL_CONTRACT,
                    amountUsdt = "0",
                    timestamp = System.currentTimeMillis() / 1000,
                    status = "PENDING"
                )
            )

            Result.Success(txHash)
        } catch (e: PaymasterError) {
            Result.Error(AppError.Unknown(e))
        } catch (e: Exception) {
            Result.Error(AppError.Unknown(e))
        }
    }

    private suspend fun fetchNonce(sender: String): kotlin.Result<BigInteger> = withContext(Dispatchers.IO) {
        try {
            val getNonceData = FunctionEncoder.encode(
                Function("getNonce", listOf(Address(sender)), emptyList())
            )
            val web3j = ethereumClient.getWeb3j()
            val response = web3j.ethCall(
                EthTransaction.createEthCallTransaction(NetworkConfig.ENTRY_POINT, NetworkConfig.ENTRY_POINT, getNonceData),
                DefaultBlockParameterName.LATEST
            ).send()
            if (response.hasError()) kotlin.Result.failure(Exception(response.error.message))
            else kotlin.Result.success(Numeric.toBigInt(response.value))
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
}
