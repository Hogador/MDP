package com.mdaopay.app.core.datastore

import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TxQueue @Inject constructor(
    private val txQueueDao: TxQueueDao
) {
    suspend fun enqueue(
        recipientAddress: String,
        weiAmount: BigInteger,
        nickname: String,
        displayAmount: BigDecimal
    ): QueuedTransaction {
        val tx = QueuedTransaction(
            idempotencyKey = UUID.randomUUID().toString(),
            recipientAddress = recipientAddress,
            weiAmount = weiAmount.toString(),
            nickname = nickname,
            displayAmount = displayAmount.toPlainString(),
            createdAt = System.currentTimeMillis()
        )
        txQueueDao.insert(tx.toEntity())
        return tx
    }

    suspend fun remove(idempotencyKey: String) {
        txQueueDao.deleteById(idempotencyKey)
    }

    suspend fun incrementRetry(idempotencyKey: String, error: String) {
        txQueueDao.incrementRetry(idempotencyKey, error)
    }

    suspend fun getQueued(): List<QueuedTransaction> {
        return txQueueDao.getAll().map { it.toQueuedTransaction() }
    }

    suspend fun getQueuedCount(): Int = txQueueDao.getCount()

    suspend fun clear() {
        txQueueDao.clearAll()
    }

    companion object {
        const val MAX_RETRIES = 5
    }
}
