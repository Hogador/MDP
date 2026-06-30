package com.mdaopay.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.motion.MDAOSpring
import com.mdaopay.app.core.ui.theme.extended

enum class MDAOButtonVariant { Primary, Secondary, Ghost, Danger }

enum class MDAOButtonSize { Sm, Md, Lg }

@Composable
fun MDAOButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    variant: MDAOButtonVariant = MDAOButtonVariant.Primary,
    size: MDAOButtonSize = MDAOButtonSize.Md
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = MDAOSpring.press,
        label = "btnScale"
    )

    val d = MaterialTheme.extended.themeColors
    val ext = MaterialTheme.extended

    val (bgColor, textColor) = when (variant) {
        MDAOButtonVariant.Primary ->
            if (isPressed) ext.accentPress to Color.White else ext.accent to Color.White
        MDAOButtonVariant.Secondary -> d.tile to d.text
        MDAOButtonVariant.Ghost ->
            Color.Transparent to if (isPressed) d.text else d.text2
        MDAOButtonVariant.Danger -> ext.danger to Color.White
    }

    val buttonHeight = when (size) {
        MDAOButtonSize.Sm -> 38.dp
        MDAOButtonSize.Md -> 44.dp
        MDAOButtonSize.Lg -> 54.dp
    }

    val paddingH = when (size) {
        MDAOButtonSize.Sm -> 16.dp
        MDAOButtonSize.Md -> 22.dp
        MDAOButtonSize.Lg -> 28.dp
    }

    val paddingV = when (size) {
        MDAOButtonSize.Sm -> 10.dp
        MDAOButtonSize.Md -> 14.dp
        MDAOButtonSize.Lg -> 18.dp
    }

    val shape = RoundedCornerShape(16.dp)

    val shadowElevation = when (variant) {
        MDAOButtonVariant.Primary -> 8.dp
        MDAOButtonVariant.Secondary -> 4.dp
        MDAOButtonVariant.Ghost -> 0.dp
        MDAOButtonVariant.Danger -> 8.dp
    }

    val isDisabled = !enabled || isLoading

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(buttonHeight)
            .scale(scale)
            .then(
                if (shadowElevation > 0.dp) Modifier.shadow(shadowElevation, shape)
                else Modifier
            )
            .clip(shape)
            .background(if (isDisabled) bgColor.copy(alpha = 0.4f) else bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isDisabled,
                onClick = {
                    HapticManager.light()
                    SoundManager.playClick()
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.padding(horizontal = paddingH, vertical = paddingV)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = textColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(20.dp)
                )
            } else {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    color = if (isDisabled) textColor.copy(alpha = 0.4f) else textColor
                )
            }
        }
    }
}
