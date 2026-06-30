package com.mdaopay.app.core.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

object PageTransition {
    val push: EnterTransition = slideInHorizontally(
        animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut),
        initialOffsetX = { it / 4 }
    ) + fadeIn(animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut))

    val pop: ExitTransition = slideOutHorizontally(
        animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut),
        targetOffsetX = { -it / 4 }
    ) + fadeOut(animationSpec = tween(MDAODuration.page, easing = MDAOEasing.easeOut))

    val tabFade: ContentTransform = fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))

    val sheetSlideIn: EnterTransition = slideInVertically(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    ) + fadeIn(animationSpec = tween(MDAODuration.base))

    val sheetSlideOut: ExitTransition = slideOutVertically(
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    ) + fadeOut(animationSpec = tween(MDAODuration.base))
}
