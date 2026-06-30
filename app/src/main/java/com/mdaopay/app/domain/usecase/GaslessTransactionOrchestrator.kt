package com.mdaopay.app.domain.usecase

import com.mdaopay.app.core.blockchain.SendRepository
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.blockchain.paymaster.PaymasterClient
import com.mdaopay.app.core.blockchain.paymaster.PaymasterError
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.blockchain.erc4337.UserOperation
import com.mdaopay.app.core.common.map
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат gasless-отправки.
 * @param txHash хэш транзакции
 * @param usedFallback true если произошёл fallback на нативный газ
 */
data class GaslessSendResult(
    val txHash: String,
    val usedFallback: Boolean = false
)

/**
 * Промежуточный слой между SendViewModel / SendTransactionUseCase и SendRepository.
 *
 * Пытается отправить USDT через пэймастер (gasless).
 * Если пэймастер недоступен — падает на нативный газ (оплата из BNB).
 *
 * ponytail: без новых зависимостей, переиспользует OkHttp и существующие репозитории.
 */
@Singleton
class GaslessTransactionOrchestrator @Inject constructor(
    private val paymasterClient: PaymasterClient,
    private val sendRepository: SendRepository,
    private val walletManager: WalletManager
) {

    suspend fun sendUsdtGasless(
        recipient: String,
        amount: BigInteger,
        fallbackToNativeGas: Boolean = true
    ): Result<GaslessSendResult> {
        return try {
            val walletData = walletManager.getWalletData()
                ?: return Result.Error(
                    AppError.Unknown(Exception("Wallet not initialized"))
                )

            // 1. Построить UserOp с пустым paymasterAndData (gas estimation first)
            val userOpResult = sendRepository.buildUserOp(
                recipientAddress = recipient,
                amount = amount,
            )
            if (userOpResult is Result.Error) return userOpResult
            @Suppress("UNCHECKED_CAST")
            val userOp = (userOpResult as Result.Success<UserOperation>).data

            // 2. Получить подписанный paymasterAndData от backend /v1/sign
            val signResponse = paymasterClient.signUserOp(
                sender = userOp.sender,
                nonce = userOp.nonce,
                initCode = userOp.initCode,
                callData = userOp.callData,
                verificationGasLimit = userOp.verificationGasLimit,
                callGasLimit = userOp.callGasLimit,
                preVerificationGas = userOp.preVerificationGas,
                maxPriorityFeePerGas = userOp.maxPriorityFeePerGas,
                maxFeePerGas = userOp.maxFeePerGas,
                usdtMaxAmount = amount,
            )

            // 3. Обновить UserOp с подписанным paymasterAndData
            val finalUserOp = userOp.copy(
                paymasterAndData = Numeric.hexStringToByteArray(signResponse.paymasterAndData)
            )

            // 4. Отправить через Bundler
            val txResult = sendRepository.executeUserOp(finalUserOp)
            return txResult.map { txHash -> GaslessSendResult(txHash) }
        } catch (e: PaymasterError) {
            if (fallbackToNativeGas) {
                // Gasless недоступен → fallback на нативный газ
                // ponytail: sendUsdtNative — прямой native-путь без gasless (предотвращает рекурсию)
                val nativeResult = sendRepository.sendUsdtNative(recipient, amount)
                nativeResult.map { txHash ->
                    GaslessSendResult(txHash, usedFallback = true)
                }
            } else {
                Result.Error(AppError.Unknown(e))
            }
        } catch (e: Exception) {
            Result.Error(AppError.Unknown(e))
        }
    }
}
