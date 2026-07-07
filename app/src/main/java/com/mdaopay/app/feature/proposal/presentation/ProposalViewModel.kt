package com.mdaopay.app.feature.proposal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.feature.proposal.domain.ProposalRepository
import com.mdaopay.app.feature.proposal.domain.ProposalSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProposalUiState(
    val proposals: List<ProposalSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProposalViewModel @Inject constructor(
    private val repository: ProposalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProposalUiState())
    val state: StateFlow<ProposalUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = repository.getProposals()) {
                is Result.Success -> _state.value = ProposalUiState(proposals = result.data)
                is Result.Error -> _state.value = ProposalUiState(error = result.exception.message ?: "Unknown error")
            }
        }
    }
}
