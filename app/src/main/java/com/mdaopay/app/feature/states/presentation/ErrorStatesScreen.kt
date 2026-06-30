package com.mdaopay.app.feature.states.presentation

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.components.MDAOButtonVariant
import com.mdaopay.app.core.ui.components.MDAOSegmentedControl
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.extended

private val stateLabels = listOf("Network", "Insufficient", "Address", "Tx Failed", "Banners")

@Composable
fun ErrorStatesScreen(
    onBack: () -> Unit
) {
    var selectedIndex by remember { mutableStateOf(0) }
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tc.bg)
    ) {
        MDAOTopBar(
            title = "Error States",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            MDAOSegmentedControl(
                options = stateLabels,
                selectedIndex = selectedIndex,
                onSelectionChange = { selectedIndex = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (selectedIndex) {
                0 -> NetworkErrorState()
                1 -> InsufficientFundsState()
                2 -> InvalidAddressState()
                3 -> TransactionFailedState()
                4 -> BannersState()
            }
        }
    }
}

@Composable
private fun NetworkErrorState() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ErrorIcon(icon = "\u26A0\uFE0F", isWarning = false)

        Text(
            text = "Сеть недоступна",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = tc.text,
            letterSpacing = (-0.2).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Не удалось подключиться к RPC-узлу. Проверьте интернет-подключение и попробуйте снова.",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = tc.text2,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(tc.tile)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "ERR_NETWORK_TIMEOUT \u00B7 30s",
                fontFamily = MarsMono,
                fontSize = 13.sp,
                color = tc.text3
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Column(modifier = Modifier.fillMaxWidth(0.8f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MDAOButton(text = "Повторить", onClick = { })
            MDAOButton(text = "Сменить сеть", onClick = { }, variant = MDAOButtonVariant.Secondary)
        }
    }
}

@Composable
private fun InsufficientFundsState() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ErrorIcon(icon = "\uD83D\uDCB0", isWarning = true)

        Text(
            text = "Недостаточно средств",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = tc.text,
            letterSpacing = (-0.2).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "На балансе недостаточно USDT для отправки указанной суммы и оплаты комиссии сети.",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = tc.text2,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        BalanceDisplay()
        Spacer(modifier = Modifier.height(12.dp))
        SolutionsCard()
    }
}

@Composable
private fun BalanceDisplay() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tc.card)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ДОСТУПНО",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = tc.text2,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1 250.50 USDT",
                fontFamily = MarsMono,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = tc.text,
                letterSpacing = (-1.2).sp
            )
            Text(
                text = "\u2248 $1 250.50",
                fontSize = 13.sp,
                color = tc.text2
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(tc.border)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Нужно: ",
                    fontSize = 12.sp,
                    color = tc.text2
                )
                Text(
                    text = "1 350.60 USDT",
                    fontFamily = MarsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = extended.danger
                )
                Text(
                    text = " (сумма + комиссия)",
                    fontSize = 12.sp,
                    color = tc.text2
                )
            }
        }
    }
}

@Composable
private fun SolutionsCard() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tc.card)
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = "РЕШЕНИЯ",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = tc.text2,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SolutionRow(text = "Уменьшить сумму до 1 250.40 USDT")
                SolutionRow(text = "Получить перевод")
                SolutionRow(text = "Обменять другой токен на USDT")
            }
        }
    }
}

@Composable
private fun SolutionRow(text: String) {
    val extended = MaterialTheme.extended
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.extended.themeColors.tile.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(text = "\u00B7", color = extended.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            text = text,
            fontFamily = MarsFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = MaterialTheme.extended.themeColors.text
        )
    }
}

@Composable
private fun InvalidAddressState() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ErrorIcon(icon = "\u2757", isWarning = false)

        Text(
            text = "Неверный адрес",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = tc.text,
            letterSpacing = (-0.2).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Введённый адрес не соответствует формату BNB Smart Chain. Проверьте правильность ввода.",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = tc.text2,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "ПОЛЕ С ОШИБКОЙ",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = tc.text2,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(tc.card)
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0x8A9B3F2C7E4D1A6B5",
                        fontFamily = MarsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = tc.text
                    )
                    Text(
                        text = "\u2716",
                        fontSize = 14.sp,
                        color = extended.danger
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "\u2757",
                        fontSize = 11.sp,
                        color = extended.danger
                    )
                    Text(
                        text = "Адрес должен содержать 42 символа (0x + 40 hex)",
                        fontFamily = MarsFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = extended.danger
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(tc.tile.copy(alpha = 0.4f))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Подсказки:",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = tc.text
                )
                HintRow(text = "Адрес BNB Chain начинается с 0x")
                HintRow(text = "Содержит только цифры 0-9 и буквы a-f")
                HintRow(text = "Длина \u2014 ровно 42 символа")
                HintRow(text = "Можно ввести ник получателя (начинается с @)")
            }
        }
    }
}

@Composable
private fun HintRow(text: String) {
    val extended = MaterialTheme.extended
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "\u2022",
            fontSize = 13.sp,
            color = extended.accent,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            fontFamily = MarsFont,
            fontSize = 12.sp,
            color = MaterialTheme.extended.themeColors.text2
        )
    }
}

@Composable
private fun TransactionFailedState() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ErrorIcon(icon = "\u2716", isWarning = false)

        Text(
            text = "Транзакция не удалась",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = tc.text,
            letterSpacing = (-0.2).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Смарт-контракт отклонил операцию. Средства возвращены на ваш кошелёк, комиссия сети списана.",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = tc.text2,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(tc.tile)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "REVERT \u00B7 gas used: 21,000 / 50,000",
                fontFamily = MarsMono,
                fontSize = 13.sp,
                color = tc.text3
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(modifier = Modifier.fillMaxWidth(0.8f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MDAOButton(text = "Повторить", onClick = { })
            MDAOButton(text = "Открыть в эксплорере", onClick = { }, variant = MDAOButtonVariant.Secondary)
            MDAOButton(text = "Связаться с поддержкой", onClick = { }, variant = MDAOButtonVariant.Secondary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ВОЗМОЖНЫЕ ПРИЧИНЫ",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = tc.text2,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CauseRow(number = "1", title = "Недостаточно газа", subtitle = "Повысьте gas limit при отправке")
            CauseRow(number = "2", title = "Контракт получателя не поддерживает перевод", subtitle = "Проверьте адрес получателя")
            CauseRow(number = "3", title = "Сеть перегружена", subtitle = "Повторите через несколько минут")
        }
    }
}

@Composable
private fun CauseRow(number: String, title: String, subtitle: String) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tc.card)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tc.text)
        }
        Column {
            Text(
                text = title,
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = tc.text
            )
            Text(
                text = subtitle,
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2
            )
        }
    }
}

@Composable
private fun BannersState() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column {
        Text(
            text = "INLINE BANNERS",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = tc.text2,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        ErrorBanner(
            icon = "\u26A0\uFE0F",
            title = "Backup не создан",
            subtitle = "Сделайте резервную копию, чтобы не потерять доступ к кошельку.",
            isWarning = true,
            actionLabel = "Backup"
        )
        ErrorBanner(
            icon = "\u26A0\uFE0F",
            title = "Нет соединения",
            subtitle = "Балансы могут быть устаревшими. Некоторые функции недоступны.",
            actionLabel = "Повторить"
        )
        ErrorBanner(
            icon = "\u2757",
            title = "Сеть BNB Chain перегружена",
            subtitle = "Комиссии могут быть выше обычного. Подтверждения занимают больше времени.",
            isWarning = true,
            actionLabel = "Подробнее"
        )
        ErrorBanner(
            icon = "\uD83D\uDEE1\uFE0F",
            title = "Подозрительный вход",
            subtitle = "Новый вход с неизвестного устройства. Если это не вы \u2014 смените PIN.",
            actionLabel = "Проверить"
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "FORM ERRORS",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = tc.text2,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        FormErrorCard(input = "abc forest", message = "Фраза должна содержать 12 или 24 слова")
        Spacer(modifier = Modifier.height(8.dp))
        FormErrorCard(input = "\u2022\u2022\u2022\u2022", message = "PIN должен содержать 6 цифр")
    }
}

@Composable
private fun ErrorBanner(
    icon: String,
    title: String,
    subtitle: String,
    isWarning: Boolean = false,
    actionLabel: String
) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors
    val bgColor = if (isWarning) extended.warningSoft else extended.dangerSoft

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = icon,
                fontSize = 16.sp,
                color = if (isWarning) extended.warning else extended.danger
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = tc.text
                )
                Text(
                    text = subtitle,
                    fontFamily = MarsFont,
                    fontSize = 11.sp,
                    color = tc.text2,
                    lineHeight = 16.sp
                )
            }
            Text(
                text = actionLabel,
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = extended.accent
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun FormErrorCard(input: String, message: String) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tc.card)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = input,
                    fontFamily = MarsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = tc.text
                )
                Text(text = "\u2716", fontSize = 13.sp, color = extended.danger)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "\u2757", fontSize = 10.sp, color = extended.danger)
                Text(
                    text = message,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = extended.danger
                )
            }
        }
    }
}

@Composable
private fun ErrorIcon(icon: String, isWarning: Boolean) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors
    val tintColor = if (isWarning) extended.warning else extended.danger

    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(tc.card),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 44.sp,
            color = tintColor
        )
    }
    Spacer(modifier = Modifier.height(20.dp))
}
