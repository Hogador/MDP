package com.mdaopay.app.core.datastore

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tx_queue")
data class TxQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "recipient_address")
    val recipientAddress: String,
    @ColumnInfo(name = "wei_amount")
    val weiAmount: String,
    val nickname: String,
    @ColumnInfo(name = "display_amount")
    val displayAmount: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null
)

fun TxQueueEntity.toQueuedTransaction(): QueuedTransaction = QueuedTransaction(
    idempotencyKey = idempotencyKey,
    recipientAddress = recipientAddress,
    weiAmount = weiAmount,
    nickname = nickname,
    displayAmount = displayAmount,
    createdAt = createdAt,
    retryCount = retryCount,
    lastError = lastError
)

fun QueuedTransaction.toEntity(): TxQueueEntity = TxQueueEntity(
    idempotencyKey = idempotencyKey,
    recipientAddress = recipientAddress,
    weiAmount = weiAmount,
    nickname = nickname,
    displayAmount = displayAmount,
    createdAt = createdAt,
    retryCount = retryCount,
    lastError = lastError
)
