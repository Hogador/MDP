package com.mdaopay.app.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.webkit.WebView
import com.mdaopay.app.core.blockchain.EthereumProviderInjector
import com.mdaopay.app.core.blockchain.BlockchainRepository
import com.mdaopay.app.core.blockchain.EtherscanRepository
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.blockchain.WalletResult
import com.mdaopay.app.core.datastore.Contact
import com.mdaopay.app.core.datastore.ContactsStore
import com.mdaopay.app.core.datastore.TransactionHistory
import com.mdaopay.app.core.datastore.TransactionRecord
import com.mdaopay.app.core.datastore.UserPreferences
import com.mdaopay.app.core.network.ConnectivityMonitor
import com.mdaopay.app.feature.home.domain.model.TransactionItem
import com.mdaopay.app.feature.home.domain.model.TxStatus
import com.mdaopay.app.feature.home.domain.model.WalletState
import com.mdaopay.app.feature.home.domain.model.toTransactionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.math.BigDecimal
import javax.inject.Inject

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Ready(
        val wallet: WalletState,
        val recentTransactions: List<TransactionItem>,
        val contacts: List<Contact> = emptyList(),
        val isConnected: Boolean = true,
        val displayName: String = "",
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val walletManager: WalletManager,
    private val blockchainRepository: BlockchainRepository,
    private val transactionHistory: TransactionHistory,
    private val etherscanRepository: EtherscanRepository,
    private val contactsStore: ContactsStore,
    private val connectivityMonitor: ConnectivityMonitor,
    private val ethereumProviderInjector: EthereumProviderInjector,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadWallet()
    }

    fun loadWallet() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            try {
                withTimeout(45_000L) {
                    loadWalletInternal()
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _uiState.value = HomeUiState.Error(
                    "Connection timed out. Check your internet and RPC status."
                )
            } catch (e: Throwable) {
                _uiState.value = HomeUiState.Error(
                    "Unexpected error: ${e.message ?: "unknown"}"
                )
            }
        }
    }

    private suspend fun loadWalletInternal() {
        val walletResult = walletManager.loadWallet()
        if (walletResult is WalletResult.Error) {
            _uiState.value = HomeUiState.Error(walletResult.message)
            return
        }

        val wallet = (walletResult as WalletResult.Success).wallet
        val nickname = userPreferences.getNickname() ?: "user"
        val socialName = userPreferences.getSocialName()
        val displayName = socialName ?: nickname

        val usdtBalance = blockchainRepository.getUsdtBalance(wallet.address)
        val mdaoBalance = blockchainRepository.getMdaoBalance(wallet.address)

        val ethBalance = blockchainRepository.getEthBalance(wallet.address)
        val balanceEth = if (ethBalance.isSuccess) ethBalance.getOrNull()!!
            else BigDecimal.ZERO
        val balanceUsdt = if (usdtBalance.isSuccess) usdtBalance.getOrNull()!!
            else BigDecimal.ZERO
        val balanceMdao = if (mdaoBalance.isSuccess) mdaoBalance.getOrNull()!!
            else BigDecimal.ZERO
        val isOnline = usdtBalance.isSuccess || mdaoBalance.isSuccess

        val records = transactionHistory.getTransactions()
        val localTxs = records.map { it.toTransactionItem() }

        val contacts = contactsStore.getContacts()

        val remoteTxs = etherscanRepository.fetchTransactions(wallet.address)
        val remoteItems = remoteTxs.map { it.toTransactionItem(wallet.address) }

        val merged = (localTxs + remoteItems)
            .distinctBy { it.txHash }
            .sortedByDescending { it.timestamp }
            .take(20)

        _uiState.value = HomeUiState.Ready(
            wallet = WalletState(
                nickname = nickname,
                address = wallet.address,
                balanceEth = balanceEth,
                balanceUsdt = balanceUsdt,
                balanceMdao = balanceMdao,
                isOnline = isOnline,
            ),
            recentTransactions = merged,
            contacts = contacts,
            isConnected = connectivityMonitor.isOnline.value,
            displayName = displayName,
        )
    }

    fun injectEthereumProvider(webView: WebView) {
        ethereumProviderInjector.inject(webView)
    }

    fun onEthereumBridgeNavigation(webView: WebView, url: String?) {
        ethereumProviderInjector.onNavigation(webView, url)
    }

    private fun com.mdaopay.app.core.blockchain.RemoteTx.toTransactionItem(userAddress: String) = TransactionItem(
        id = txHash,
        nickname = "",
        amountUsdt = if (from.equals(userAddress, ignoreCase = true)) amount.negate() else amount,
        timestamp = timestamp,
        status = if (isConfirmed && !isError) TxStatus.CONFIRMED else if (isError) TxStatus.FAILED else TxStatus.PENDING,
        txHash = txHash,
        tokenSymbol = tokenSymbol,
        counterparty = if (from.equals(userAddress, ignoreCase = true)) to else from,
        isExternal = true
    )
}
