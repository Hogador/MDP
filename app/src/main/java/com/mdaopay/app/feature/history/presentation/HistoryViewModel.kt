package com.mdaopay.app.feature.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.datastore.TransactionHistory
import com.mdaopay.app.feature.home.domain.model.TransactionItem
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
    private val transactionHistory: TransactionHistory
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
            } catch (e: Exception) {
                _error.value = "Не удалось загрузить историю"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
