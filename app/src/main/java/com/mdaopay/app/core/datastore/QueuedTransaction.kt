package com.mdaopay.app.core.datastore

import kotlinx.serialization.Serializable

@Serializable
data class QueuedTransaction(
    val idempotencyKey: String,
    val recipientAddress: String,
    val weiAmount: String,
    val nickname: String,
    val displayAmount: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
)
