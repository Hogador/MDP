package com.mdaopay.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.motion.MDAOSpring
import com.mdaopay.app.core.ui.theme.*
import com.mdaopay.app.core.ui.theme.extended

data class NavItem(
    val label: String,
    val isActive: Boolean = false,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit
)

@Composable
fun MDAONavigationBar(
    items: List<NavItem>,
    modifier: Modifier = Modifier
) {
    val d = MaterialTheme.extended.themeColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MDARadius.xl))
            .background(d.card)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(MDARadius.xl), clip = false)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.92f else 1f,
                animationSpec = MDAOSpring.press,
                label = "navScale"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .scale(scale)
                    .clip(RoundedCornerShape(MDARadius.md))
                    .then(
                        if (item.isActive) Modifier.background(d.tile) else Modifier
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {
                            HapticManager.light()
                            SoundManager.playClick()
                            item.onClick()
                        }
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        item.icon()
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (item.isActive) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (item.isActive) d.text else d.text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
