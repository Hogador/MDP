package com.mdaopay.app.core.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val dropSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

private val CARD_RADIUS = 28.dp

enum class NotifStyle { Card, Pill }

private val NotifStyle.targetRadius: Dp
    get() = when (this) {
        NotifStyle.Card -> CARD_RADIUS
        NotifStyle.Pill -> 999.dp
    }

fun Modifier.notifDropAndMorph(
    visible: Boolean,
    style: NotifStyle = NotifStyle.Card
): Modifier = composed {
    val animRadius = remember { Animatable(50f) }
    val animOffsetY = remember { Animatable(-110f) }
    val animScaleX = remember { Animatable(0.05f) }
    val animScaleY = remember { Animatable(0.05f) }
    val animAlpha = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (visible) {
            animOffsetY.snapTo(-110f)
            animScaleX.snapTo(0.05f)
            animScaleY.snapTo(0.05f)
            animRadius.snapTo(50f)
            animAlpha.snapTo(1f)

            animOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = MDAODuration.base
                    0f at 0
                    0f at (MDAODuration.base * 35 / 100)
                }
            )
            animScaleX.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = MDAODuration.result
                    0.05f at 0
                    0.05f at (MDAODuration.result * 35 / 100)
                    0.4f at (MDAODuration.result * 55 / 100)
                    1f at MDAODuration.result
                }
            )
            animScaleY.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = MDAODuration.result
                    0.05f at 0
                    0.05f at (MDAODuration.result * 35 / 100)
                    0.06f at (MDAODuration.result * 55 / 100)
                    1f at MDAODuration.result
                }
            )
            animRadius.animateTo(
                targetValue = style.targetRadius.value,
                animationSpec = keyframes {
                    durationMillis = MDAODuration.result
                    50f at 0
                    50f at (MDAODuration.result * 55 / 100)
                    style.targetRadius.value at MDAODuration.result
                }
            )
        } else {
            launch {
                animScaleX.animateTo(0.05f, animationSpec = dropSpring)
            }
            launch {
                animScaleY.animateTo(0.05f, animationSpec = dropSpring)
            }
            animAlpha.animateTo(0f, animationSpec = dropSpring)
        }
    }

    graphicsLayer {
        scaleX = animScaleX.value
        scaleY = animScaleY.value
        alpha = animAlpha.value
        translationY = animOffsetY.value
    }
        .clip(RoundedCornerShape(animRadius.value.dp))
}
