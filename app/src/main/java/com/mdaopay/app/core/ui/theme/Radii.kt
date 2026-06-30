package com.mdaopay.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object MDARadius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 14.dp
    val xl = 16.dp
    val xxl = 20.dp
    val xxxl = 24.dp
    val card = 28.dp
    val pill = 999.dp
}

object MDAShape {
    val xs = RoundedCornerShape(MDARadius.xs)
    val sm = RoundedCornerShape(MDARadius.sm)
    val md = RoundedCornerShape(MDARadius.md)
    val lg = RoundedCornerShape(MDARadius.lg)
    val xl = RoundedCornerShape(MDARadius.xl)
    val xxl = RoundedCornerShape(MDARadius.xxl)
    val xxxl = RoundedCornerShape(MDARadius.xxxl)
    val card = RoundedCornerShape(MDARadius.card)
    val pill = RoundedCornerShape(MDARadius.pill)
}
