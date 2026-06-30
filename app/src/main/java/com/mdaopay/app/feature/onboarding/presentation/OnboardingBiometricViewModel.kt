package com.mdaopay.app.feature.onboarding.presentation

import android.os.Build
import androidx.lifecycle.ViewModel
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.common.isRetryable
import com.mdaopay.app.core.common.toUserMessage
import com.mdaopay.app.core.security.BiometricAuthManager
import com.mdaopay.app.core.security.BiometricAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed class BiometricUiState {
    data object Idle : BiometricUiState()
    data object Authenticating : BiometricUiState()
    data object Success : BiometricUiState()
    data object AutoSkipped : BiometricUiState()
    data class Error(val message: String, val canRetry: Boolean) : BiometricUiState()
    data object BiometricUnavailable : BiometricUiState()
}

@HiltViewModel
class OnboardingBiometricViewModel @Inject constructor(
    val biometricManager: BiometricAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<BiometricUiState>(BiometricUiState.Idle)
    val uiState: StateFlow<BiometricUiState> = _uiState.asStateFlow()

    fun checkBiometricAvailability() {
        if (isHuawei()) {
            _uiState.value = BiometricUiState.AutoSkipped
            return
        }
        when (biometricManager.isBiometricAvailable()) {
            is BiometricAvailability.Available -> { }
            else -> _uiState.value = BiometricUiState.BiometricUnavailable
        }
    }

    private fun isHuawei(): Boolean =
        Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) ||
        Build.BRAND.equals("HONOR", ignoreCase = true)

    fun onAuthenticateClick() {
        _uiState.value = BiometricUiState.Authenticating
    }

    fun onBiometricResult(result: Result<Unit>) {
        _uiState.value = when (result) {
            is Result.Success -> BiometricUiState.Success
            is Result.Error -> when (result.error) {
                AppError.BiometricCancelled -> BiometricUiState.Idle
                AppError.BiometricNotAvailable -> BiometricUiState.BiometricUnavailable
                else -> BiometricUiState.Error(
                    message = result.error.toUserMessage(),
                    canRetry = result.error.isRetryable()
                )
            }
            else -> BiometricUiState.Idle
        }
    }

    fun onRetry() {
        _uiState.value = BiometricUiState.Authenticating
    }
}
