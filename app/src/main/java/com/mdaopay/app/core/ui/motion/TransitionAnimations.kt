package com.mdaopay.app.core.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object BottomSheetAnim {
    val enterTransition = tween<Float>(
        durationMillis = MDAODuration.sheet,
        easing = MDAOEasing.easeOut
    )
    val exitTransition = tween<Float>(
        durationMillis = MDAODuration.base,
        easing = MDAOEasing.easeIn
    )
    val springEnter = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

object ModalAnim {
    val fadeInSpec = tween<Float>(
        durationMillis = MDAODuration.base,
        easing = MDAOEasing.easeOut
    )
    val fadeOutSpec = tween<Float>(
        durationMillis = MDAODuration.fast,
        easing = MDAOEasing.easeIn
    )
    val scaleSpec = tween<Float>(
        durationMillis = MDAODuration.base,
        easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    )
}

object SharedElementAnim {
    val sharedEnterSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val sharedExitSpec = tween<Float>(
        durationMillis = MDAODuration.base,
        easing = MDAOEasing.easeIn
    )
}
