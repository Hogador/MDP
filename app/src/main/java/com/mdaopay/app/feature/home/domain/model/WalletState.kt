package com.mdaopay.app.feature.home.domain.model

import java.math.BigDecimal
import com.mdaopay.app.core.datastore.TransactionRecord

/**
 * Состояние кошелька пользователя.
 * Пока мок-данные — в следующих шагах подключим реальный блокчейн.
 */
data class WalletState(
    val nickname: String,
    val address: String = "",
    val balanceEth: BigDecimal = BigDecimal.ZERO,
    val balanceUsdt: BigDecimal,
    val balanceMdao: BigDecimal,
    val isOnline: Boolean = true,
    val pendingCount: Int = 0
)

data class TransactionItem(
    val id: String,
    val nickname: String,
    val amountUsdt: BigDecimal,
    val timestamp: Long,
    val status: TxStatus,
    val txHash: String = "",
    val tokenSymbol: String = "USDT",
    val counterparty: String = "",
    val isExternal: Boolean = false
)

enum class TxStatus { CONFIRMED, PENDING, FAILED }

fun TransactionRecord.toTransactionItem(): TransactionItem = TransactionItem(
    id = id,
    nickname = nickname,
    amountUsdt = BigDecimal(amountUsdt),
    timestamp = timestamp,
    status = TxStatus.valueOf(status),
    txHash = id
)