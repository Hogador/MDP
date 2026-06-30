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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.theme.*
import com.mdaopay.app.core.ui.theme.extended

@Composable
fun MDAOSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = 12.dp
) {
    val d = MaterialTheme.extended.themeColors

    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate = shimmerTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    Box(
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier)
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        d.tile,
                        d.surface,
                        d.tile
                    ),
                    startX = shimmerTranslate.value,
                    endX = shimmerTranslate.value + 1200f
                )
            )
    )
}

@Composable
fun MDAOSkeletonCard() {
    val d = MaterialTheme.extended.themeColors

    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate = shimmerTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        d.tile,
                        d.surface,
                        d.tile
                    ),
                    startX = shimmerTranslate.value,
                    endX = shimmerTranslate.value + 1200f
                )
            )
    )
}

@Composable
fun MDAOSkeletonLine(
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    widthFraction: Float = 1f
) {
    val d = MaterialTheme.extended.themeColors

    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate = shimmerTransition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        d.tile,
                        d.surface,
                        d.tile
                    ),
                    startX = shimmerTranslate.value,
                    endX = shimmerTranslate.value + 1200f
                )
            )
    )
}
