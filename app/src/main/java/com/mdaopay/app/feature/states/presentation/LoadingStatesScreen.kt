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
import com.mdaopay.app.core.ui.components.MDAOSkeleton
import com.mdaopay.app.core.ui.components.MDAOSkeletonCard
import com.mdaopay.app.core.ui.components.MDAOSkeletonLine
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.extended

private val stateLabels = listOf("Splash", "Wallet Init", "Syncing", "Skeletons", "Button Loading")

@Composable
fun LoadingStatesScreen(
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
            title = "Loading States",
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

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedIndex) {
                0 -> SplashState()
                1 -> WalletInitState()
                2 -> SyncingState()
                3 -> SkeletonsState()
                4 -> ButtonLoadingState()
            }
        }
    }
}

@Composable
private fun SplashState() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(extended.accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u25C8",
                fontSize = 48.sp,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MDAO",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = extended.accent,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Pay",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = tc.text,
                letterSpacing = (-0.5).sp
            )
        }
        Text(text = "\u25E0", fontSize = 36.sp, color = extended.accent)
    }
}

@Composable
private fun WalletInitState() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(50))
                .background(extended.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\uD83D\uDD12", fontSize = 40.sp, color = extended.accent)
        }
        Text(
            text = "Создаём кошелёк",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = tc.text,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = "Генерируем ключи, шифруем данные на устройстве. Это займёт несколько секунд.",
            fontFamily = MarsFont,
            fontSize = 13.sp,
            color = tc.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(0.8f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            InitStepRow(label = "Генерация ключей", done = true)
            InitStepRow(label = "Шифрование на устройстве", done = true)
            InitStepRow(label = "Регистрация в сети BNB Chain", active = true)
            InitStepRow(label = "Индексация токенов", pending = true)
        }
    }
}

@Composable
private fun InitStepRow(label: String, done: Boolean = false, active: Boolean = false, pending: Boolean = false) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors
    val icon = when {
        done -> "\u2713"
        active -> "\u25E0"
        else -> "\u25CB"
    }
    val iconColor = when {
        done -> extended.success
        active -> extended.accent
        else -> tc.text3
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Text(text = icon, fontSize = 14.sp, color = iconColor)
        }
        Text(
            text = label,
            fontFamily = MarsFont,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = if (done) tc.text else tc.text2
        )
    }
}

@Composable
private fun SyncingState() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(50))
                .background(extended.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\u21BB", fontSize = 36.sp, color = extended.accent)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Синхронизация балансов",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = tc.text,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = "Обновляем данные с 4 сетей. Это может занять до 30 секунд.",
            fontFamily = MarsFont,
            fontSize = 13.sp,
            color = tc.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ПРОГРЕСС",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = tc.text2,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tc.tile)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(extended.accent)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        repeat(3) {
            MDAOSkeletonCard()
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SkeletonsState() {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "КАРТА ТОКЕНОВ",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.extended.themeColors.text2,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        MDAOSkeletonCard()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "КОНТАКТЫ",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.extended.themeColors.text2,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.extended.themeColors.card)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(4) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MDAOSkeleton(modifier = Modifier.size(40.dp), height = 40.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        MDAOSkeleton(width = 40.dp, height = 8.dp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "ИСТОРИЯ ТРАНЗАКЦИЙ",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.extended.themeColors.text2,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        repeat(3) {
            MDAOSkeletonLine(height = 14.dp)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ButtonLoadingState() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Кнопки в loading-состоянии",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = tc.text,
            letterSpacing = (-0.2).sp
        )
        Text(
            text = "Кнопка блокируется и показывает спиннер во время выполнения действия.",
            fontFamily = MarsFont,
            fontSize = 13.sp,
            color = tc.text2
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "PRIMARY",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = tc.text2,
            letterSpacing = 1.sp
        )
        MDAOButton(text = "Отправка\u2026", onClick = { }, isLoading = true)

        Text(
            text = "SECONDARY",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = tc.text2,
            letterSpacing = 1.sp
        )
        MDAOButton(text = "Загрузка\u2026", onClick = { }, isLoading = true, variant = MDAOButtonVariant.Secondary)

        Text(
            text = "INLINE С ТЕКСТОМ",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = tc.text2,
            letterSpacing = 1.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(tc.card)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "\u25E0", fontSize = 18.sp, color = extended.accent)
                Text(
                    text = "Подтверждение в блокчейне\u2026",
                    fontFamily = MarsFont,
                    fontSize = 13.sp,
                    color = tc.text2
                )
            }
        }

        Text(
            text = "PULL-TO-REFRESH",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = tc.text2,
            letterSpacing = 1.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(tc.tile)
                .padding(14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "\u25E0", fontSize = 14.sp, color = extended.accent)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Обновляем балансы\u2026",
                fontFamily = MarsFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = tc.text2
            )
        }
    }
}
