package com.mdaopay.app.feature.states.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.mdaopay.app.core.ui.theme.extended

private val stateLabels = listOf(
    "Нет транзакций", "Нет контактов", "Поиск пуст",
    "Нет токенов", "Нет уведомлений", "Оффлайн"
)

private data class EmptyStateConfig(
    val icon: String,
    val title: String,
    val subtitle: String,
    val isMuted: Boolean = false,
    val isSuccess: Boolean = false,
    val buttons: List<String>
)

private val states = listOf(
    EmptyStateConfig(
        icon = "\u25B3",
        title = "Пока нет транзакций",
        subtitle = "Здесь появятся все входящие и исходящие переводы. Отправьте или получите средства, чтобы начать.",
        buttons = listOf("Отправить", "Получить")
    ),
    EmptyStateConfig(
        icon = "\uD83D\uDC65",
        title = "Контактов пока нет",
        subtitle = "Добавьте адреса получателей, чтобы быстро отправлять средства без ввода адреса каждый раз.",
        buttons = listOf("Добавить контакт")
    ),
    EmptyStateConfig(
        icon = "\uD83D\uDD0D",
        title = "Ничего не найдено",
        subtitle = "Попробуйте изменить запрос или проверить правильность ввода адреса / ника.",
        isMuted = true,
        buttons = listOf("Сбросить поиск")
    ),
    EmptyStateConfig(
        icon = "\uD83D\uDCB0",
        title = "Нет токенов",
        subtitle = "На этом аккаунте ещё нет токенов. Получите перевод или добавьте существующий токен по адресу контракта.",
        buttons = listOf("Получить", "Добавить токен")
    ),
    EmptyStateConfig(
        icon = "\uD83D\uDD14",
        title = "Уведомлений нет",
        subtitle = "Вы всё прочитали. Здесь появятся уведомления о транзакциях, обменах и важных событиях.",
        isSuccess = true,
        buttons = listOf("Настройки уведомлений")
    ),
    EmptyStateConfig(
        icon = "\uD83C\uDF10",
        title = "Нет соединения",
        subtitle = "Проверьте интернет-подключение. Балансы и транзакции обновятся автоматически при восстановлении сети.",
        isMuted = true,
        buttons = listOf("Повторить")
    )
)

@Composable
fun EmptyStatesScreen(
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
            title = "Empty States",
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

            val state = states[selectedIndex]
            EmptyStateContent(state = state)
        }
    }
}

@Composable
private fun EmptyStateContent(state: EmptyStateConfig) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(tc.card)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = state.icon,
                fontSize = 44.sp,
                color = if (state.isMuted) tc.text3
                else if (state.isSuccess) extended.success
                else extended.accent,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = state.title,
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = tc.text,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.2).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.subtitle,
            fontFamily = MarsFont,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = tc.text2,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(0.8f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.buttons.forEachIndexed { index, label ->
                MDAOButton(
                    text = label,
                    onClick = { },
                    variant = if (index == 0) MDAOButtonVariant.Primary else MDAOButtonVariant.Secondary
                )
            }
        }
    }
}
