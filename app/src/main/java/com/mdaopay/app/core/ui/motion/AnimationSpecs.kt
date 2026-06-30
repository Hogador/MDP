package com.mdaopay.app.core.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object MDAOEasing {
    val spring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val easeOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val easeIn = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)
    val ease = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

object MDAODuration {
    val fast = 180
    val quick = 250
    val base = 350
    val page = 500
    val sheet = 550
    val result = 700
}

val pressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

val smoothSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

val slideSpec = tween<Int>(durationMillis = MDAODuration.page, easing = MDAOEasing.easeOut)

val fadeSpec = tween<Float>(durationMillis = MDAODuration.base, easing = MDAOEasing.easeOut)

object MDAOSpring {

    val press = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val smooth = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

object MDAOTween {

    val normal = tween<Float>(
        durationMillis = 350,
        easing = FastOutSlowInEasing
    )

    val slow = tween<Float>(
        durationMillis = 600,
        easing = FastOutSlowInEasing
    )

    val shimmer = tween<Float>(
        durationMillis = 1400,
        easing = FastOutSlowInEasing
    )

    val nebula = tween<Float>(
        durationMillis = 8000,
        easing = FastOutSlowInEasing
    )
}
