package com.mdaopay.app.feature.assets.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.datastore.TransactionHistory
import com.mdaopay.app.feature.home.domain.model.TransactionItem
import com.mdaopay.app.feature.home.domain.model.toTransactionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssetDetailsViewModel @Inject constructor(
    private val transactionHistory: TransactionHistory
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<TransactionItem>>(emptyList())
    val transactions: StateFlow<List<TransactionItem>> = _transactions.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadTransactions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val records = transactionHistory.getTransactions()
                _transactions.value = records.map { it.toTransactionItem() }
            } catch (e: Exception) {
                _error.value = "Не удалось загрузить операции"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
