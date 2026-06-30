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
import kotlinx.coroutines.delay

enum class NotifType { SUCCESS, ERROR, INFO }

enum class NotifMode { CARD, PILL }

data class NotifData(
    val type: NotifType,
    val title: String,
    val sub: String = "",
    val amount: String = "",
    val commission: String = "",
    val actionLabel: String = "",
    val onAction: (() -> Unit)? = null,
    val mode: NotifMode = NotifMode.CARD
)

@Composable
fun MDAONotification(
    data: NotifData,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val d = MaterialTheme.extended.themeColors

    val bgColor = when (data.type) {
        NotifType.SUCCESS -> d.card
        NotifType.ERROR -> d.card
        NotifType.INFO -> d.card
    }
    val accentColor = when (data.type) {
        NotifType.SUCCESS -> MaterialTheme.extended.success
        NotifType.ERROR -> MaterialTheme.extended.danger
        NotifType.INFO -> MaterialTheme.extended.accent
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(data) {
        delay(3000)
        onDismiss()
    }

    val waveProgress = remember { Animatable(0f) }

    LaunchedEffect(data) {
        waveProgress.snapTo(0f)
        waveProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(900, easing = FastOutSlowInEasing)
        )
    }

    when (data.mode) {
        NotifMode.CARD -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(22.dp, 24.dp)
                    .shadow(
                        elevation = 40.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = Color.Black.copy(alpha = 0.5f)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(bgColor)
                    .scale(if (isPressed) 0.98f else 1f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { data.onAction?.invoke() }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(28.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .size(300.dp * waveProgress.value)
                            .graphicsLayer {
                                scaleX = waveProgress.value * (0.6f + 8.4f * waveProgress.value)
                                scaleY = waveProgress.value * (0.6f + 8.4f * waveProgress.value)
                                alpha = (1f - waveProgress.value) * 0.75f
                            }
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.08f))
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (data.type) {
                                NotifType.SUCCESS -> "\u2713"
                                NotifType.ERROR -> "\u2717"
                                NotifType.INFO -> "i"
                            },
                            fontFamily = MarsFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = data.title,
                            fontFamily = MarsFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = d.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (data.sub.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = data.sub,
                                fontFamily = MarsFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = d.text2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (data.amount.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = data.amount,
                                fontFamily = MarsMono,
                                fontWeight = FontWeight.Medium,
                                fontSize = 20.sp,
                                letterSpacing = (-0.5).sp,
                                color = d.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (data.commission.isNotBlank()) {
                            Text(
                                text = data.commission,
                                fontFamily = MarsFont,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = d.text3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (data.actionLabel.isNotBlank() && data.onAction != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = data.actionLabel,
                                fontFamily = MarsFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        NotifMode.PILL -> {
            Box(
                modifier = modifier
                    .padding(12.dp, 18.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(999.dp),
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.4f)
                    )
                    .clip(RoundedCornerShape(999.dp))
                    .background(bgColor)
                    .scale(if (isPressed) 0.97f else 1f)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { data.onAction?.invoke() }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (data.type) {
                                NotifType.SUCCESS -> "\u2713"
                                NotifType.ERROR -> "\u2717"
                                NotifType.INFO -> "i"
                            },
                            fontFamily = MarsFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = data.title,
                        fontFamily = MarsFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = d.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (data.sub.isNotBlank()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = data.sub,
                            fontFamily = MarsFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = d.text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MDAONotificationZone(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        content()
    }
}
