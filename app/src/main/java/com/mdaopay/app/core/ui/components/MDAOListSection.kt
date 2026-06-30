package com.mdaopay.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.mdaopay.app.core.ui.theme.extended
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.theme.*

enum class ListIconVariant {
    Default, Accent, Success, Danger
}

@Composable
fun MDAOListSection(
    modifier: Modifier = Modifier,
    header: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    val d = MaterialTheme.extended.themeColors
    val e = MaterialTheme.extended.elevation
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(d.card)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp), clip = false)
            .drawBehind {
                drawRoundRect(
                    color = d.softBorder,
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
    ) {
        if (header.isNotBlank()) {
            Text(
                text = header.uppercase(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = d.text2,
                maxLines = 1
            )
        }
        content()
    }
}

@Composable
fun MDAOListItem(
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    iconVariant: ListIconVariant = ListIconVariant.Default,
    title: String,
    subtitle: String = "",
    value: String = "",
    showChevron: Boolean = true,
    badge: String = "",
    toggle: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val d = MaterialTheme.extended.themeColors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor = if (isPressed && onClick != null) d.tile else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null) { onClick() }
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            val iconBg = when (iconVariant) {
                ListIconVariant.Accent -> MaterialTheme.extended.accentSoft
                ListIconVariant.Success -> MaterialTheme.extended.successSoft
                ListIconVariant.Danger -> MaterialTheme.extended.dangerSoft
                ListIconVariant.Default -> d.tile
            }
            val iconTint = when (iconVariant) {
                ListIconVariant.Accent -> MaterialTheme.extended.accent
                ListIconVariant.Success -> MaterialTheme.extended.success
                ListIconVariant.Danger -> MaterialTheme.extended.danger
                ListIconVariant.Default -> d.text
            }
            val iconElevation = MaterialTheme.extended.elevation
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp), clip = false),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = MarsFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = d.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = d.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (badge.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.extended.accent)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (toggle != null) {
            toggle()
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (value.isNotBlank()) {
            Text(
                text = value,
                fontFamily = MarsFont,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = d.text2,
                textAlign = TextAlign.End,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (showChevron) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.3f, size.height * 0.2f)
                    lineTo(size.width * 0.7f, size.height * 0.5f)
                    lineTo(size.width * 0.3f, size.height * 0.8f)
                }
                drawPath(
                    path = path,
                    color = d.text3,
                    style = Stroke(
                        width = 2.4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
    }
    }
}


