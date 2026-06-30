package com.mdaopay.app.feature.exchanger.presentation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.extended

@Composable
fun ExchangerScreen(
    onBuyClick: (url: String, title: String) -> Unit = { _, _ -> },
    onSellClick: (url: String, title: String) -> Unit = { _, _ -> }
) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tc.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 48.dp)
    ) {
        Text(
            text = "Обменник",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = tc.text,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Покупка и продажа USDT за рубли через СБП",
            fontFamily = MarsFont,
            fontSize = 13.sp,
            color = tc.text2
        )

        Spacer(modifier = Modifier.height(28.dp))

        ObmennikCard(
            onBuyClick = onBuyClick,
            onSellClick = onSellClick
        )
    }
}

@Composable
private fun ObmennikCard(
    onBuyClick: (url: String, title: String) -> Unit = { _, _ -> },
    onSellClick: (url: String, title: String) -> Unit = { _, _ -> }
) {
    val botUrl = "https://t.me/mommys_trader_bot"
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tc.card)
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(extended.successSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\u21C4", fontSize = 20.sp, color = extended.success)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "P2P Обмен USDT",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = tc.text
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Покупка и продажа USDT за рубли через СБП",
                fontFamily = MarsFont,
                fontSize = 12.sp,
                color = tc.text2
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MDAOButton(
                    text = "Купить USDT",
                    onClick = {
                        HapticManager.light()
                        onBuyClick("$botUrl?start=buy_usdt", "Купить USDT")
                    },
                    modifier = Modifier.weight(1f)
                )
                MDAOButton(
                    text = "Продать USDT",
                    onClick = {
                        HapticManager.light()
                        onSellClick("$botUrl?start=sell_usdt", "Продать USDT")
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
