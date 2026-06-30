package com.mdaopay.app.feature.states.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.components.MDAOButtonVariant
import com.mdaopay.app.core.ui.components.MDAOBottomSheet
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.extended

private data class ModalTrigger(
    val icon: String,
    val iconBg: String,
    val title: String,
    val subtitle: String
)

private val triggers = listOf(
    ModalTrigger(icon = "\u26A0\uFE0F", iconBg = "warning", title = "Подтверждение действия", subtitle = "Confirm / Cancel"),
    ModalTrigger(icon = "\u2714\uFE0F", iconBg = "accent", title = "Approve token spending", subtitle = "Смарт-контракт запрашивает доступ"),
    ModalTrigger(icon = "\uD83C\uDF10", iconBg = "neutral", title = "Switch network", subtitle = "Смена сети для dApp"),
    ModalTrigger(icon = "\uD83D\uDEE1\uFE0F", iconBg = "success", title = "Sign transaction", subtitle = "Подтверждение tx для dApp"),
    ModalTrigger(icon = "\uD83D\uDDD1\uFE0F", iconBg = "danger", title = "Delete wallet / contact", subtitle = "Необратимое действие"),
    ModalTrigger(icon = "\u2139\uFE0F", iconBg = "neutral", title = "Info dialog", subtitle = "Информационное сообщение")
)

private val triggerColors = mapOf(
    "warning" to Triple("\u26A0\uFE0F", "warning", "\u26A0\uFE0F"),
    "accent" to Triple("\u2714\uFE0F", "accent", "\u2714\uFE0F"),
    "neutral" to Triple("\uD83C\uDF10", "neutral", "\uD83C\uDF10"),
    "success" to Triple("\uD83D\uDEE1\uFE0F", "success", "\uD83D\uDEE1\uFE0F"),
    "danger" to Triple("\uD83D\uDDD1\uFE0F", "danger", "\uD83D\uDDD1\uFE0F")
)

@Composable
fun ModalsScreen(
    onBack: () -> Unit
) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    var showSheet by remember { mutableStateOf(false) }
    var sheetIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tc.bg)
    ) {
        MDAOTopBar(
            title = "Modals & Dialogs",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "TRIGGERS",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = tc.text2,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                triggers.forEachIndexed { index, trigger ->
                    ModalTriggerRow(
                        trigger = trigger,
                        onClick = {
                            sheetIndex = index
                            showSheet = true
                        }
                    )
                }
            }
        }

        if (showSheet) {
            ModalSheetContent(
                visible = showSheet,
                index = sheetIndex,
                onDismiss = { showSheet = false }
            )
        }
    }
}

@Composable
private fun ModalTriggerRow(trigger: ModalTrigger, onClick: () -> Unit) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    val iconColor = when (trigger.iconBg) {
        "warning" -> extended.warning
        "accent" -> extended.accent
        "danger" -> extended.danger
        "success" -> extended.success
        else -> tc.text
    }
    val iconBgColor = when (trigger.iconBg) {
        "warning" -> extended.warningSoft
        "accent" -> extended.accentSoft
        "danger" -> extended.dangerSoft
        "success" -> extended.successSoft
        else -> tc.tile
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tc.card)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(text = trigger.icon, fontSize = 16.sp, color = iconColor)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trigger.title,
                fontFamily = MarsFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = tc.text
            )
            Text(
                text = trigger.subtitle,
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModalSheetContent(visible: Boolean, index: Int, onDismiss: () -> Unit) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    MDAOBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        title = ""
    ) {
        when (index) {
            0 -> ConfirmModal(onDismiss = onDismiss)
            1 -> ApproveModal(onDismiss = onDismiss)
            2 -> SwitchNetworkModal(onDismiss = onDismiss)
            3 -> SignTransactionModal(onDismiss = onDismiss)
            4 -> DeleteModal(onDismiss = onDismiss)
            5 -> InfoModal(onDismiss = onDismiss)
        }
    }
}

@Composable
private fun ModalHeader(icon: String, bgColor: String, title: String, text: String) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    val iconBg = when (bgColor) {
        "danger" -> extended.danger
        "warning" -> extended.warning
        "success" -> extended.success
        "accent" -> extended.accent
        else -> tc.tile
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(50))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 28.sp, color = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = tc.text,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                fontFamily = MarsFont,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                color = tc.text2,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun ConfirmModal(onDismiss: () -> Unit) {
    ModalHeader(
        icon = "\u26A0\uFE0F",
        bgColor = "warning",
        title = "Выйти из кошелька?",
        text = "Все данные будут удалены с устройства. Убедитесь, что у вас есть recovery phrase, иначе восстановить доступ будет невозможно."
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MDAOButton(
            text = "Отмена",
            onClick = onDismiss,
            variant = MDAOButtonVariant.Secondary,
            modifier = Modifier.weight(1f)
        )
        MDAOButton(
            text = "Выйти",
            onClick = onDismiss,
            variant = MDAOButtonVariant.Danger,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ApproveModal(onDismiss: () -> Unit) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    ModalHeader(
        icon = "\u2714\uFE0F",
        bgColor = "accent",
        title = "Approve USDT",
        text = "Смарт-контракт 0x4F2E\u20268B9C запрашивает разрешение на использование ваших USDT."
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tc.tile)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailRow(label = "Контракт", value = "Arena DEX")
            DetailRow(label = "Токен", value = "USDT")
            DetailRow(label = "Лимит", value = "Unlimited", isAccent = true)
            DetailRow(label = "Комиссия", value = "~0.10 USDT")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MDAOButton(text = "Approve", onClick = onDismiss)
        MDAOButton(text = "Отклонить", onClick = onDismiss, variant = MDAOButtonVariant.Secondary)
    }
}

@Composable
private fun SwitchNetworkModal(onDismiss: () -> Unit) {
    ModalHeader(
        icon = "\uD83C\uDF10",
        bgColor = "neutral",
        title = "Сменить сеть",
        text = "dApp Arena запрашивает переключение на Ethereum Mainnet. Текущая сеть: BNB Smart Chain."
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MDAOButton(text = "Переключить", onClick = onDismiss)
        MDAOButton(text = "Остаться на BNB Chain", onClick = onDismiss, variant = MDAOButtonVariant.Secondary)
    }
}

@Composable
private fun SignTransactionModal(onDismiss: () -> Unit) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    ModalHeader(
        icon = "\uD83D\uDEE1\uFE0F",
        bgColor = "success",
        title = "Подтвердить транзакцию",
        text = "Подпишите транзакцию для смарт-контракта Arena."
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tc.tile)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailRow(label = "От", value = "0x8A9B\u2026C1D2")
            DetailRow(label = "Контракт", value = "0x4F2E\u20268B9C")
            DetailRow(label = "Значение", value = "0 ETH")
            DetailRow(label = "Gas", value = "~120,000")
            DetailRow(label = "Комиссия", value = "~$3.45", isAccent = true)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MDAOButton(text = "Подписать", onClick = onDismiss)
        MDAOButton(text = "Отклонить", onClick = onDismiss, variant = MDAOButtonVariant.Secondary)
    }
}

@Composable
private fun DeleteModal(onDismiss: () -> Unit) {
    ModalHeader(
        icon = "\uD83D\uDDD1\uFE0F",
        bgColor = "danger",
        title = "Удалить контакт?",
        text = "Контакт @bob будет удалён. История транзакций сохранится. Это действие нельзя отменить."
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MDAOButton(
            text = "Отмена",
            onClick = onDismiss,
            variant = MDAOButtonVariant.Secondary,
            modifier = Modifier.weight(1f)
        )
        MDAOButton(
            text = "Удалить",
            onClick = onDismiss,
            variant = MDAOButtonVariant.Danger,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InfoModal(onDismiss: () -> Unit) {
    ModalHeader(
        icon = "\u2139\uFE0F",
        bgColor = "neutral",
        title = "Новое обновление",
        text = "Версия 1.1.0 уже доступна. Улучшена производительность, добавлены новые токены, исправлены ошибки."
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MDAOButton(text = "Обновить", onClick = onDismiss)
        MDAOButton(text = "Позже", onClick = onDismiss, variant = MDAOButtonVariant.Secondary)
    }
}

@Composable
private fun DetailRow(label: String, value: String, isAccent: Boolean = false) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = MarsFont,
            fontSize = 12.sp,
            color = tc.text2
        )
        Text(
            text = value,
            fontFamily = MarsMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = if (isAccent) extended.accent else tc.text
        )
    }
}
