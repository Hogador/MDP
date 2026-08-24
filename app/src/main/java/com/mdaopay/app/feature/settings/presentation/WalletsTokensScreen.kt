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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOListSection
import com.mdaopay.app.core.ui.components.MDAOToggle
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.*

private data class WalletAccount(
    val name: String,
    val address: String,
    val active: Boolean = false
)

private data class TokenItem(
    val symbol: String,
    val name: String,
    val network: String,
    val icon: String,
    val color: Color,
    val visible: Boolean = true
)

@Composable
fun WalletsTokensScreen(onBack: () -> Unit) {
    val ext = MaterialTheme.extended
    val d = ext.themeColors
    var activeWallet by remember { mutableIntStateOf(0) }
    var tokenStates by remember {
        mutableStateOf(
            listOf(true, true, true, true, false, false)
        )
    }

    val wallets = listOf(
        WalletAccount("Аккаунт 1", "0x8A9B\u2026C1D2", true),
        WalletAccount("Аккаунт 2", "0x4F2E\u20268B9C")
    )

    val tokens = listOf(
        TokenItem("USDT", "Tether USD", "BNB Chain", "\u20AE", Color(0xFF26A17B)),
        TokenItem("MDAO", "MDAOPay Token", "BNB Chain", "\u25C8", Color(0xFF6D4AFF)),
        TokenItem("BNB", "BNB", "BNB Chain", "\u25C6", Color(0xFFF0B90B)),
        TokenItem("USDC", "USD Coin", "BNB Chain", "\u25CF", Color(0xFF2775CA)),
        TokenItem("ETH", "Ethereum", "L1", "\u039E", Color(0xFF627EEA)),
        TokenItem("WBTC", "Wrapped Bitcoin", "", "\u20BF", Color(0xFFF09242))
    )

    Column(modifier = Modifier.fillMaxSize().background(d.bg)) {
        MDAOTopBar(title = "Кошельки и токены", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Accounts
            MDAOListSection(header = "Аккаунты") {
                wallets.forEachIndexed { index, wallet ->
                    val sel = activeWallet == index
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(MDARadius.xl))
                            .background(d.card)
                            .clickable {
                                activeWallet = index
                                HapticManager.light()
                            }
                            .drawBehind {
                                val borderColor = if (sel) ext.accent else d.softBorder
                                drawRoundRect(color = borderColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xl.toPx()), style = Stroke(width = 2.dp.toPx()))
                            }
                            .padding(14.dp, 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .shadow(4.dp, CircleShape, clip = false)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(colors = listOf(ext.accent, Color(0xFFFF9A4D)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = wallet.name.take(1).uppercase(),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = MarsFont
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = wallet.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = d.text,
                                    fontFamily = MarsFont
                                )
                                Text(
                                    text = wallet.address,
                                    fontSize = 10.sp,
                                    color = d.text2,
                                    fontFamily = MarsMono
                                )
                            }
                            if (sel) {
                                Text(
                                    text = "Активен",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ext.success,
                                    fontFamily = MarsFont,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(ext.successSoft)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(MDARadius.lg))
                        .background(ext.accentSoft)
                        .clickable { HapticManager.light() }
                        .drawBehind {
                            drawRoundRect(color = ext.accent, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.lg.toPx()), style = Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)))
                        }
                        .padding(12.dp, 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            drawLine(color = ext.accent, start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.2f), end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.8f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                            drawLine(color = ext.accent, start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height / 2f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                        }
                        Text(
                            text = "Добавить аккаунт",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.accent,
                            fontFamily = MarsFont
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tokens
            MDAOListSection(header = "Управление токенами") {
                tokens.forEachIndexed { index, token ->
                    val isVisible = tokenStates[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { HapticManager.light() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .shadow(3.dp, CircleShape, clip = false)
                                .clip(CircleShape)
                                .background(token.color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = token.icon,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = token.symbol,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = d.text,
                                fontFamily = MarsFont
                            )
                            Text(
                                text = "${token.name} · ${token.network}".trimEnd(' ', '·'),
                                fontSize = 10.sp,
                                color = d.text2,
                                fontFamily = MarsFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        MDAOToggle(
                            checked = isVisible,
                            onCheckedChange = {
                                tokenStates = tokenStates.toMutableList().also { it[index] = it[index].not() }
                                HapticManager.light()
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { HapticManager.light() }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            drawLine(color = ext.accent, start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.2f), end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.8f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                            drawLine(color = ext.accent, start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height / 2f), strokeWidth = 2.2f, cap = StrokeCap.Round)
                        }
                        Text(
                            text = "Добавить кастомный токен",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ext.accent,
                            fontFamily = MarsFont
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Токены с выключенным тогглом скрыты из главного экрана, но остаются в кошельке. Баланс можно посмотреть здесь.",
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
