package com.mdaopay.app.core.network

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mdaopay.app.core.blockchain.SendRepository
import com.mdaopay.app.core.common.Result as AppResult
import com.mdaopay.app.core.common.toUserMessage
import com.mdaopay.app.core.datastore.TransactionHistory
import com.mdaopay.app.core.datastore.TransactionRecord
import com.mdaopay.app.core.datastore.TxQueue
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.math.BigInteger

@HiltWorker
class OfflineSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val txQueue: TxQueue,
    private val sendRepository: SendRepository,
    private val transactionHistory: TransactionHistory
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME = "offline_sync"
        private const val MAX_CONCURRENT = 3

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueOnNetworkRestored(context: Context) {
            schedule(context)
        }
    }

    override suspend fun doWork(): Result {
        val queued = txQueue.getQueued()
        if (queued.isEmpty()) return Result.success()

        val batch = queued.take(MAX_CONCURRENT)
        var successCount = 0
        var failureCount = 0

        for (tx in batch) {
            if (tx.retryCount >= TxQueue.MAX_RETRIES) {
                failureCount++
                continue
            }

            val wei = BigInteger(tx.weiAmount)
            try {
                val sendResult = sendRepository.sendUsdt(tx.recipientAddress, wei)
                when (sendResult) {
                    is AppResult.Success -> {
                        val txHash = sendResult.data
                        txQueue.remove(tx.idempotencyKey)
                        transactionHistory.addTransaction(
                            TransactionRecord(
                                id = txHash,
                                nickname = tx.nickname,
                                address = tx.recipientAddress,
                                amountUsdt = "-${tx.displayAmount}",
                                timestamp = tx.createdAt,
                                status = "CONFIRMED"
                            )
                        )
                        successCount++
                    }
                    is AppResult.Error -> {
                        txQueue.incrementRetry(tx.idempotencyKey, sendResult.error.toUserMessage())
                        failureCount++
                    }
                    is AppResult.Loading -> {}
                }
            } catch (e: Exception) {
                txQueue.incrementRetry(tx.idempotencyKey, e.message ?: "Unknown error")
                failureCount++
            }
        }

        val remaining = txQueue.getQueued()
        return if (remaining.isEmpty()) Result.success() else Result.retry()
    }
}
