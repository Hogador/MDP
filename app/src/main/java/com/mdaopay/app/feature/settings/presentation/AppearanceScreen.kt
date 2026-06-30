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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.mdaopay.app.core.ui.components.MDAOListItem
import com.mdaopay.app.core.ui.components.MDAOListSection
import com.mdaopay.app.core.ui.components.MDAOToggle
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.*

private val accentColors = listOf(
    "Оранжевый" to Color(0xFFFF6B00),
    "Синий" to Color(0xFF2D7FF9),
    "Зелёный" to Color(0xFF00B377),
    "Фиолетовый" to Color(0xFF7B4DFF),
    "Розовый" to Color(0xFFF94D9E)
)

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val ext = MaterialTheme.extended
    val d = ext.themeColors
    var selectedTheme by remember { mutableIntStateOf(0) }
    var selectedAccent by remember { mutableIntStateOf(0) }
    var selectedFontSize by remember { mutableIntStateOf(1) }
    var animationsEnabled by remember { mutableStateOf(true) }
    var systemThemeEnabled by remember { mutableStateOf(false) }

    val themeNames = listOf("Тёмная", "Светлая", "AMOLED")
    val fontSizes = listOf("S", "M", "L")
    val fontSizeNames = listOf("Маленький", "Средний", "Большой")

    Column(modifier = Modifier.fillMaxSize().background(d.bg)) {
        MDAOTopBar(title = "Внешний вид", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Live preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.xxl))
                    .background(d.card)
                    .drawBehind {
                        drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xxl.toPx()), style = Stroke(width = 1.dp.toPx()))
                    }
                    .padding(20.dp, 18.dp)
            ) {
                Column {
                    Text(
                        text = "ПРЕДПРОСМОТР",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = d.text2,
                        fontFamily = MarsFont,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "1 250.50",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = d.text,
                        fontFamily = MarsMono,
                        letterSpacing = (-1.2).sp
                    )
                    Text(
                        text = "\u2248 \$1 250.50",
                        fontSize = 12.sp,
                        color = d.text2,
                        fontFamily = MarsFont
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(MDARadius.xl))
                                .background(ext.accentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Canvas(modifier = Modifier.size(14.dp)) {
                                    val p = Path().apply {
                                        moveTo(size.width * 0.2f, size.height * 0.7f)
                                        lineTo(size.width * 0.8f, size.height * 0.2f)
                                        moveTo(size.width * 0.2f, size.height * 0.2f)
                                        lineTo(size.width * 0.8f, size.height * 0.7f)
                                    }
                                    drawPath(p, color = ext.accent, style = Stroke(width = 2.4f, cap = StrokeCap.Round))
                                }
                                Text(
                                    text = "Отправить",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ext.accent,
                                    fontFamily = MarsFont
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(MDARadius.xl))
                                .background(d.tile),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Получить",
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

            // Theme
            MDAOListSection(header = "Тема") {
                themeNames.forEachIndexed { index, name ->
                    val selected = selectedTheme == index
                    val themeBgColors = listOf(
                        Brush.linearGradient(colors = listOf(Color(0xFF0A0A0F), Color(0xFF1A1A22), Color(0xFF23232C))),
                        Brush.linearGradient(colors = listOf(Color(0xFFE8EBF1), Color(0xFFFBFCFE), Color(0xFFE4E8EE))),
                        Brush.linearGradient(colors = listOf(Color(0xFF000000), Color(0xFF0B0B0D), Color(0xFF151518)))
                    )
                    val themeSubs = listOf(
                        "Для низкого освещения, мягкие тени",
                        "Классическая, для дневного света",
                        "Чёрный фон, экономия батареи"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .clip(RoundedCornerShape(MDARadius.xl))
                            .background(d.card)
                            .clickable {
                                selectedTheme = index
                                HapticManager.light()
                            }
                            .drawBehind {
                                val borderColor = if (selected) ext.accent else d.softBorder
                                drawRoundRect(color = borderColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xl.toPx()), style = Stroke(width = 2.dp.toPx()))
                                if (selected) {
                                    drawRoundRect(
                                        color = ext.accent.copy(alpha = 0.08f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xl.toPx())
                                    )
                                }
                            }
                            .padding(14.dp, 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(MDARadius.md))
                                    .background(themeBgColors[index]),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(ext.accent)
                                        .padding(start = 6.dp, bottom = 6.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = d.text,
                                    fontFamily = MarsFont
                                )
                                Text(
                                    text = themeSubs[index],
                                    fontSize = 12.sp,
                                    color = d.text2,
                                    fontFamily = MarsFont
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) ext.accent else Color.Transparent)
                                    .drawBehind {
                                        if (!selected) {
                                            drawCircle(color = d.border, radius = size.minDimension / 2f - 2.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Canvas(modifier = Modifier.size(14.dp)) {
                                        val p = Path().apply {
                                            moveTo(size.width * 0.15f, size.height * 0.5f)
                                            lineTo(size.width * 0.4f, size.height * 0.78f)
                                            lineTo(size.width * 0.88f, size.height * 0.25f)
                                        }
                                        drawPath(p, color = Color.White, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Accent color
            MDAOListSection(header = "Акцентный цвет") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    accentColors.forEachIndexed { index, (name, color) ->
                        val sel = selectedAccent == index
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .shadow(if (sel) 6.dp else 4.dp, CircleShape, clip = false)
                                .clickable {
                                    selectedAccent = index
                                    HapticManager.light()
                                }
                                .drawBehind {
                                    if (sel) {
                                        drawCircle(color = Color.White.copy(alpha = 0.5f), radius = size.minDimension / 2f + 4.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
                                    }
                                }
                        )
                    }
                }
                Text(
                    text = "${accentColors[selectedAccent].first} · по умолчанию",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = d.text2,
                    fontFamily = MarsFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font size
            MDAOListSection(header = "Размер шрифта") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fontSizes.forEachIndexed { index, size ->
                        val sel = selectedFontSize == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(MDARadius.lg))
                                .background(if (sel) ext.accentSoft else d.tile)
                                .clickable {
                                    selectedFontSize = index
                                    HapticManager.light()
                                }
                                .drawBehind {
                                    val borderColor = if (sel) ext.accent else d.softBorder
                                    drawRoundRect(color = borderColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.lg.toPx()), style = Stroke(width = 2.dp.toPx()))
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "A",
                                    fontSize = when (size) { "S" -> 18.sp; "M" -> 22.sp; else -> 26.sp },
                                    fontWeight = FontWeight.Bold,
                                    color = if (sel) ext.accent else d.text,
                                    fontFamily = MarsFont
                                )
                                Text(
                                    text = fontSizeNames[index],
                                    fontSize = 10.sp,
                                    color = if (sel) ext.accent else d.text2,
                                    fontFamily = MarsFont,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Other
            MDAOListSection(header = "Дополнительно") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.5f, size.height * 0.15f)
                                lineTo(size.width * 0.5f, size.height * 0.85f)
                                moveTo(size.width * 0.15f, size.height * 0.5f)
                                lineTo(size.width * 0.85f, size.height * 0.5f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round))
                            drawCircle(color = Color.White, radius = size.minDimension * 0.1f)
                        }
                    },
                    title = "Анимации",
                    subtitle = "Плавные переходы и эффекты",
                    toggle = {
                        MDAOToggle(checked = animationsEnabled, onCheckedChange = { animationsEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            drawCircle(color = Color.White, radius = size.minDimension * 0.2f)
                            drawCircle(color = Color.White, radius = size.minDimension * 0.4f, style = Stroke(width = 1.5f))
                        }
                    },
                    title = "Системная тема",
                    subtitle = "Следовать настройкам устройства",
                    toggle = {
                        MDAOToggle(checked = systemThemeEnabled, onCheckedChange = { systemThemeEnabled = it; HapticManager.light() })
                    },
                    showChevron = false
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
