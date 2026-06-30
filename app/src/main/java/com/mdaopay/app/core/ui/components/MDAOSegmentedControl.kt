package com.mdaopay.app.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.extended

@Composable
fun MDAOSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val d = MaterialTheme.extended.themeColors
    val accent = MaterialTheme.extended.accent
    val density = LocalDensity.current

    var containerWidth by remember { mutableFloatStateOf(0f) }

    val indicatorOffset by animateDpAsState(
        targetValue = with(density) { ((containerWidth / options.size) * selectedIndex).toDp() },
        label = "segmentIndicator"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(MDARadius.lg))
            .background(d.tile)
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(MDARadius.md))
                .background(d.tile)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f / options.size)
                    .height(34.dp)
                    .offset(x = indicatorOffset)
                    .clip(RoundedCornerShape(MDARadius.md))
                    .background(accent.copy(alpha = 0.2f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectionChange(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (index == selectedIndex) accent else d.text2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
