package com.mdaopay.app.feature.onboarding.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.extended

data class TutorialSlide(
    val title: String,
    val text: String,
    val icon: String
)

@Composable
fun OnboardingTutorialScreen(
    onComplete: () -> Unit,
    onImport: () -> Unit = onComplete
) {
    val slides = remember {
        listOf(
            TutorialSlide(
                title = "Добро пожаловать в MDAOPay",
                text = "Децентрализованный кошелёк нового поколения. Полный контроль над вашими средствами — без посредников.",
                icon = "\u25C8"
            ),
            TutorialSlide(
                title = "Газless транзакции",
                text = "Отправляйте USDT и MDAO без газа. Мгновенные переводы по @username.",
                icon = "\u25C9"
            ),
            TutorialSlide(
                title = "Безопасность прежде всего",
                text = "Face ID, PIN и passkey. Ваши ключи хранятся только на устройстве.",
                icon = "\u2756"
            ),
            TutorialSlide(
                title = "Экосистема MDAO",
                text = "Arena, DEX, Flopi, VPN — все сервисы в одном приложении. Зарабатывайте, обменивайте, общайтесь.",
                icon = "\u2726"
            )
        )
    }
    var currentSlide by remember { mutableStateOf(0) }
    val extended = MaterialTheme.extended
    val tc = extended.themeColors
    val slide = slides[currentSlide]
    val isLast = currentSlide == slides.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tc.bg)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MDAO",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = extended.accent,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Pay",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = tc.text,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!isLast) {
                    Text(
                        text = "Пропустить",
                        fontFamily = MarsFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = tc.text2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(tc.tile)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.3f))

            AnimatedContent(
                targetState = currentSlide,
                transitionSpec = {
                    (slideInHorizontally(animationSpec = tween(350)) { +it } + fadeIn(tween(350)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { -it } + fadeOut(tween(350)))
                },
                label = "slideContent",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { index ->
                val s = slides[index]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .drawBehind {
                                drawRoundRect(color = tc.card, cornerRadius = CornerRadius(28.dp.toPx()))
                                drawRoundRect(
                                    color = extended.accentSoft,
                                    cornerRadius = CornerRadius(28.dp.toPx()),
                                    size = size
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = s.icon,
                            fontSize = 72.sp,
                            color = extended.accent,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = s.title,
                        fontFamily = MarsFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = tc.text,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 28.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = s.text,
                        fontFamily = MarsFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = tc.text2,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                slides.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (i == currentSlide) 24.dp else 8.dp,
                                height = 8.dp
                            )
                            .clip(if (i == currentSlide) RoundedCornerShape(4.dp) else RoundedCornerShape(50))
                            .background(
                                if (i == currentSlide) extended.accent else tc.tile
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MDAOButton(
                    text = if (isLast) "Создать кошелёк" else "Далее",
                    onClick = {
                        if (isLast) onComplete()
                        else currentSlide++
                    }
                )
                MDAOButton(
                    text = if (isLast) "Импортировать существующий" else "У меня есть фраза",
                    onClick = onImport
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
