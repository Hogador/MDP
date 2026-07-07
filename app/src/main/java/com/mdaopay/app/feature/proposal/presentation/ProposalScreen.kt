package com.mdaopay.app.feature.proposal.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.core.ui.components.GradientBackground
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.components.MDAOButtonVariant
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.feature.proposal.domain.ProposalSummary

@Composable
fun ProposalScreen(
    onBack: () -> Unit,
    viewModel: ProposalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val ext = MaterialTheme.extended
    val d = ext.themeColors

    GradientBackground {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            MDAOTopBar(title = "DAO Proposals", onBack = onBack)

            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Загрузка...", color = d.text2, fontFamily = MarsFont)
                }
                state.error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Ошибка", color = ext.danger, fontFamily = MarsFont)
                        Spacer(Modifier.height(12.dp))
                        MDAOButton(text = "Повторить", onClick = { viewModel.load() }, variant = MDAOButtonVariant.Secondary)
                    }
                }
                state.proposals.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет предложений", color = d.text2, fontFamily = MarsFont)
                }
                else -> {
                    // Vote status snackbar
                    val vs by viewModel.voteState.collectAsState()
                    if (vs.voteTxHash != null) {
                        LaunchedEffect(vs.voteTxHash) {
                            kotlinx.coroutines.delay(3000)
                            viewModel.clearVoteState()
                        }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(state.proposals, key = { it.id }) { proposal ->
                            ProposalCard(
                                proposal = proposal,
                                isVoting = vs.isVoting && vs.votingProposalId == proposal.id,
                                voteError = if (vs.votingProposalId == proposal.id) vs.voteError else null,
                                voteTxHash = if (vs.votingProposalId == proposal.id) vs.voteTxHash else null,
                                onVote = { support -> viewModel.castVote(proposal.id, support) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: ProposalSummary,
    isVoting: Boolean = false,
    voteError: String? = null,
    voteTxHash: String? = null,
    onVote: (support: Int) -> Unit = {}
) {
    val ext = MaterialTheme.extended
    val d = ext.themeColors

    val statusColor = when (proposal.status) {
        "active" -> ext.success
        "ended" -> ext.warning
        "executed" -> d.text2
        "cancelled" -> ext.danger
        else -> d.text3
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(d.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: ID + status
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${proposal.id}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = d.text, fontFamily = MarsFont)
                Text(
                    text = when (proposal.status) {
                        "active" -> "Active"
                        "ended" -> "Ended"
                        "executed" -> "Executed"
                        "cancelled" -> "Cancelled"
                        else -> proposal.status
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    fontFamily = MarsFont,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(statusColor.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // Description
            Text(
                text = proposal.description,
                fontSize = 13.sp,
                color = d.text,
                fontFamily = MarsFont,
                lineHeight = 18.sp,
                maxLines = 3
            )

            // Vote bars
            if (proposal.totalVotes > 0) {
                val forPct = if (proposal.totalVotes > 0) proposal.forVotes * 100f / proposal.totalVotes else 0f
                val againstPct = if (proposal.totalVotes > 0) proposal.againstVotes * 100f / proposal.totalVotes else 0f
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.weight(forPct / 100f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ext.success))
                    Box(Modifier.weight(againstPct / 100f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ext.danger))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("For: ${proposal.forVotes}", fontSize = 11.sp, color = d.text2, fontFamily = MarsFont)
                    Text("Against: ${proposal.againstVotes}", fontSize = 11.sp, color = d.text2, fontFamily = MarsFont)
                }
            } else {
                Text("No votes yet", fontSize = 12.sp, color = d.text3, fontFamily = MarsFont)
            }

            // Proposer + deadline
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("by ${proposal.proposer}", fontSize = 10.sp, color = d.text3, fontFamily = MarsFont)
                if (proposal.deadline > 0) {
                    val remaining = (proposal.deadline * 1000 - System.currentTimeMillis()) / 1000 / 3600
                    val timeText = if (remaining > 0) "${remaining}h left" else "Ended"
                    Text(timeText, fontSize = 10.sp, color = d.text3, fontFamily = MarsFont)
                }
            }

            // Vote buttons (active proposals only)
            if (proposal.status == "active") {
                if (voteTxHash != null) {
                    Text("Vote sent: ${voteTxHash.take(10)}...", fontSize = 11.sp, color = ext.success, fontFamily = MarsFont)
                } else if (voteError != null) {
                    Text("Error: $voteError", fontSize = 11.sp, color = ext.danger, fontFamily = MarsFont)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MDAOButton(
                            text = if (isVoting) "..." else "For",
                            onClick = { HapticManager.light(); onVote(1) },
                            variant = MDAOButtonVariant.Primary,
                            modifier = Modifier.weight(1f),
                            enabled = !isVoting
                        )
                        MDAOButton(
                            text = if (isVoting) "..." else "Against",
                            onClick = { HapticManager.light(); onVote(0) },
                            variant = MDAOButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                            enabled = !isVoting
                        )
                    }
                }
            }
        }
    }
}
