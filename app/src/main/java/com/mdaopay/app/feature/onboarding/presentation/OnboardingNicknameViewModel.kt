package com.mdaopay.app.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.blockchain.NicknameResolver
import com.mdaopay.app.core.blockchain.WalletData
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.blockchain.WalletResult
import com.mdaopay.app.core.common.NicknameGenerator
import com.mdaopay.app.core.datastore.UserPreferences
import com.mdaopay.app.core.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NicknameUiState {
    data object Loading : NicknameUiState()
    data class Ready(
        val options: List<String>,
        val selected: String,
        val isConfirmed: Boolean = false
    ) : NicknameUiState()
    data class WalletError(val message: String) : NicknameUiState()
}

@HiltViewModel
class OnboardingNicknameViewModel @Inject constructor(
    private val generator: NicknameGenerator,
    private val appLockManager: AppLockManager,
    private val userPreferences: UserPreferences,
    private val walletManager: WalletManager,
    private val nicknameResolver: NicknameResolver
) : ViewModel() {

    private var walletData: WalletData? = null
    private val _uiState = MutableStateFlow<NicknameUiState>(NicknameUiState.Loading)
    val uiState: StateFlow<NicknameUiState> = _uiState.asStateFlow()

    private val _scenario = MutableStateFlow("standard")
    val scenario: StateFlow<String> = _scenario.asStateFlow()

    fun setScenario(s: String) { _scenario.value = s }

    init { initWallet() }

    fun generateOptions() = refresh()

    private fun initWallet() {
        viewModelScope.launch {
            _uiState.value = NicknameUiState.Loading
            val result = if (walletManager.hasWallet()) {
                walletManager.loadWallet()
            } else {
                walletManager.generateWallet()
            }
            when (result) {
                is WalletResult.Success -> {
                    walletData = result.wallet
                    showOptions()
                }
                is WalletResult.Error -> {
                    _uiState.value = NicknameUiState.WalletError(result.message)
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.value = NicknameUiState.Loading
            if (walletData == null) {
                val result = walletManager.generateWallet()
                when (result) {
                    is WalletResult.Success -> walletData = result.wallet
                    is WalletResult.Error -> {
                        _uiState.value = NicknameUiState.WalletError(result.message)
                        return@launch
                    }
                }
            }
            showOptions()
        }
    }

    private fun showOptions() {
        val wallet = walletData ?: return
        val deterministic = generator.generate(wallet.publicKeyBytes)
        val options = mutableListOf(deterministic)
        options.addAll(generator.generateOptions(3).filter { it != deterministic })
        _uiState.value = NicknameUiState.Ready(options = options, selected = deterministic)
    }

    fun selectNickname(nickname: String) {
        (_uiState.value as? NicknameUiState.Ready)?.let {
            _uiState.value = it.copy(selected = nickname)
        }
    }

    fun confirmNickname() {
        val state = _uiState.value as? NicknameUiState.Ready ?: return
        val wallet = walletData ?: return
        viewModelScope.launch {
            try {
                walletManager.saveMnemonic(wallet.mnemonic)
                userPreferences.setNickname(state.selected)
                userPreferences.setRecoveryScenario(_scenario.value)
                nicknameResolver.register(state.selected, wallet.address)
                userPreferences.setOnboardingComplete()
                appLockManager.setOnboarded()
                _uiState.value = state.copy(isConfirmed = true)
            } catch (e: Exception) {
                _uiState.value = NicknameUiState.WalletError(
                    "Wallet creation failed: ${e.message}"
                )
            }
        }
    }
}
