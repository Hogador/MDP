package com.mdaopay.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.extended

enum class BadgeVariant { Default, Success, Danger, Warning, Muted }

@Composable
fun MDAOBadgePill(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Default,
    showDot: Boolean = false
) {
    val d = MaterialTheme.extended.themeColors
    val (bgColor, textColor, dotColor) = when (variant) {
        BadgeVariant.Default -> Triple(d.tile, d.text2, d.text2)
        BadgeVariant.Success -> Triple(MaterialTheme.extended.successSoft, MaterialTheme.extended.success, MaterialTheme.extended.success)
        BadgeVariant.Danger -> Triple(MaterialTheme.extended.dangerSoft, MaterialTheme.extended.danger, MaterialTheme.extended.danger)
        BadgeVariant.Warning -> Triple(MaterialTheme.extended.warningSoft, MaterialTheme.extended.warning, MaterialTheme.extended.warning)
        BadgeVariant.Muted -> Triple(d.surface, d.text3, d.text3)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MDARadius.pill))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

enum class ChipVariant { Default, Accent, Success, Danger }

@Composable
fun MDAOChip(
    text: String,
    modifier: Modifier = Modifier,
    variant: ChipVariant = ChipVariant.Default,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false
) {
    val d = MaterialTheme.extended.themeColors
    val accent = MaterialTheme.extended.accent

    val (bgColor, textColor, borderColor) = when (variant) {
        ChipVariant.Default -> {
            if (selected) Triple(accent.copy(alpha = 0.15f), accent, accent.copy(alpha = 0.3f))
            else Triple(d.tile, d.text2, d.softBorder)
        }
        ChipVariant.Accent -> {
            if (selected) Triple(accent.copy(alpha = 0.2f), accent, accent.copy(alpha = 0.4f))
            else Triple(accent.copy(alpha = 0.08f), accent, accent.copy(alpha = 0.15f))
        }
        ChipVariant.Success -> {
            if (selected) Triple(MaterialTheme.extended.success.copy(alpha = 0.2f), MaterialTheme.extended.success, MaterialTheme.extended.success.copy(alpha = 0.4f))
            else Triple(MaterialTheme.extended.successSoft, MaterialTheme.extended.success, MaterialTheme.extended.success.copy(alpha = 0.15f))
        }
        ChipVariant.Danger -> {
            if (selected) Triple(MaterialTheme.extended.danger.copy(alpha = 0.2f), MaterialTheme.extended.danger, MaterialTheme.extended.danger.copy(alpha = 0.4f))
            else Triple(MaterialTheme.extended.dangerSoft, MaterialTheme.extended.danger, MaterialTheme.extended.danger.copy(alpha = 0.15f))
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MDARadius.pill))
            .background(bgColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClick() }
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
