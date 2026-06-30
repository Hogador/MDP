package com.mdaopay.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mdaopay.app.core.ui.motion.MDAOSpring
import com.mdaopay.app.core.ui.theme.DarkOnSurfaceMuted
import com.mdaopay.app.core.ui.theme.MDAOPurple

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MDAOPurple,
    inactiveColor: Color = DarkOnSurfaceMuted,
    dotSize: Dp = 8.dp,
    activeDotWidth: Dp = 24.dp,
    spacing: Dp = 6.dp
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage

            val animatedWidth by animateFloatAsState(
                targetValue = if (isActive) activeDotWidth.value else dotSize.value,
                animationSpec = MDAOSpring.smooth,
                label = "indicatorWidth"
            )

            val animatedAlpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.4f,
                animationSpec = MDAOSpring.smooth,
                label = "indicatorAlpha"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = spacing / 2)
                    .width(width = animatedWidth.dp)
                    .height(dotSize)
                    .alpha(animatedAlpha)
                    .clip(RoundedCornerShape(dotSize / 2))
                    .background(if (isActive) activeColor else inactiveColor)
            )
        }
    }
}
