package com.mdaopay.app.feature.send.domain

import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.datastore.TransactionHistory
import com.mdaopay.app.core.datastore.TransactionRecord
import com.mdaopay.app.core.datastore.TxQueue
import com.mdaopay.app.core.network.ConnectivityMonitor
import com.mdaopay.app.domain.usecase.GaslessSendResult
import com.mdaopay.app.domain.usecase.GaslessTransactionOrchestrator
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SendTransactionUseCase @Inject constructor(
    private val transactionHistory: TransactionHistory,
    private val txQueue: TxQueue,
    private val connectivityMonitor: ConnectivityMonitor,
    private val gaslessOrchestrator: GaslessTransactionOrchestrator
) {
    suspend fun send(
        recipientAddress: String,
        weiAmount: BigInteger,
        nickname: String,
        displayAmount: BigDecimal
    ): Result<GaslessSendResult> {
        if (!connectivityMonitor.isOnline.value) {
            txQueue.enqueue(recipientAddress, weiAmount, nickname, displayAmount)
            return Result.Error(com.mdaopay.app.core.common.AppError.Unknown(
                OfflineQueuedException(nickname, displayAmount)
            ))
        }

        val tempTxHash = "pending_${System.currentTimeMillis()}"

        transactionHistory.addTransaction(
            TransactionRecord(
                id = tempTxHash,
                nickname = nickname,
                address = recipientAddress,
                amountUsdt = "-${displayAmount.toPlainString()}",
                timestamp = System.currentTimeMillis(),
                status = "PENDING"
            )
        )

        val result = gaslessOrchestrator.sendUsdtGasless(recipientAddress, weiAmount)
        return when (result) {
            is Result.Success -> {
                transactionHistory.replaceId(tempTxHash, result.data.txHash, "CONFIRMED")
                result
            }
            is Result.Error -> {
                transactionHistory.updateStatus(tempTxHash, "FAILED")
                result
            }
            is Result.Loading -> result
        }
    }
}

class OfflineQueuedException(
    val nickname: String,
    val displayAmount: BigDecimal
) : Exception("Transaction queued for offline sync")
