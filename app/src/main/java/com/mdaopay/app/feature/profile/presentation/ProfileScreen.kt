package com.mdaopay.app.feature.profile.presentation

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.common.copyToClipboard
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.ListIconVariant
import com.mdaopay.app.core.ui.components.MDAOListItem
import com.mdaopay.app.core.ui.components.MDAOListSection
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MDAOThemeColors
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.*

@Composable
fun ProfileScreen(
    username: String,
    walletAddress: String,
    onBack: () -> Unit,
    onSecurityClick: () -> Unit,
    onRecoveryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val d = MaterialTheme.extended.themeColors
    val context = LocalContext.current
    var addressCopied by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(d.bg)) {
        MDAOTopBar(
            title = "Профиль",
            onBack = onBack,
            action = {
                Canvas(modifier = Modifier.size(18.dp)) {
                    val p = Path().apply {
                        moveTo(size.width * 0.15f, size.height * 0.5f)
                        lineTo(size.width * 0.25f, size.height * 0.85f)
                        lineTo(size.width * 0.75f, size.height * 0.85f)
                        lineTo(size.width * 0.85f, size.height * 0.5f)
                        lineTo(size.width * 0.75f, size.height * 0.15f)
                        lineTo(size.width * 0.25f, size.height * 0.15f)
                        close()
                    }
                    drawPath(p, color = d.text, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        )

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
                    .clip(RoundedCornerShape(MDARadius.xxxl))
                    .background(d.card)
                    .drawBehind {
                        drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xxxl.toPx()), style = Stroke(width = 1.dp.toPx()))
                    }
                    .padding(24.dp, 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .shadow(12.dp, CircleShape, clip = false)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(colors = listOf(MaterialTheme.extended.accent, Color(0xFFFF9A4D)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = username.take(2).uppercase(),
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MarsFont
                        )
                    }
                    Text(
                        text = username,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = d.text,
                        fontFamily = MarsFont,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "@${username.lowercase().replace(" ", "-")}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.extended.accent,
                        fontFamily = MarsMono
                    )
                    Text(
                        text = "Дискриминатор: ",
                        fontSize = 12.sp,
                        color = d.text2,
                        fontFamily = MarsFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // QR Block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.xxl))
                    .background(d.card)
                    .drawBehind {
                        drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xxl.toPx()), style = Stroke(width = 1.dp.toPx()))
                    }
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ВАШ QR-КОД",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = d.text2,
                        fontFamily = MarsFont,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(MDARadius.lg))
                            .background(Color.White)
                            .drawBehind {
                                drawRoundRect(
                                    color = d.softBorder,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.lg.toPx()),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(140.dp)) {
                            val s = 8
                            val step = size.width / s
                            for (row in 0 until s) {
                                for (col in 0 until s) {
                                    if ((row + col) % 2 == 0) continue
                                    drawRect(
                                        color = Color(0xFF0A0A0F),
                                        topLeft = androidx.compose.ui.geometry.Offset(col * step, row * step),
                                        size = androidx.compose.ui.geometry.Size(step, step)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Покажите этот код отправителю,\nчтобы получить перевод",
                        fontSize = 11.sp,
                        color = d.text2,
                        fontFamily = MarsFont,
                        lineHeight = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Address Block
            val addrShort = if (walletAddress.length > 20) "${walletAddress.take(12)}...${walletAddress.takeLast(8)}" else walletAddress
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.lg))
                    .background(d.tile)
                    .clickable {
                        context.copyToClipboard(walletAddress, "Wallet address")
                        addressCopied = true
                        HapticManager.light()
                    }
                    .padding(14.dp, 14.dp)
            ) {
                Column {
                    Text(
                        text = "АДРЕС КОШЕЛЬКА (BNB CHAIN)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = d.text2,
                        fontFamily = MarsFont,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (addressCopied) "Скопировано!" else walletAddress,
                            fontSize = 12.sp,
                            color = d.text,
                            fontFamily = MarsMono,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(MDARadius.sm))
                                .background(d.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val p = Path().apply {
                                    addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                        androidx.compose.ui.geometry.Rect(size.width * 0.2f, size.height * 0.25f, size.width * 0.85f, size.height * 0.9f),
                                        2f, 2f
                                    ))
                                }
                                drawPath(p, color = d.text, style = Stroke(width = 1.5f))
                                val p2 = Path().apply {
                                    moveTo(size.width * 0.7f, size.height * 0.25f)
                                    lineTo(size.width * 0.7f, size.height * 0.1f)
                                    lineTo(size.width * 0.15f, size.height * 0.1f)
                                    lineTo(size.width * 0.15f, size.height * 0.75f)
                                    lineTo(size.width * 0.3f, size.height * 0.75f)
                                }
                                drawPath(p2, color = d.text, style = Stroke(width = 1.5f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Share Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShareButton(modifier = Modifier.weight(1f), label = "Telegram", icon = { TelegramShareIcon(d.text) }, d = d)
                ShareButton(modifier = Modifier.weight(1f), label = "WhatsApp", icon = { WhatsAppShareIcon(d.text) }, d = d)
                ShareButton(modifier = Modifier.weight(1f), label = "Ссылка", icon = { LinkShareIcon(d.text) }, d = d)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Управление section
            MDAOListSection(header = "Управление") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.5f)
                                lineTo(size.width * 0.35f, size.height * 0.15f)
                                lineTo(size.width * 0.65f, size.height * 0.15f)
                                lineTo(size.width * 0.8f, size.height * 0.5f)
                                lineTo(size.width * 0.65f, size.height * 0.85f)
                                lineTo(size.width * 0.35f, size.height * 0.85f)
                                close()
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    },
                    iconVariant = ListIconVariant.Accent,
                    title = "Безопасность",
                    subtitle = "PIN, биометрия, автоблокировка",
                    onClick = onSecurityClick
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.15f, size.height * 0.55f)
                                lineTo(size.width * 0.5f, size.height * 0.85f)
                                lineTo(size.width * 0.85f, size.height * 0.55f)
                                moveTo(size.width * 0.5f, size.height * 0.85f)
                                lineTo(size.width * 0.5f, size.height * 0.1f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    },
                    title = "Резервное копирование",
                    subtitle = "Recovery phrase, экспорт",
                    onClick = onRecoveryClick
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.2f, size.height * 0.3f)
                                lineTo(size.width * 0.5f, size.height * 0.1f)
                                lineTo(size.width * 0.8f, size.height * 0.3f)
                                lineTo(size.width * 0.8f, size.height * 0.7f)
                                lineTo(size.width * 0.5f, size.height * 0.9f)
                                lineTo(size.width * 0.2f, size.height * 0.7f)
                                close()
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.1f), end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.9f), strokeWidth = 2f)
                        }
                    },
                    title = "Настройки",
                    onClick = onSettingsClick
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ShareButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: @Composable () -> Unit,
    d: MDAOThemeColors
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MDARadius.lg))
            .background(d.card)
            .clickable { HapticManager.light() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            icon()
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

@Composable
private fun TelegramShareIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val p = Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.5f)
            lineTo(size.width * 0.45f, size.height * 0.65f)
            lineTo(size.width * 0.55f, size.height * 0.9f)
            lineTo(size.width * 0.7f, size.height * 0.7f)
            lineTo(size.width * 0.9f, size.height * 0.85f)
            lineTo(size.width * 0.1f, size.height * 0.5f)
            close()
        }
        drawPath(p, color = color, style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun WhatsAppShareIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        drawCircle(color = color, radius = size.minDimension * 0.4f, style = Stroke(width = 1.5f))
        val p = Path().apply {
            moveTo(size.width * 0.3f, size.height * 0.7f)
            lineTo(size.width * 0.35f, size.height * 0.55f)
            lineTo(size.width * 0.55f, size.height * 0.65f)
            lineTo(size.width * 0.35f, size.height * 0.55f)
        }
        drawPath(p, color = color, style = Stroke(width = 1.5f))
    }
}

@Composable
private fun LinkShareIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val p = Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.65f)
            lineTo(size.width * 0.65f, size.height * 0.35f)
        }
        drawPath(p, color = color, style = Stroke(width = 2f, cap = StrokeCap.Round))
        drawCircle(color = color, radius = size.minDimension * 0.15f, style = Stroke(width = 1.5f))
    }
}
