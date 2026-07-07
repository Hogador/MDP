package com.mdaopay.app.feature.proposal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.common.toUserMessage
import com.mdaopay.app.feature.proposal.domain.ProposalRepository
import com.mdaopay.app.feature.proposal.domain.ProposalSummary
import com.mdaopay.app.feature.proposal.domain.VoteRepository
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

data class VoteUiState(
    val isVoting: Boolean = false,
    val votingProposalId: Long? = null,
    val votingSupport: Int? = null,
    val voteError: String? = null,
    val voteTxHash: String? = null
)

@HiltViewModel
class ProposalViewModel @Inject constructor(
    private val repository: ProposalRepository,
    private val voteRepository: VoteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProposalUiState())
    val state: StateFlow<ProposalUiState> = _state.asStateFlow()

    private val _voteState = MutableStateFlow(VoteUiState())
    val voteState: StateFlow<VoteUiState> = _voteState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = repository.getProposals()) {
                is Result.Success -> _state.value = ProposalUiState(proposals = result.data)
                is Result.Error -> _state.value = ProposalUiState(error = result.error.toUserMessage())
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun castVote(proposalId: Long, support: Int) {
        viewModelScope.launch {
            _voteState.value = VoteUiState(isVoting = true, votingProposalId = proposalId, votingSupport = support)
            when (val result = voteRepository.castVote(proposalId, support)) {
                is Result.Success -> {
                    _voteState.value = VoteUiState(voteTxHash = result.data)
                    load() // refresh proposals after vote
                }
                is Result.Error -> {
                    _voteState.value = VoteUiState(voteError = result.error.toUserMessage())
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun clearVoteState() {
        _voteState.value = VoteUiState()
    }
}
