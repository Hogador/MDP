package com.mdaopay.app.core.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_STAGGER_ITEMS = 8

fun Modifier.staggerItem(
    index: Int,
    baseDelay: Int = 40,
    baseDuration: Int = MDAODuration.base
): Modifier = composed {
    val animOffset = remember { Animatable(1f) }
    val animAlpha = remember { Animatable(0f) }

    LaunchedEffect(index) {
        val delayMs = (index.coerceAtMost(MAX_STAGGER_ITEMS - 1)) * baseDelay
        delay(delayMs.toLong())
        launch {
            animOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(baseDuration, easing = MDAOEasing.easeOut)
            )
        }
        animAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(baseDuration, easing = MDAOEasing.easeOut)
        )
    }

    graphicsLayer {
        translationY = animOffset.value * 16f
        alpha = animAlpha.value
    }
}

@Composable
fun StaggeredList(
    items: List<*>,
    itemContent: @Composable (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        items.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier.staggerItem(index = index)
            ) {
                itemContent(index)
            }
        }
    }
}
