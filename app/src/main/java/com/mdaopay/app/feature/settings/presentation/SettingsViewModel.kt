package com.mdaopay.app.feature.settings.presentation

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.blockchain.WalletResult
import com.mdaopay.app.core.datastore.UserPreferences
import com.mdaopay.app.core.security.AppLockManager
import com.mdaopay.app.core.security.RecoveryShareManager
import com.mdaopay.app.core.security.SocialAuthManager
import com.mdaopay.app.core.security.SocialUser
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.common.toUserMessage
import com.mdaopay.app.core.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val walletManager: WalletManager,
    private val userPreferences: UserPreferences,
    private val appLockManager: AppLockManager,
    private val recoveryShareManager: RecoveryShareManager,
    private val socialAuthManager: SocialAuthManager,
    private val app: Application
) : ViewModel() {

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address.asStateFlow()

    private val _socialUser = MutableStateFlow<SocialUser?>(null)
    val socialUser: StateFlow<SocialUser?> = _socialUser.asStateFlow()

    private val _socialError = MutableStateFlow<String?>(null)
    val socialError: StateFlow<String?> = _socialError.asStateFlow()

    private val _appLockEnabled = MutableStateFlow(true)
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _appVersion = MutableStateFlow("")
    val appVersion: StateFlow<String> = _appVersion.asStateFlow()

    init {
        loadWalletAddress()
        loadLockState()
        loadVersion()
        loadSocialUser()
    }

    fun toggleLock(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setAppLockEnabled(enabled)
            appLockManager.setLockEnabled(enabled)
            _appLockEnabled.value = enabled
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            userPreferences.setAppTheme(when (theme) {
                AppTheme.LIGHT -> "light"
                AppTheme.DARK -> "dark"
                AppTheme.AMOLED -> "amoled"
                AppTheme.SYSTEM -> "system"
            })
        }
    }

    fun clearWallet() {
        walletManager.clearWallet()
    }

    fun createNewWallet() {
        walletManager.clearWallet()
        recoveryShareManager.deleteShares()
        viewModelScope.launch {
            userPreferences.resetOnboarding()
        }
    }

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _socialError.value = null
            when (val result = socialAuthManager.signInWithGoogle(activity)) {
                is Result.Success -> _socialUser.value = result.data
                is Result.Error -> _socialError.value = result.error.toUserMessage()
                else -> {}
            }
        }
    }

    fun signInWithApple(activity: Activity) {
        viewModelScope.launch {
            _socialError.value = null
            when (val result = socialAuthManager.signInWithApple(activity)) {
                is Result.Success -> _socialUser.value = result.data
                is Result.Error -> _socialError.value = result.error.toUserMessage()
                else -> {}
            }
        }
    }

    fun signOutSocial() {
        viewModelScope.launch {
            socialAuthManager.signOut()
            _socialUser.value = null
        }
    }

    private fun loadSocialUser() {
        viewModelScope.launch {
            _socialUser.value = socialAuthManager.getCurrentUser()
        }
    }

    private fun loadWalletAddress() {
        _address.value = walletManager.loadWallet().mapNullable { it.address }
    }

    private fun loadLockState() {
        viewModelScope.launch {
            _appLockEnabled.value = userPreferences.isAppLockEnabled()
        }
    }

    private fun loadVersion() {
        try {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            _appVersion.value = info.versionName ?: ""
        } catch (_: PackageManager.NameNotFoundException) {
            _appVersion.value = ""
        }
    }
}
