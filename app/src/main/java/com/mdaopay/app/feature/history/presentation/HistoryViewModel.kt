package com.mdaopay.app.feature.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.blockchain.EtherscanRepository
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.datastore.TransactionHistory
import com.mdaopay.app.core.datastore.TransactionRecord
import com.mdaopay.app.feature.home.domain.model.TransactionItem
import com.mdaopay.app.feature.home.domain.model.TxStatus
import com.mdaopay.app.feature.home.domain.model.toTransactionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

enum class TransactionFilter { ALL, INCOME, OUTCOME }

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactionHistory: TransactionHistory,
    private val etherscanRepository: EtherscanRepository,
    private val walletManager: WalletManager
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<TransactionItem>>(emptyList())
    private val _filter = MutableStateFlow(TransactionFilter.ALL)
    val filter: StateFlow<TransactionFilter> = _filter.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val transactions: StateFlow<List<TransactionItem>> = combine(
        _transactions, _filter
    ) { all, f ->
        when (f) {
            TransactionFilter.ALL -> all
            TransactionFilter.INCOME -> all.filter { it.amountUsdt > BigDecimal.ZERO }
            TransactionFilter.OUTCOME -> all.filter { it.amountUsdt < BigDecimal.ZERO }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadTransactions()
    }

    fun setFilter(f: TransactionFilter) {
        _filter.value = f
    }

    fun loadTransactions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val records = transactionHistory.getTransactions()
                _transactions.value = records.map { it.toTransactionItem() }
                // Fire remote sync in background
                syncFromRemote()
            } catch (e: Exception) {
                _error.value = "Не удалось загрузить историю"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun syncFromRemote() {
        val wallet = walletManager.getWalletData() ?: return
        val address = wallet.address
        if (address.isBlank()) return

        try {
            val remoteTxs = etherscanRepository.fetchTransactions(address)
            val existing = transactionHistory.getTransactions().map { it.id }.toSet()

            for (tx in remoteTxs) {
                if (tx.txHash in existing) continue
                val record = TransactionRecord(
                    id = tx.txHash,
                    nickname = if (tx.from.equals(address, ignoreCase = true)) tx.to else tx.from,
                    address = if (tx.from.equals(address, ignoreCase = true)) tx.to else tx.from,
                    amountUsdt = if (tx.from.equals(address, ignoreCase = true))
                        tx.amount.negate().toString() else tx.amount.toString(),
                    timestamp = tx.timestamp,
                    status = if (tx.isError) TxStatus.FAILED.name else TxStatus.CONFIRMED.name
                )
                transactionHistory.addTransaction(record)
            }

            if (remoteTxs.isNotEmpty()) {
                val records = transactionHistory.getTransactions()
                _transactions.value = records.map { it.toTransactionItem() }
            }
        } catch (_: Exception) {
            // silent — local cache is sufficient for offline
        }
    }
}
