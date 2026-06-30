package com.mdaopay.app.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.mdaopay.app.core.ui.motion.MDAOTween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mdaopay.app.core.ui.theme.DarkNebulaBlue
import com.mdaopay.app.core.ui.theme.DarkNebulaPurple
import com.mdaopay.app.core.ui.theme.DarkNebulaPink
import kotlin.math.sin

@Composable
fun NebulaOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "nebula")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = MDAOTween.nebula,
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        drawNebulaBlob(cx, cy, size.width * 0.5f, DarkNebulaPurple.copy(alpha = 0.15f), phase, 0f)
        drawNebulaBlob(cx, cy, size.width * 0.4f, DarkNebulaBlue.copy(alpha = 0.10f), phase, 120f)
        drawNebulaBlob(cx, cy, size.width * 0.45f, DarkNebulaPink.copy(alpha = 0.08f), phase, 240f)
    }
}

private fun DrawScope.drawNebulaBlob(
    cx: Float, cy: Float, radius: Float, color: Color,
    phase: Float, offset: Float
) {
    val angle = Math.toRadians((phase + offset).toDouble())
    val dx = (sin(angle) * radius * 0.15f).toFloat()
    val dy = (Math.toRadians((phase + offset + 90f).toDouble()).let { sin(it) * radius * 0.15f }).toFloat()

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = Offset(cx + dx, cy + dy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx + dx, cy + dy)
    )
}
