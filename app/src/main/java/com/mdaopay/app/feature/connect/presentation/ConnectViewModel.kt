package com.mdaopay.app.feature.connect.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.security.BiometricAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DappInfo(
    val name: String,
    val url: String,
    val iconUrl: String = "",
    val permissions: List<String> = emptyList(),
    val spendingLimit: String? = null
)

sealed class ConnectState {
    data object Idle : ConnectState()
    data object Authenticating : ConnectState()
    data object Connected : ConnectState()
    data class Error(val message: String) : ConnectState()
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val biometricAuth: BiometricAuthManager
) : ViewModel() {

    private val _dapp = MutableStateFlow(
        DappInfo(
            name = "MDAO dApp",
            url = "https://dapp.mdaopay.com",
            permissions = listOf("View wallet balance", "Request transactions", "Read address"),
            spendingLimit = "500 MDAO / day"
        )
    )
    val dapp: StateFlow<DappInfo> = _dapp.asStateFlow()

    private val _state = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val state: StateFlow<ConnectState> = _state.asStateFlow()

    fun connect(activity: androidx.fragment.app.FragmentActivity) {
        if (_state.value == ConnectState.Authenticating) return
        _state.value = ConnectState.Authenticating

        biometricAuth.authenticate(
            activity = activity,
            title = "Confirm connection",
            subtitle = _dapp.value.name,
            onResult = { result ->
                when (result) {
                    is Result.Success -> _state.value = ConnectState.Connected
                    is Result.Error -> {
                        val msg = when (result.error) {
                            is AppError.BiometricCancelled -> "Cancelled"
                            else -> "Authentication failed"
                        }
                        _state.value = ConnectState.Error(msg)
                    }
                    is Result.Loading -> {}
                }
            }
        )
    }

    fun revokeAccess() {
        _state.value = ConnectState.Idle
    }

    fun dismissError() {
        _state.value = ConnectState.Idle
    }
}
