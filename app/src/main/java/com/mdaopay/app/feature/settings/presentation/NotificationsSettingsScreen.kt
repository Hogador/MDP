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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import com.mdaopay.app.core.ui.components.MDAOSlider
import com.mdaopay.app.core.ui.components.MDAOToggle
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.*

@Composable
fun NotificationsSettingsScreen(onBack: () -> Unit) {
    val ext = MaterialTheme.extended
    val d = ext.themeColors
    var masterEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(true) }
    var silentEnabled by remember { mutableStateOf(false) }
    var incomingEnabled by remember { mutableStateOf(true) }
    var outgoingEnabled by remember { mutableStateOf(true) }
    var swapsEnabled by remember { mutableStateOf(true) }
    var securityEnabled by remember { mutableStateOf(true) }
    var newsEnabled by remember { mutableStateOf(false) }
    var priceAlertEnabled by remember { mutableStateOf(true) }
    var thresholdValue by remember { mutableFloatStateOf(5f) }
    var quietEnabled by remember { mutableStateOf(false) }
    var selectedSound by remember { mutableIntStateOf(0) }

    val soundOptions = listOf("По умолчанию", "Колокольчик", "Монета", "Поп", "Без звука")

    Column(modifier = Modifier.fillMaxSize().background(d.bg)) {
        MDAOTopBar(title = "Уведомления", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            MDAOListSection(header = "Общие") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.3f, size.height * 0.2f)
                                lineTo(size.width * 0.7f, size.height * 0.2f)
                                lineTo(size.width * 0.7f, size.height * 0.4f)
                                lineTo(size.width * 0.3f, size.height * 0.4f)
                                close()
                            }
                            drawPath(p, color = Color.White)
                        }
                    },
                    iconVariant = ListIconVariant.Accent,
                    title = "Push-уведомления",
                    subtitle = "Главный переключатель",
                    toggle = {
                        MDAOToggle(checked = masterEnabled, onCheckedChange = { masterEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                    androidx.compose.ui.geometry.Rect(size.width * 0.15f, size.height * 0.3f, size.width * 0.85f, size.height * 0.8f),
                                    2f, 2f
                                ))
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                        }
                    },
                    title = "Звук",
                    toggle = {
                        MDAOToggle(checked = soundEnabled, onCheckedChange = { soundEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                    androidx.compose.ui.geometry.Rect(size.width * 0.15f, size.height * 0.3f, size.width * 0.85f, size.height * 0.8f),
                                    2f, 2f
                                ))
                            }
                            drawPath(p, color = d.text3, style = Stroke(width = 1.5f))
                            drawLine(color = d.text3, start = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.15f), end = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.85f), strokeWidth = 1.5f)
                        }
                    },
                    title = "Без звука (только визуально)",
                    toggle = {
                        MDAOToggle(checked = silentEnabled, onCheckedChange = { silentEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            MDAOListSection(header = "Категории") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.7f)
                                lineTo(size.width * 0.8f, size.height * 0.2f)
                                moveTo(size.width * 0.2f, size.height * 0.2f)
                                lineTo(size.width * 0.8f, size.height * 0.7f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f, cap = StrokeCap.Round))
                        }
                    },
                    iconVariant = ListIconVariant.Success,
                    title = "Входящие транзакции",
                    subtitle = "Когда получаете средства",
                    toggle = {
                        MDAOToggle(checked = incomingEnabled, onCheckedChange = { incomingEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.2f)
                                lineTo(size.width * 0.8f, size.height * 0.7f)
                                moveTo(size.width * 0.8f, size.height * 0.2f)
                                lineTo(size.width * 0.2f, size.height * 0.7f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f, cap = StrokeCap.Round))
                        }
                    },
                    title = "Исходящие транзакции",
                    subtitle = "Подтверждения отправки",
                    toggle = {
                        MDAOToggle(checked = outgoingEnabled, onCheckedChange = { outgoingEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.15f, size.height * 0.15f)
                                lineTo(size.width * 0.55f, size.height * 0.15f)
                                lineTo(size.width * 0.55f, size.height * 0.55f)
                                moveTo(size.width * 0.55f, size.height * 0.15f)
                                lineTo(size.width * 0.85f, size.height * 0.45f)
                                lineTo(size.width * 0.45f, size.height * 0.85f)
                                moveTo(size.width * 0.45f, size.height * 0.45f)
                                lineTo(size.width * 0.15f, size.height * 0.75f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    },
                    title = "Обмены",
                    subtitle = "Swap, DEX операции",
                    toggle = {
                        MDAOToggle(checked = swapsEnabled, onCheckedChange = { swapsEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.5f, size.height * 0.1f)
                                lineTo(size.width * 0.85f, size.height * 0.3f)
                                lineTo(size.width * 0.85f, size.height * 0.7f)
                                lineTo(size.width * 0.5f, size.height * 0.9f)
                                lineTo(size.width * 0.15f, size.height * 0.7f)
                                lineTo(size.width * 0.15f, size.height * 0.3f)
                                close()
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                        }
                    },
                    iconVariant = ListIconVariant.Accent,
                    title = "Безопасность",
                    subtitle = "Входы, подозрительные действия",
                    toggle = {
                        MDAOToggle(checked = securityEnabled, onCheckedChange = { securityEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            drawCircle(color = Color.White, radius = size.minDimension * 0.35f, style = Stroke(width = 1.5f))
                            val p = Path().apply {
                                moveTo(size.width * 0.3f, size.height * 0.7f)
                                lineTo(size.width * 0.5f, size.height * 0.5f)
                                lineTo(size.width * 0.7f, size.height * 0.7f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                        }
                    },
                    title = "Новости MDAO",
                    subtitle = "Анонсы, обновления",
                    toggle = {
                        MDAOToggle(checked = newsEnabled, onCheckedChange = { newsEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            MDAOListSection(header = "Цена токенов") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.15f, size.height * 0.7f)
                                lineTo(size.width * 0.4f, size.height * 0.45f)
                                lineTo(size.width * 0.6f, size.height * 0.65f)
                                lineTo(size.width * 0.85f, size.height * 0.35f)
                                moveTo(size.width * 0.7f, size.height * 0.35f)
                                lineTo(size.width * 0.85f, size.height * 0.35f)
                                lineTo(size.width * 0.85f, size.height * 0.5f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    },
                    title = "Уведомлять о росте",
                    subtitle = "При изменении цены на N%",
                    toggle = {
                        MDAOToggle(checked = priceAlertEnabled, onCheckedChange = { priceAlertEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Порог изменения",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = d.text,
                                fontFamily = MarsFont
                            )
                            Text(
                                text = "${thresholdValue.toInt()}%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ext.accent,
                                fontFamily = MarsMono
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val sliderFraction = (thresholdValue - 1f) / 19f
                        MDAOSlider(
                            value = sliderFraction,
                            onValueChange = { thresholdValue = 1f + it * 19f },
                            steps = 19
                        )
                        Text(
                            text = "Уведомление приходит при изменении цены токена на указанный процент за 24 часа",
                            fontSize = 10.sp,
                            color = d.text2,
                            fontFamily = MarsFont,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MDAOListSection(header = "Тихие часы") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                addOval(androidx.compose.ui.geometry.Rect(size.width * 0.15f, size.height * 0.15f, size.width * 0.85f, size.height * 0.85f))
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                            drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.5f), end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.25f), strokeWidth = 2f)
                        }
                    },
                    title = "Включить тихие часы",
                    subtitle = "Без звука в указанное время",
                    toggle = {
                        MDAOToggle(checked = quietEnabled, onCheckedChange = { quietEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "С", fontSize = 12.sp, color = d.text2, fontFamily = MarsFont, fontWeight = FontWeight.SemiBold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MDARadius.sm))
                                .background(d.tile)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "22:00", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = d.text, fontFamily = MarsMono)
                        }
                    }
                    Text(text = "\u2192", fontSize = 12.sp, color = d.text3)
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "До", fontSize = 12.sp, color = d.text2, fontFamily = MarsFont, fontWeight = FontWeight.SemiBold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MDARadius.sm))
                                .background(d.tile)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = "08:00", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = d.text, fontFamily = MarsMono)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            MDAOListSection(header = "Звук уведомления") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    soundOptions.forEachIndexed { index, label ->
                        val sel = selectedSound == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(MDARadius.lg))
                                .background(if (sel) ext.accentSoft else d.tile)
                                .clickable {
                                    selectedSound = index
                                    HapticManager.light()
                                }
                                .drawBehind {
                                    val borderColor = if (sel) ext.accent else d.softBorder
                                    drawRoundRect(color = borderColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.lg.toPx()), style = Stroke(width = 2.dp.toPx()))
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (sel) ext.accent else d.text2,
                                fontFamily = MarsFont
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
