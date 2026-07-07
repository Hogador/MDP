package com.mdaopay.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import com.mdaopay.app.core.blockchain.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val walletManager: WalletManager
) : ViewModel() {

    private val _mnemonicWords = MutableStateFlow<List<String>>(emptyList())
    val mnemonicWords: StateFlow<List<String>> = _mnemonicWords.asStateFlow()

    private val _hasWallet = MutableStateFlow(false)
    val hasWallet: StateFlow<Boolean> = _hasWallet.asStateFlow()

    init {
        val wallet = walletManager.getWalletData()
        if (wallet != null) {
            _mnemonicWords.value = wallet.mnemonic.split(" ")
            _hasWallet.value = true
        }
    }

    fun verifyWord(index: Int, input: String): Boolean {
        val words = _mnemonicWords.value
        return index in words.indices && words[index] == input.trim().lowercase()
    }
}
