package com.mdaopay.app.feature.receive.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.blockchain.WalletResult
import com.mdaopay.app.core.datastore.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val walletManager: WalletManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address.asStateFlow()

    private val _nickname = MutableStateFlow<String?>(null)
    val nickname: StateFlow<String?> = _nickname.asStateFlow()

    init {
        loadWalletInfo()
    }

    private fun loadWalletInfo() {
        when (val result = walletManager.loadWallet()) {
            is WalletResult.Success -> {
                _address.value = result.wallet.address
                viewModelScope.launch {
                    _nickname.value = userPreferences.getNickname()
                }
            }
            is WalletResult.Error -> {
                _address.value = null
                _nickname.value = null
            }
        }
    }
}
