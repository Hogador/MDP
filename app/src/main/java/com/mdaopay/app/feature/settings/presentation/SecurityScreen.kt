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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.ListIconVariant
import com.mdaopay.app.core.ui.components.MDAOListItem
import com.mdaopay.app.core.ui.components.MDAOListSection
import com.mdaopay.app.core.ui.components.MDAOToggle
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.*

@Composable
fun SecurityScreen(onBack: () -> Unit) {
    val ext = MaterialTheme.extended
    val d = ext.themeColors
    var pinEnabled by remember { mutableStateOf(true) }
    var faceIdEnabled by remember { mutableStateOf(true) }
    var touchIdEnabled by remember { mutableStateOf(false) }
    var hideBalanceEnabled by remember { mutableStateOf(false) }
    var antiphishEnabled by remember { mutableStateOf(true) }
    var noScreenshotsEnabled by remember { mutableStateOf(true) }
    var autoLockSeconds by remember { mutableStateOf(60) }

    val autoLockOptions = listOf(
        0 to "Сразу", 30 to "30с", 60 to "1м", 300 to "5м"
    )

    Column(modifier = Modifier.fillMaxSize().background(d.bg)) {
        MDAOTopBar(title = "Безопасность", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Status card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.xxl))
                    .background(d.card)
                    .drawBehind {
                        drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xxl.toPx()), style = Stroke(width = 1.dp.toPx()))
                    }
                    .padding(14.dp, 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (pinEnabled) ext.successSoft else ext.warningSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(22.dp)) {
                            if (pinEnabled) {
                                val p = Path().apply {
                                    moveTo(size.width * 0.15f, size.height * 0.5f)
                                    lineTo(size.width * 0.4f, size.height * 0.78f)
                                    lineTo(size.width * 0.88f, size.height * 0.25f)
                                }
                                drawPath(p, color = ext.success, style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            } else {
                                val p = Path().apply {
                                    moveTo(size.width * 0.3f, size.height * 0.3f)
                                    lineTo(size.width * 0.7f, size.height * 0.7f)
                                    moveTo(size.width * 0.7f, size.height * 0.3f)
                                    lineTo(size.width * 0.3f, size.height * 0.7f)
                                }
                                drawPath(p, color = ext.warning, style = Stroke(width = 2.4f, cap = StrokeCap.Round))
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (pinEnabled) "Защита включена" else "Защита отключена",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = d.text,
                            fontFamily = MarsFont
                        )
                        Text(
                            text = if (pinEnabled) "PIN-код и биометрия активны" else "Без PIN — кто угодно получит доступ",
                            fontSize = 12.sp,
                            color = d.text2,
                            fontFamily = MarsFont
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PIN section
            MDAOListSection(header = "PIN-код") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                    androidx.compose.ui.geometry.Rect(size.width * 0.1f, size.height * 0.4f, size.width * 0.9f, size.height * 0.9f),
                                    2f, 2f
                                ))
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                            drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.4f), end = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.25f), strokeWidth = 1.5f)
                            drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.4f), end = androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.25f), strokeWidth = 1.5f)
                        }
                    },
                    iconVariant = ListIconVariant.Accent,
                    title = "PIN-код",
                    subtitle = "6 цифр",
                    toggle = {
                        MDAOToggle(
                            checked = pinEnabled,
                            onCheckedChange = {
                                pinEnabled = it
                                HapticManager.light()
                            }
                        )
                    },
                    showChevron = false
                )
                if (pinEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(6) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(ext.accent)
                                    .drawBehind {
                                        drawCircle(
                                            color = ext.accent,
                                            radius = size.minDimension / 2f
                                        )
                                    }
                            )
                        }
                    }
                }
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.8f)
                                lineTo(size.width * 0.8f, size.height * 0.2f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f, cap = StrokeCap.Round))
                        }
                    },
                    title = "Сменить PIN",
                    onClick = { HapticManager.light() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Biometry section
            MDAOListSection(header = "Биометрия") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            drawCircle(color = Color.White, radius = size.minDimension * 0.25f)
                            val p = Path().apply {
                                addOval(androidx.compose.ui.geometry.Rect(size.width * 0.2f, size.height * 0.55f, size.width * 0.8f, size.height * 0.95f))
                            }
                            drawPath(p, color = Color.White)
                        }
                    },
                    iconVariant = ListIconVariant.Success,
                    title = "Face ID",
                    subtitle = "Разблокировка по лицу",
                    toggle = {
                        MDAOToggle(checked = faceIdEnabled, onCheckedChange = { faceIdEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.3f, size.height * 0.15f)
                                lineTo(size.width * 0.7f, size.height * 0.15f)
                                lineTo(size.width * 0.7f, size.height * 0.45f)
                                lineTo(size.width * 0.3f, size.height * 0.45f)
                                close()
                            }
                            drawPath(p, color = Color.White)
                        }
                    },
                    title = "Touch ID",
                    subtitle = "Разблокировка по отпечатку",
                    toggle = {
                        MDAOToggle(checked = touchIdEnabled, onCheckedChange = { touchIdEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-lock
            MDAOListSection(header = "Автоблокировка") {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)) {
                    Text(
                        text = "Блокировать кошелёк после бездействия:",
                        fontSize = 12.sp,
                        color = d.text2,
                        fontFamily = MarsFont,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        autoLockOptions.forEach { (seconds, label) ->
                            val selected = autoLockSeconds == seconds
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(MDARadius.lg))
                                    .background(if (selected) ext.accentSoft else d.tile)
                                    .clickable {
                                        autoLockSeconds = seconds
                                        HapticManager.light()
                                    }
                                    .drawBehind {
                                        val borderColor = if (selected) ext.accent else d.softBorder
                                        drawRoundRect(color = borderColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.lg.toPx()), style = Stroke(width = 2.dp.toPx()))
                                    }
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = label,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) ext.accent else d.text,
                                        fontFamily = MarsFont
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy section
            MDAOListSection(header = "Приватность") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            drawCircle(color = Color.White, radius = size.minDimension / 2f, style = Stroke(width = 1.5f))
                            drawCircle(color = Color.White, radius = size.minDimension * 0.2f)
                        }
                    },
                    title = "Скрывать баланс",
                    subtitle = "Показывать •••• вместо сумм",
                    toggle = {
                        MDAOToggle(checked = hideBalanceEnabled, onCheckedChange = { hideBalanceEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            drawCircle(color = Color.White, radius = size.minDimension * 0.4f, style = Stroke(width = 1.5f))
                            val p = Path().apply {
                                moveTo(size.width * 0.3f, size.height * 0.5f)
                                lineTo(size.width * 0.45f, size.height * 0.65f)
                                lineTo(size.width * 0.7f, size.height * 0.35f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    },
                    title = "Антифишинг",
                    subtitle = "Показывать адрес при отправке",
                    toggle = {
                        MDAOToggle(checked = antiphishEnabled, onCheckedChange = { antiphishEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                addRect(androidx.compose.ui.geometry.Rect(size.width * 0.1f, size.height * 0.1f, size.width * 0.9f, size.height * 0.9f))
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                            val inner = Path().apply {
                                addRect(androidx.compose.ui.geometry.Rect(size.width * 0.3f, size.height * 0.3f, size.width * 0.7f, size.height * 0.7f))
                            }
                            drawPath(inner, color = Color.White)
                        }
                    },
                    title = "Скриншоты",
                    subtitle = "Запретить в приложении",
                    toggle = {
                        MDAOToggle(checked = noScreenshotsEnabled, onCheckedChange = { noScreenshotsEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Danger zone
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    text = "ОПАСНАЯ ЗОНА",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ext.danger,
                    fontFamily = MarsFont,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
                MDAOListSection {
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                val p = Path().apply {
                                    moveTo(size.width * 0.15f, size.height * 0.3f)
                                    lineTo(size.width * 0.85f, size.height * 0.3f)
                                    moveTo(size.width * 0.3f, size.height * 0.3f)
                                    lineTo(size.width * 0.3f, size.height * 0.15f)
                                    lineTo(size.width * 0.7f, size.height * 0.15f)
                                    lineTo(size.width * 0.7f, size.height * 0.3f)
                                    moveTo(size.width * 0.25f, size.height * 0.3f)
                                    lineTo(size.width * 0.25f, size.height * 0.85f)
                                    lineTo(size.width * 0.75f, size.height * 0.85f)
                                    lineTo(size.width * 0.75f, size.height * 0.3f)
                                }
                                drawPath(p, color = ext.danger, style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        },
                        iconVariant = ListIconVariant.Danger,
                        title = "Стереть кошелёк",
                        subtitle = "Удалить все данные с устройства",
                        onClick = { HapticManager.light() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
