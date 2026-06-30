package com.mdaopay.app.feature.walletconnect.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.blockchain.WCProposal
import com.mdaopay.app.core.blockchain.WCSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class WalletConnectViewModel @Inject constructor() : ViewModel() {

    private val _sessions = MutableStateFlow<List<WCSession>>(emptyList())
    val sessions: StateFlow<List<WCSession>> = _sessions.asStateFlow()

    private val _proposals = MutableStateFlow<List<WCProposal>>(emptyList())
    val proposals: StateFlow<List<WCProposal>> = _proposals.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun initialize() {}

    suspend fun pair(uri: String): Boolean = false

    fun approve(proposal: WCProposal) {}
    fun reject(proposal: WCProposal) {}
    fun disconnect(topic: String) {}
}
