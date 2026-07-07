package com.mdaopay.app.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.webkit.WebView
import com.mdaopay.app.core.blockchain.EthereumProviderInjector
import com.mdaopay.app.core.blockchain.BlockchainRepository
import com.mdaopay.app.core.blockchain.EtherscanRepository
import com.mdaopay.app.core.blockchain.NetworkConfig
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        if (!NetworkConfig.isConfigured()) {
            _uiState.value = HomeUiState.Error(
                "Contracts not deployed. Set MDAO_CONTRACT and SOCIAL_RECOVERY_MODULE in NetworkConfig."
            )
            return
        }

        val walletResult = walletManager.loadWallet()
        if (walletResult is WalletResult.Error) {
            _uiState.value = HomeUiState.Error(walletResult.message)
            return
        }

        val wallet = (walletResult as WalletResult.Success).wallet
        val nickname = userPreferences.getNickname() ?: "user"
        val socialName = userPreferences.getSocialName()
        val displayName = socialName ?: nickname

        // ponytail: parallel fetch balances, txs, contacts — independent calls
        val results = coroutineScope {
            val usdtDeferred = async { blockchainRepository.getUsdtBalance(wallet.address) }
            val mdaoDeferred = async { blockchainRepository.getMdaoBalance(wallet.address) }
            val ethDeferred = async { blockchainRepository.getEthBalance(wallet.address) }
            val txsDeferred = async { transactionHistory.getTransactions() }
            val contactsDeferred = async { contactsStore.getContacts() }
            val remoteTxsDeferred = async { etherscanRepository.fetchTransactions(wallet.address) }

            val usdtBalance = usdtDeferred.await()
            val mdaoBalance = mdaoDeferred.await()
            val ethBalance = ethDeferred.await()
            val records = txsDeferred.await()
            val contacts = contactsDeferred.await()
            val remoteTxs = remoteTxsDeferred.await()

            val balanceEth = ethBalance.getOrNull() ?: BigDecimal.ZERO
            val balanceUsdt = usdtBalance.getOrNull() ?: BigDecimal.ZERO
            val balanceMdao = mdaoBalance.getOrNull() ?: BigDecimal.ZERO
            val isOnline = usdtBalance.isSuccess || mdaoBalance.isSuccess

            val localTxs = records.map { it.toTransactionItem() }
            val remoteItems = remoteTxs.map { it.toTransactionItem(wallet.address) }
            val merged = (localTxs + remoteItems)
                .distinctBy { it.txHash }
                .sortedByDescending { it.timestamp }
                .take(20)

            Triple(
                WalletState(nickname, wallet.address, balanceEth, balanceUsdt, balanceMdao, isOnline),
                merged,
                contacts
            )
        }

        _uiState.value = HomeUiState.Ready(
            wallet = results.first,
            recentTransactions = results.second,
            contacts = results.third,
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
