package com.mdaopay.app.ui.recovery

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.guardian.GuardianInfo
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.extended
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TrustConstellation(
    guardians: List<GuardianInfo>,
    modifier: Modifier = Modifier
) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(extended.accentSoft, tc.bg),
                    radius = 1.2f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (guardians.isEmpty()) {
            Text(
                text = "No guardians yet",
                fontFamily = MarsFont,
                fontSize = 13.sp,
                color = tc.text2
            )
            return@Box
        }

        val n = guardians.size
        val orbitDp = 80.dp
        val pi = kotlin.math.PI.toFloat()

        // Lines from center to each guardian
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val orbitPx = orbitDp.toPx()

            guardians.forEachIndexed { i, _ ->
                val angle = 2f * pi / n * i - pi / 2f
                drawLine(
                    color = tc.text2.copy(alpha = 0.25f),
                    start = center,
                    end = Offset(
                        center.x + orbitPx * cos(angle),
                        center.y + orbitPx * sin(angle)
                    ),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // Center user node
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(extended.accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "U",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        }

        // Guardian nodes
        guardians.forEachIndexed { i, guardian ->
            val angle = 2f * pi / n * i - pi / 2f
            val isOnline = guardian.isOnline
            val isInactive = guardian.lastActiveDaysAgo > 30

            val infiniteTransition = rememberInfiniteTransition(label = "pulse_$i")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale_$i"
            )

            val nodeAlpha = if (isInactive) 0.3f else 1f
            val effectiveScale = if (isOnline) pulseScale else 1f

            Box(
                modifier = Modifier
                    .offset(
                        x = (cos(angle) * orbitDp.value).dp,
                        y = (sin(angle) * orbitDp.value).dp
                    )
                    .scale(effectiveScale)
                    .alpha(nodeAlpha),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOnline) extended.success
                                else tc.text2.copy(alpha = 0.4f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "s${guardian.shareIndex}",
                            fontFamily = MarsFont,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = guardian.label,
                        fontFamily = MarsFont,
                        fontSize = 9.sp,
                        color = tc.text2.copy(alpha = nodeAlpha),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
        }
    }
}
