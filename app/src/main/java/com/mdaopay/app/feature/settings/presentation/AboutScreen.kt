package com.mdaopay.app.feature.settings.presentation

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.ListIconVariant
import com.mdaopay.app.core.ui.components.MDAOListItem
import com.mdaopay.app.core.ui.components.MDAOListSection
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.*

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val d = MaterialTheme.extended.themeColors

    Column(modifier = Modifier.fillMaxSize().background(d.bg)) {
        MDAOTopBar(title = "О приложении", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Hero
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.xxl))
                    .background(d.card)
                    .drawBehind {
                        drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xxl.toPx()), style = Stroke(width = 1.dp.toPx()))
                    }
                    .padding(28.dp, 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .shadow(10.dp, RoundedCornerShape(MDARadius.card), clip = false)
                            .clip(RoundedCornerShape(MDARadius.card))
                            .background(Brush.linearGradient(colors = listOf(MaterialTheme.extended.accent, Color(0xFFFF9A4D)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\u25C8",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "MDAOPay",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = d.text,
                        fontFamily = MarsFont,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Версия 1.0.0 (build 2215)",
                        fontSize = 14.sp,
                        color = d.text2,
                        fontFamily = MarsMono
                    )
                    Text(
                        text = "Децентрализованный кошелёк нового поколения. Полный контроль над вашими средствами.",
                        fontSize = 12.sp,
                        color = d.text2,
                        fontFamily = MarsFont,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "4" to "Сети",
                    "120+" to "Токенов",
                    "99.9%" to "Uptime"
                ).forEach { (value, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(MDARadius.lg))
                            .background(d.card)
                            .drawBehind {
                                drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.lg.toPx()), style = Stroke(width = 1.dp.toPx()))
                            }
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = value,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = d.text,
                                fontFamily = MarsMono
                            )
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = d.text2,
                                fontFamily = MarsFont
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Links
            MDAOListSection(header = "Ссылки") {
                LinkRow(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            drawCircle(color = Color.White, radius = size.minDimension / 2f, style = Stroke(width = 1.5f))
                            drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height / 2f), strokeWidth = 1.5f)
                            drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.3f), end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.7f), strokeWidth = 1.5f)
                        }
                    },
                    title = "Официальный сайт",
                    subtitle = "mdaopay.xyz"
                )
                LinkRow(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.15f)
                                lineTo(size.width * 0.6f, size.height * 0.15f)
                                lineTo(size.width * 0.8f, size.height * 0.35f)
                                lineTo(size.width * 0.8f, size.height * 0.85f)
                                lineTo(size.width * 0.2f, size.height * 0.85f)
                                close()
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                        }
                    },
                    title = "Whitepaper",
                    subtitle = "docs.mdaopay.xyz"
                )
                LinkRow(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.75f)
                                lineTo(size.width * 0.2f, size.height * 0.35f)
                                lineTo(size.width * 0.5f, size.height * 0.15f)
                                lineTo(size.width * 0.8f, size.height * 0.35f)
                                lineTo(size.width * 0.8f, size.height * 0.75f)
                                close()
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                        }
                    },
                    title = "GitHub",
                    subtitle = "github.com/mdaopay"
                )
                LinkRow(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.1f, size.height * 0.15f)
                                lineTo(size.width * 0.4f, size.height * 0.15f)
                                lineTo(size.width * 0.5f, size.height * 0.4f)
                                lineTo(size.width * 0.4f, size.height * 0.85f)
                                lineTo(size.width * 0.1f, size.height * 0.85f)
                                close()
                                moveTo(size.width * 0.9f, size.height * 0.15f)
                                lineTo(size.width * 0.6f, size.height * 0.15f)
                                lineTo(size.width * 0.5f, size.height * 0.4f)
                                lineTo(size.width * 0.6f, size.height * 0.85f)
                                lineTo(size.width * 0.9f, size.height * 0.85f)
                                close()
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                        }
                    },
                    title = "Twitter / X",
                    subtitle = "@mdaopay"
                )
                LinkRow(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.1f, size.height * 0.85f)
                                lineTo(size.width * 0.2f, size.height * 0.6f)
                                lineTo(size.width * 0.5f, size.height * 0.4f)
                                lineTo(size.width * 0.9f, size.height * 0.15f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                        }
                    },
                    title = "Telegram",
                    subtitle = "t.me/mdaopay"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            MDAOListSection(header = "Система") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.15f, size.height * 0.2f)
                                lineTo(size.width * 0.5f, size.height * 0.2f)
                                lineTo(size.width * 0.5f, size.height * 0.5f)
                                moveTo(size.width * 0.5f, size.height * 0.2f)
                                lineTo(size.width * 0.85f, size.height * 0.5f)
                                lineTo(size.width * 0.5f, size.height * 0.8f)
                                moveTo(size.width * 0.5f, size.height * 0.5f)
                                lineTo(size.width * 0.15f, size.height * 0.8f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    },
                    iconVariant = ListIconVariant.Accent,
                    title = "Проверить обновления",
                    subtitle = "Последняя проверка: сегодня",
                    onClick = { HapticManager.light() }
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.15f, size.height * 0.85f)
                                lineTo(size.width * 0.15f, size.height * 0.15f)
                                lineTo(size.width * 0.85f, size.height * 0.15f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                        }
                    },
                    title = "Диагностика",
                    subtitle = "Логи, информация для поддержки",
                    onClick = { HapticManager.light() }
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.15f, size.height * 0.5f)
                                lineTo(size.width * 0.4f, size.height * 0.78f)
                                lineTo(size.width * 0.88f, size.height * 0.25f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    },
                    iconVariant = ListIconVariant.Success,
                    title = "Статус сети",
                    subtitle = "Все системы работают",
                    onClick = { HapticManager.light() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "\u00A9 2024-2026 MDAOPay",
                fontSize = 10.sp,
                color = d.text3,
                fontFamily = MarsFont,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
            Text(
                text = "Условия · Конфиденциальность · Дисклеймер",
                fontSize = 10.sp,
                color = MaterialTheme.extended.accent,
                fontFamily = MarsFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LinkRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String
) {
    val d = MaterialTheme.extended.themeColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(MDARadius.lg))
            .background(d.card)
            .clickable { HapticManager.light() }
            .padding(12.dp, 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(MDARadius.md))
                    .background(d.tile),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = d.text,
                    fontFamily = MarsFont
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = d.text2,
                    fontFamily = MarsMono
                )
            }
            Canvas(modifier = Modifier.size(14.dp)) {
                val p = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.2f)
                    lineTo(size.width * 0.8f, size.height * 0.2f)
                    lineTo(size.width * 0.8f, size.height * 0.8f)
                    moveTo(size.width * 0.8f, size.height * 0.2f)
                    lineTo(size.width * 0.2f, size.height * 0.8f)
                }
                drawPath(p, color = d.text3, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
