package com.mdaopay.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.extended

@Composable
fun MDAOSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0
) {
    val d = MaterialTheme.extended.themeColors
    val accent = MaterialTheme.extended.accent
    val density = LocalDensity.current

    var sliderWidth by remember { mutableFloatStateOf(0f) }

    val fraction = if (steps > 0) {
        val stepSize = 1f / steps
        (kotlin.math.round(value / stepSize) * stepSize).coerceIn(0f, 1f)
    } else {
        value.coerceIn(0f, 1f)
    }

    val thumbOffsetDp: Dp = with(density) {
        (sliderWidth * fraction).toDp()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ ->
                    change.consume()
                    val newFraction = (change.position.x / sliderWidth).coerceIn(0f, 1f)
                    onValueChange(newFraction)
                }
            }
            .onSizeChanged { sliderWidth = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(MDARadius.pill))
                .background(d.tile)
        ) {
            Box(
                modifier = Modifier
                    .width(thumbOffsetDp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(MDARadius.pill))
                    .background(accent)
            )
        }
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp - 13.dp)
                .size(26.dp)
                .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(if (enabled) accent else d.text3)
        )
    }
}
