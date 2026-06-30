package com.mdaopay.app.feature.history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.core.common.formatTxTime
import com.mdaopay.app.core.common.toDisplayAmount
import com.mdaopay.app.core.ui.components.GradientBackground
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.feature.home.domain.model.TransactionItem
import com.mdaopay.app.feature.home.domain.model.TxStatus
import java.math.BigDecimal

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val currentFilter by viewModel.filter.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val d = MaterialTheme.extended.themeColors

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp)
        ) {
            MDAOTopBar(
                title = "\u0418\u0441\u0442\u043E\u0440\u0438\u044F",
                onBack = onBack
            )

            FilterChipsRow(
                currentFilter = currentFilter,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430\u2026",
                            fontSize = 14.sp,
                            color = d.text2
                        )
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = error!!,
                                fontSize = 14.sp,
                                color = MaterialTheme.extended.danger,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "\u041F\u043E\u043F\u0440\u043E\u0431\u043E\u0432\u0430\u0442\u044C \u0441\u043D\u043E\u0432\u0430",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.extended.accent,
                                modifier = Modifier.clickable { viewModel.loadTransactions() }
                            )
                        }
                    }
                }
                transactions.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(d.tile),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "\uD83D\uDCCB", fontSize = 28.sp)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "\u041E\u043F\u0435\u0440\u0430\u0446\u0438\u0439 \u043F\u043E\u043A\u0430 \u043D\u0435\u0442",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = d.text,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "\u041F\u0435\u0440\u0432\u043E\u0435 \u043F\u043E\u043F\u043E\u043B\u043D\u0435\u043D\u0438\u0435 \u0438\u043B\u0438 \u043E\u0442\u043F\u0440\u0430\u0432\u043A\u0430 \u043F\u043E\u044F\u0432\u0438\u0442\u0441\u044F \u0437\u0434\u0435\u0441\u044C",
                                fontSize = 13.sp,
                                color = d.text2,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        val grouped = groupByDate(transactions)
                        grouped.forEach { (section, txs) ->
                            item {
                                Text(
                                    text = section,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = d.text2,
                                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp)
                                )
                            }
                            items(txs, key = { it.id }) { tx ->
                                HistoryTransactionRow(tx = tx)
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

private fun groupByDate(transactions: List<TransactionItem>): List<Pair<String, List<TransactionItem>>> {
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    val sections = mutableListOf<Pair<String, List<TransactionItem>>>()
    val today = transactions.filter { now - it.timestamp < day }
    val yesterday = transactions.filter { now - it.timestamp in day until (2 * day) }
    val week = transactions.filter { now - it.timestamp in (2 * day) until (7 * day) }
    val older = transactions.filter { now - it.timestamp >= (7 * day) }
    if (today.isNotEmpty()) sections.add("\u0421\u0415\u0413\u041E\u0414\u041D\u042F" to today)
    if (yesterday.isNotEmpty()) sections.add("\u0412\u0427\u0415\u0420\u0410" to yesterday)
    if (week.isNotEmpty()) sections.add("\u041D\u0410 \u042D\u0422\u041E\u0419 \u041D\u0415\u0414\u0415\u041B\u0415" to week)
    if (older.isNotEmpty()) sections.add("\u0420\u0410\u041D\u0415\u0415" to older)
    return sections
}

@Composable
private fun FilterChipsRow(
    currentFilter: TransactionFilter,
    onFilterSelected: (TransactionFilter) -> Unit
) {
    val filters = TransactionFilter.entries
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        filters.forEach { filter ->
            val label = when (filter) {
                TransactionFilter.ALL -> "\u0412\u0441\u0435"
                TransactionFilter.INCOME -> "\u041F\u043E\u043B\u0443\u0447\u0435\u043D\u043E"
                TransactionFilter.OUTCOME -> "\u041E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u043E"
            }
            val isSelected = filter == currentFilter
            val bgColor = if (isSelected) a else d.tile
            val textColor = if (isSelected) androidx.compose.ui.graphics.Color.White else d.text2

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(bgColor)
                    .clickable {
                        HapticManager.light()
                        onFilterSelected(filter)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun HistoryTransactionRow(tx: TransactionItem) {
    val d = MaterialTheme.extended.themeColors
    val extended = MaterialTheme.extended
    val isIncoming = tx.amountUsdt > BigDecimal.ZERO
    val amountColor = if (isIncoming) extended.success else extended.danger
    val prefix = if (isIncoming) "+" else "\u2212"
    val iconColor = if (isIncoming) extended.success else extended.danger
    val iconBg = if (isIncoming) extended.successSoft else extended.dangerSoft
    val iconLetter = if (isIncoming) "\u2193" else "\u2191"

    val isPending = tx.status == TxStatus.PENDING
    val isFailed = tx.status == TxStatus.FAILED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(d.card)
            .clickable { }
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconLetter, color = iconColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isIncoming) "\u041E\u0442 @${tx.nickname.ifBlank { tx.counterparty.take(10) }}" else "\u2192 @${tx.nickname.ifBlank { tx.counterparty.take(10) }}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = d.text,
                    maxLines = 1
                )
                if (isPending) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(extended.warningSoft)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "\u0412 \u043E\u0431\u0440\u0430\u0431\u043E\u0442\u043A\u0435",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = extended.warning
                        )
                    }
                }
                if (isFailed) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(extended.dangerSoft)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = extended.danger
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(when (tx.tokenSymbol) {
                            "MDAO" -> androidx.compose.ui.graphics.Color(0xFF6D4AFF)
                            "USDT" -> androidx.compose.ui.graphics.Color(0xFF26A17B)
                            "BNB" -> androidx.compose.ui.graphics.Color(0xFFF0B90B)
                            "USDC" -> androidx.compose.ui.graphics.Color(0xFF2775CA)
                            else -> androidx.compose.ui.graphics.Color.Gray
                        })
                )
                Text(
                    text = "${tx.tokenSymbol} \u00B7 ${tx.timestamp.formatTxTime()}",
                    fontSize = 11.sp,
                    color = d.text2
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$prefix${tx.amountUsdt.abs().toDisplayAmount()} ${tx.tokenSymbol}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MarsMono,
                color = amountColor
            )
            Text(
                text = "\u2248 \$${tx.amountUsdt.abs().toDisplayAmount()}",
                fontSize = 11.sp,
                color = d.text2
            )
        }
    }
}
