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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.*

private data class Network(
    val name: String,
    val chainId: String,
    val rpc: String,
    val icon: String,
    val color: Color,
    val builtIn: Boolean = true
)

private val builtInNetworks = listOf(
    Network("BNB Smart Chain", "56", "rpc-bsc.mdao.xyz", "\u25C6", Color(0xFFF0B90B)),
    Network("Ethereum", "1", "rpc-eth.mdao.xyz", "\u039E", Color(0xFF627EEA)),
    Network("Polygon", "137", "rpc-poly.mdao.xyz", "\u25C6", Color(0xFF8247E5)),
    Network("Arbitrum One", "42161", "rpc-arb.mdao.xyz", "\u25C6", Color(0xFF28A0F0)),
    Network("Optimism", "10", "rpc-op.mdao.xyz", "\u25C6", Color(0xFFFF0420))
)

private val customNetworks = listOf(
    Network("My Custom RPC", "1234", "my-node.example.com", "\u26A1", Color(0xFF7B4DFF), builtIn = false)
)

@Composable
fun NetworksScreen(onBack: () -> Unit) {
    val ext = MaterialTheme.extended
    val d = ext.themeColors
    var activeIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(d.bg)) {
        MDAOTopBar(title = "Сети", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Add network
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.lg))
                    .background(ext.accentSoft)
                    .clickable { HapticManager.light() }
                    .drawBehind {
                        drawRoundRect(color = ext.accent, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.lg.toPx()), style = Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)))
                    }
                    .padding(14.dp, 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        drawLine(color = ext.accent, start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.2f), end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.8f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                        drawLine(color = ext.accent, start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height / 2f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                    }
                    Text(
                        text = "Добавить сеть (RPC)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.accent,
                        fontFamily = MarsFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Built-in networks
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.xxl))
                    .background(d.card)
                    .drawBehind {
                        drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xxl.toPx()), style = Stroke(width = 1.dp.toPx()))
                    }
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                        Text(
                            text = "ВСТРОЕННЫЕ СЕТИ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = d.text2,
                            fontFamily = MarsFont,
                            letterSpacing = 1.sp
                        )
                    }
                    builtInNetworks.forEachIndexed { index, net ->
                        val sel = activeIndex == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeIndex = index
                                    HapticManager.light()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .shadow(4.dp, CircleShape, clip = false)
                                    .clip(CircleShape)
                                    .background(net.color),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = net.icon,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = MarsFont
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = net.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = d.text,
                                        fontFamily = MarsFont
                                    )
                                    if (sel) {
                                        Text(
                                            text = "активна",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ext.success,
                                            fontFamily = MarsFont,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(ext.successSoft)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Chain ID: ${net.chainId} · ${net.rpc}",
                                    fontSize = 10.sp,
                                    color = d.text2,
                                    fontFamily = MarsMono
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (sel) ext.accent else Color.Transparent)
                                    .drawBehind {
                                        if (!sel) {
                                            drawCircle(color = d.border, radius = size.minDimension / 2f - 2.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (sel) {
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.xxl))
                    .background(d.card)
                    .drawBehind {
                        drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xxl.toPx()), style = Stroke(width = 1.dp.toPx()))
                    }
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                        Text(
                            text = "ПОЛЬЗОВАТЕЛЬСКИЕ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = d.text2,
                            fontFamily = MarsFont,
                            letterSpacing = 1.sp
                        )
                    }
                    customNetworks.forEach { net ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { HapticManager.light() }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .shadow(4.dp, CircleShape, clip = false)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFF7B4DFF), Color(0xFF2D7FF9)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = net.icon, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = MarsFont)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = net.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = d.text, fontFamily = MarsFont)
                                Text(text = "Chain ID: ${net.chainId} · ${net.rpc}", fontSize = 10.sp, color = d.text2, fontFamily = MarsMono)
                            }
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .drawBehind {
                                        drawCircle(color = d.border, radius = size.minDimension / 2f - 2.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "MDAOPay использует собственные RPC-узлы для скорости и приватности. Активная сеть определяет, где выполняются транзакции и отображаются балансы.",
                fontSize = 11.sp,
                color = d.text2,
                fontFamily = MarsFont,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
