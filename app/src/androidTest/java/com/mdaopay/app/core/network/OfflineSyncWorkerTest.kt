package com.mdaopay.app.core.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.mdaopay.app.core.blockchain.SendRepository
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result as AppResult
import com.mdaopay.app.core.datastore.QueuedTransaction
import com.mdaopay.app.core.datastore.TransactionHistory
import com.mdaopay.app.core.datastore.TransactionRecord
import com.mdaopay.app.core.datastore.TxQueue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.math.BigInteger

@RunWith(JUnit4::class)
class OfflineSyncWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val queued = mutableListOf<QueuedTransaction>()
    private val removed = mutableListOf<String>()
    private val retried = mutableListOf<String>()
    private val sent = mutableListOf<Pair<String, BigInteger>>()
    private val added = mutableListOf<TransactionRecord>()

    private val fakeTxQueue = object : TxQueue(txQueueDao = null!!) {
        override suspend fun getQueued(): List<QueuedTransaction> = queued.toList()
        override suspend fun remove(idempotencyKey: String) { removed.add(idempotencyKey) }
        override suspend fun incrementRetry(idempotencyKey: String, error: String) { retried.add(idempotencyKey) }
    }

    private val fakeSendRepository = object : SendRepository(
        walletManager = null!!, ethereumClient = null!!, bundlerClient = null!!
    ) {
        override suspend fun sendUsdt(recipient: String, amount: BigInteger): AppResult<String> {
            sent.add(recipient to amount)
            return AppResult.Success("0x" + "ab".repeat(32))
        }
    }

    private val fakeHistory = object : TransactionHistory(transactionDao = null!!) {
        override suspend fun addTransaction(record: TransactionRecord) { added.add(record) }
        override suspend fun getAllTransactions(): List<TransactionRecord> = added.toList()
    }

    @Test
    fun empty_queue_returns_success() = runTest {
        val worker = TestListenableWorkerBuilder<OfflineSyncWorker>(context)
            .setWorkerFactory { ctx, params ->
                OfflineSyncWorker(ctx, params, fakeTxQueue, fakeSendRepository, fakeHistory)
            }
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(sent.isEmpty())
        assertTrue(removed.isEmpty())
    }

    @Test
    fun single_queued_tx_is_sent_and_removed() = runTest {
        queued.add(QueuedTransaction(
            idempotencyKey = "key1", recipientAddress = "0xabc",
            weiAmount = "1000", nickname = "alice", displayAmount = "1.0",
            createdAt = System.currentTimeMillis()
        ))
        val worker = TestListenableWorkerBuilder<OfflineSyncWorker>(context)
            .setWorkerFactory { ctx, params ->
                OfflineSyncWorker(ctx, params, fakeTxQueue, fakeSendRepository, fakeHistory)
            }
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, sent.size)
        assertEquals("0xabc", sent[0].first)
        assertEquals(BigInteger("1000"), sent[0].second)
        assertEquals(1, removed.size)
        assertEquals("key1", removed[0])
    }

    @Test
    fun send_failure_increments_retry() = runTest {
        queued.add(QueuedTransaction(
            idempotencyKey = "key1", recipientAddress = "0xabc",
            weiAmount = "1000", nickname = "alice", displayAmount = "1.0",
            createdAt = System.currentTimeMillis()
        ))
        val failingRepo = object : SendRepository(
            walletManager = null!!, ethereumClient = null!!, bundlerClient = null!!
        ) {
            override suspend fun sendUsdt(recipient: String, amount: BigInteger): AppResult<String> {
                return AppResult.Error(AppError.NetworkError("timeout"))
            }
        }
        val worker = TestListenableWorkerBuilder<OfflineSyncWorker>(context)
            .setWorkerFactory { ctx, params ->
                OfflineSyncWorker(ctx, params, fakeTxQueue, failingRepo, fakeHistory)
            }
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(1, retried.size)
        assertEquals("key1", retried[0])
    }
}
