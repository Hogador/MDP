package com.mdaopay.app.feature.notifications.presentation

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.extended

private enum class NotifCardType {
    TX_IN, TX_OUT, SWAP, SECURITY, NEWS
}

private data class NotifCardData(
    val id: String,
    val type: NotifCardType,
    val title: String,
    val text: String,
    val time: String,
    val amount: String = "",
    val actionLabel: String = "",
    val isUnread: Boolean = true
)

private val sampleNotifs = listOf(
    NotifCardData("1", NotifCardType.TX_IN, "Входящий перевод", "От @bob \u00B7 BNB Smart Chain", "14:32", "+150.00 USDT", isUnread = true),
    NotifCardData("2", NotifCardType.TX_OUT, "Исходящий перевод", "\u2192 @alice \u00B7 подтверждено", "11:08", "\u221250.00 MDAO", isUnread = true),
    NotifCardData("3", NotifCardType.SECURITY, "Вход в аккаунт", "Новый вход с устройства iPhone 15 Pro \u00B7 Москва, RU", "09:45", isUnread = true),
    NotifCardData("4", NotifCardType.SWAP, "Обмен завершён", "USDT \u2192 MDAO по курсу 0.183", "22:14", "\u2212100.00 USDT"),
    NotifCardData("5", NotifCardType.TX_IN, "Входящий перевод", "От @alice \u00B7 BNB Smart Chain", "18:50", "+500.00 USDC"),
    NotifCardData("6", NotifCardType.TX_OUT, "Транзакция не удалась", "\u2192 @bob \u00B7 недостаточно газа. Средства возвращены.", "12:20", actionLabel = "Повторить"),
    NotifCardData("7", NotifCardType.NEWS, "Новое обновление MDAO", "Версия 1.0.0 уже доступна.", "19 июня", actionLabel = "Обновить"),
    NotifCardData("8", NotifCardType.SWAP, "Цена MDAO выросла", "+7.2% за 24 часа \u00B7 текущая цена $0.366", "17 июня"),
)

@Composable
fun NotificationsCenterScreen(
    onBack: () -> Unit
) {
    var unreadIds by remember { mutableStateOf(sampleNotifs.filter { it.isUnread }.map { it.id }.toSet()) }
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tc.bg)
    ) {
        MDAOTopBar(
            title = "Уведомления",
            onBack = onBack,
            action = {
                Text(text = "\u2699", fontSize = 18.sp, color = tc.text)
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            if (unreadIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\u2713 Отметить все прочитанными",
                            fontFamily = MarsFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = extended.accent
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "СЕГОДНЯ",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = tc.text2,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            sampleNotifs.take(3).forEach { notif ->
                NotifCard(
                    data = notif,
                    isUnread = notif.id in unreadIds,
                    onAction = { /* handle action */ }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text(
                text = "ВЧЕРА",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = tc.text2,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            sampleNotifs.drop(3).forEach { notif ->
                NotifCard(
                    data = notif,
                    isUnread = notif.id in unreadIds,
                    onAction = { /* handle action */ }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NotifCard(
    data: NotifCardData,
    isUnread: Boolean,
    onAction: () -> Unit
) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    val iconColor = when (data.type) {
        NotifCardType.TX_IN -> extended.success
        NotifCardType.TX_OUT -> extended.danger
        NotifCardType.SWAP -> extended.accent
        NotifCardType.SECURITY -> extended.warning
        NotifCardType.NEWS -> tc.text2
    }
    val iconBg = when (data.type) {
        NotifCardType.TX_IN -> extended.successSoft
        NotifCardType.TX_OUT -> extended.dangerSoft
        NotifCardType.SWAP -> extended.accentSoft
        NotifCardType.SECURITY -> extended.warningSoft
        NotifCardType.NEWS -> tc.tile
    }
    val iconText = when (data.type) {
        NotifCardType.TX_IN -> "\u2193"
        NotifCardType.TX_OUT -> "\u2191"
        NotifCardType.SWAP -> "\u21C4"
        NotifCardType.SECURITY -> "\u26A0"
        NotifCardType.NEWS -> "\uD83D\uDCE2"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tc.card)
            .padding(start = if (isUnread) 18.dp else 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (isUnread) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(extended.accent)
                    .align(Alignment.Top)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconText, fontSize = 18.sp, color = iconColor)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = data.title,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = tc.text,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = data.time,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = tc.text2
                )
            }
            Text(
                text = data.text,
                fontFamily = MarsFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = tc.text2,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (data.amount.isNotBlank()) {
                Text(
                    text = data.amount,
                    fontFamily = MarsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (data.type == NotifCardType.TX_IN) extended.success
                    else if (data.type == NotifCardType.TX_OUT) extended.danger
                    else tc.text,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            if (data.actionLabel.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(extended.accentSoft)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = data.actionLabel,
                        fontFamily = MarsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = extended.accent
                    )
                }
            }
        }
    }
}
