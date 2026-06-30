package com.mdaopay.app.feature.assets.presentation

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.core.common.formatTxTime
import com.mdaopay.app.core.common.toDisplayAmount
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.feature.home.domain.model.TransactionItem
import com.mdaopay.app.feature.home.domain.model.TxStatus
import java.math.BigDecimal

@Composable
fun AssetDetailsScreen(
    symbol: String,
    balance: String,
    onBack: () -> Unit,
    onSendClick: () -> Unit = {},
    onReceiveClick: () -> Unit = {},
    viewModel: AssetDetailsViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    val accentColor = when (symbol) {
        "MDAO" -> Color(0xFF7B4DFF)
        "USDT" -> Color(0xFF00D68F)
        "ETH" -> Color(0xFF627EEA)
        else -> extended.accent
    }

    LaunchedEffect(Unit) {
        viewModel.loadTransactions()
    }

    Box(modifier = Modifier.fillMaxSize().background(tc.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tc.tile),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\u2190", fontSize = 20.sp, color = tc.text)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = symbol,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = tc.text,
                    letterSpacing = (-0.5).sp
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(tc.card)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = symbol.take(1),
                                    fontFamily = MarsFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = accentColor
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = symbol,
                                fontFamily = MarsFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\u25B2 ${balance.toBigDecimalOrNull()?.toDisplayAmount(
                                    if (symbol == "ETH" || symbol == "Sepolia ETH") 6 else 2
                                ) ?: "0"}",
                                fontFamily = MarsMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = tc.text,
                                letterSpacing = (-1.2).sp
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActionChip(
                            label = "Отправить",
                            icon = "\u2191",
                            onClick = { HapticManager.light(); onSendClick() },
                            modifier = Modifier.weight(1f)
                        )
                        ActionChip(
                            label = "Получить",
                            icon = "\u2193",
                            onClick = { HapticManager.light(); onReceiveClick() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Последние операции",
                        fontFamily = MarsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = tc.text2,
                        letterSpacing = 1.sp
                    )
                }

                when {
                    isLoading -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Загрузка операций...",
                                    fontFamily = MarsFont,
                                    fontSize = 13.sp,
                                    color = tc.text2
                                )
                            }
                        }
                    }
                    error != null -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = error!!,
                                        fontFamily = MarsFont,
                                        fontSize = 13.sp,
                                        color = extended.danger
                                    )
                                }
                            }
                        }
                    }
                    transactions.isEmpty() -> {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Операций пока нет",
                                    fontFamily = MarsFont,
                                    fontSize = 13.sp,
                                    color = tc.text2
                                )
                            }
                        }
                    }
                    else -> {
                        items(transactions.take(10), key = { it.id }) { tx ->
                            AssetTransactionRow(tx = tx)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tc.card)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 18.sp, color = extended.accent)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontFamily = MarsFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = tc.text2
            )
        }
    }
}

@Composable
private fun AssetTransactionRow(tx: TransactionItem) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors
    val isIncoming = tx.amountUsdt > BigDecimal.ZERO
    val amountColor = if (isIncoming) extended.success else tc.text
    val prefix = if (isIncoming) "+" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tc.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(extended.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = tx.nickname.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = extended.accent
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val displayName = if (tx.nickname.isNotBlank()) tx.nickname else tx.counterparty.let {
                if (it.length > 10) "${it.take(6)}...${it.takeLast(4)}" else it
            }
            val isAddress = tx.nickname.isBlank()
            Text(
                text = displayName,
                fontFamily = if (isAddress) MarsMono else MarsFont,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = tc.text
            )
            Text(
                text = tx.timestamp.formatTxTime(),
                fontFamily = MarsFont,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = tc.text2
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$prefix${tx.amountUsdt.abs().toDisplayAmount()} ${tx.tokenSymbol}",
                fontFamily = MarsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = amountColor
            )
            if (tx.status == TxStatus.PENDING) {
                Text(
                    text = "ожидает",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = tc.text2
                )
            }
        }
    }
}
