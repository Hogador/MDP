package com.mdaopay.app.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.theme.*
import com.mdaopay.app.core.ui.theme.extended

@Composable
fun MDAOTokenCard(
    tokenName: String,
    network: String,
    balance: String,
    usdBalance: String,
    trend: String = "",
    trendUp: Boolean = true,
    isExpanded: Boolean = false,
    position: Int = 0,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onSend: () -> Unit = {},
    onReceive: () -> Unit = {},
    onHistory: () -> Unit = {}
) {
    val d = MaterialTheme.extended.themeColors
    val accent = MaterialTheme.extended.accent

    val posTranslateY = when (position) {
        0 -> 0f
        1 -> 14f
        2 -> 26f
        3 -> 36f
        else -> 0f
    }
    val posScale = when (position) {
        0 -> 1f
        1 -> 0.94f
        2 -> 0.88f
        3 -> 0.84f
        else -> 1f
    }
    val posAlpha = when (position) {
        0 -> 1f
        1 -> 0.55f
        2 -> 0.30f
        3 -> 0.15f
        else -> 1f
    }

    val expandTranslateY = if (isExpanded) -46f * position else 0f
    val expandOffset = if (isExpanded) -46f * position else posTranslateY

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isExpanded) 260.dp else 220.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isExpanded) 200.dp else 220.dp)
                .graphicsLayer {
                    translationY = expandOffset
                    scaleX = posScale
                    scaleY = posScale
                    alpha = posAlpha
                }
                .shadow(
                    elevation = 40.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = Color.Black.copy(alpha = 0.6f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1A1A28),
                            Color(0xFF121220),
                            Color(0xFF0A0A18)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(if (isExpanded) 14.dp else 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(if (isExpanded) 30.dp else 44.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tokenName.take(1),
                                fontFamily = MarsFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = (if (isExpanded) 14 else 20).sp,
                                color = accent
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = tokenName,
                                fontFamily = MarsFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isExpanded) 13.sp else 16.sp,
                                color = d.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = network,
                                fontFamily = MarsFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = if (isExpanded) 10.sp else 12.sp,
                                color = d.text2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = balance,
                            fontFamily = MarsMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = 36.sp,
                            letterSpacing = (-1.2).sp,
                            color = d.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = usdBalance,
                            fontFamily = MarsFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = d.text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (trend.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = trend,
                                fontFamily = MarsFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = if (trendUp) MaterialTheme.extended.success else MaterialTheme.extended.danger,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionTile(
                            label = "Send",
                            backgroundColor = MaterialTheme.extended.accent,
                            modifier = Modifier.weight(1f),
                            onClick = onSend
                        )
                        ActionTile(
                            label = "Receive",
                            backgroundColor = MaterialTheme.extended.accentSoft,
                            modifier = Modifier.weight(1f),
                            onClick = onReceive
                        )
                        ActionTile(
                            label = "History",
                            backgroundColor = d.tile,
                            modifier = Modifier.weight(1f),
                            onClick = onHistory
                        )
                    }
                }
            }
        }

        if (!isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                d.overlayDim.copy(alpha = 0.3f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .scale(if (isPressed) 0.96f else 1f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = MarsFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = if (backgroundColor == MaterialTheme.extended.accent) Color.White else MaterialTheme.extended.themeColors.text,
            maxLines = 1
        )
    }
}
